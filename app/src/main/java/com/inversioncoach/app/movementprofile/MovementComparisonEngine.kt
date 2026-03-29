package com.inversioncoach.app.movementprofile

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MovementComparisonEngine(
    private val explainer: MovementDeviationExplainer = MovementDeviationExplainer(),
) {
    fun compareStoredProfiles(
        subject: StoredProfileSnapshot,
        reference: StoredProfileSnapshot,
    ): MovementComparisonResult {
        val template = ReferenceTemplateDefinition(
            id = "stored-template",
            templateName = "Stored template",
            drillId = "",
            description = "",
            phaseTimingMs = reference.phaseDurationsMs,
            alignmentTargets = reference.featureMeans,
            stabilityTargets = reference.stabilityJitter,
            assetPath = "",
        )
        val analysis = UploadedVideoAnalysisResult(
            inferredView = CameraViewConstraint.ANY,
            phaseTimeline = expandPhaseTimeline(subject.phaseDurationsMs),
            overlayTimeline = syntheticOverlay(subject),
            droppedFrames = 0,
            telemetry = emptyMap(),
            candidate = MovementTemplateCandidate(
                id = "stored",
                sourceSessionId = "stored",
                tentativeName = null,
                movementTypeGuess = MovementType.HOLD,
                detectedView = CameraViewConstraint.ANY,
                keyJoints = emptySet(),
                candidatePhases = emptyList(),
                candidateRomMetrics = emptyMap(),
                thresholdSuggestions = emptyMap(),
                confidence = 1f,
                status = CandidateStatus.DRAFT,
            ),
        )
        return compare(template, analysis)
    }

    fun compare(
        template: ReferenceTemplateDefinition,
        analysis: UploadedVideoAnalysisResult,
    ): MovementComparisonResult {
        val deviations = mutableListOf<ComparisonDeviation>()

        val timing = timingScore(template, analysis, deviations)
        val alignment = alignmentScore(template, analysis, deviations)
        val stability = stabilityScore(template, analysis, deviations)

        val overall = (timing * 0.4f + alignment * 0.35f + stability * 0.25f)
            .roundToInt()
            .coerceIn(0, 100)

        return MovementComparisonResult(
            overallSimilarityScore = overall,
            timingScore = timing,
            alignmentScore = alignment,
            stabilityScore = stability,
            phaseScores = phaseScores(template, analysis),
            topDifferences = explainer.topDifferences(deviations, limit = 3),
        )
    }

    private fun timingScore(
        template: ReferenceTemplateDefinition,
        analysis: UploadedVideoAnalysisResult,
        deviations: MutableList<ComparisonDeviation>,
    ): Int {
        val observedDurations = phaseDurations(analysis.phaseTimeline)
        if (template.phaseTimingMs.isEmpty()) return 0
        val perPhaseScores = template.phaseTimingMs.map { (phase, expectedMs) ->
            val observed = observedDurations[phase] ?: 0L
            val normalizedDelta = abs(observed - expectedMs).toFloat() / expectedMs.coerceAtLeast(1L).toFloat()
            val score = (100f * (1f - normalizedDelta.coerceIn(0f, 1f))).roundToInt().coerceIn(0, 100)
            if (normalizedDelta > 0.25f) {
                val relation = if (observed > expectedMs) "longer" else "shorter"
                deviations += ComparisonDeviation(
                    key = "timing_$phase",
                    severity = normalizedDelta,
                    description = "$phase timing was ${abs(observed - expectedMs)}ms $relation than template",
                )
            }
            score
        }
        return perPhaseScores.average().roundToInt().coerceIn(0, 100)
    }

    private fun alignmentScore(
        template: ReferenceTemplateDefinition,
        analysis: UploadedVideoAnalysisResult,
        deviations: MutableList<ComparisonDeviation>,
    ): Int {
        if (template.alignmentTargets.isEmpty() || analysis.overlayTimeline.isEmpty()) return 0
        val metricsByKey = analysis.overlayTimeline
            .flatMap { point -> point.metrics.entries }
            .groupBy({ it.key }, { it.value })
        val scores = template.alignmentTargets.mapNotNull { (key, target) ->
            val observedMean = metricsByKey[key]?.average()?.toFloat() ?: return@mapNotNull null
            val normalizedDelta = abs(observedMean - target) / target.coerceAtLeast(0.01f)
            val score = (100f * (1f - normalizedDelta.coerceIn(0f, 1f))).roundToInt().coerceIn(0, 100)
            if (normalizedDelta > 0.2f) {
                deviations += ComparisonDeviation(
                    key = "alignment_$key",
                    severity = normalizedDelta,
                    description = "$key mean deviated by ${"%.2f".format(abs(observedMean - target))} from reference",
                )
            }
            score
        }
        return if (scores.isEmpty()) 0 else scores.average().roundToInt().coerceIn(0, 100)
    }

    private fun stabilityScore(
        template: ReferenceTemplateDefinition,
        analysis: UploadedVideoAnalysisResult,
        deviations: MutableList<ComparisonDeviation>,
    ): Int {
        if (template.stabilityTargets.isEmpty() || analysis.overlayTimeline.isEmpty()) return 0
        val scores = template.stabilityTargets.mapNotNull { (key, target) ->
            val values = analysis.overlayTimeline.mapNotNull { it.metrics[key] }
            if (values.isEmpty()) return@mapNotNull null
            val jitter = standardDeviation(values)
            val normalizedDelta = abs(jitter - target) / target.coerceAtLeast(0.01f)
            val score = (100f * (1f - normalizedDelta.coerceIn(0f, 1f))).roundToInt().coerceIn(0, 100)
            if (normalizedDelta > 0.2f) {
                deviations += ComparisonDeviation(
                    key = "stability_$key",
                    severity = normalizedDelta,
                    description = "$key stability jitter differed by ${"%.2f".format(abs(jitter - target))}",
                )
            }
            score
        }
        return if (scores.isEmpty()) 0 else scores.average().roundToInt().coerceIn(0, 100)
    }

    private fun phaseScores(
        template: ReferenceTemplateDefinition,
        analysis: UploadedVideoAnalysisResult,
    ): Map<String, Int> {
        val observedDurations = phaseDurations(analysis.phaseTimeline)
        return template.phaseTimingMs.mapValues { (phase, expectedMs) ->
            val observed = observedDurations[phase] ?: 0L
            val normalizedDelta = abs(observed - expectedMs).toFloat() / expectedMs.coerceAtLeast(1L).toFloat()
            (100f * (1f - normalizedDelta.coerceIn(0f, 1f))).roundToInt().coerceIn(0, 100)
        }
    }

    private fun phaseDurations(phaseTimeline: List<Pair<Long, String>>): Map<String, Long> {
        if (phaseTimeline.isEmpty()) return emptyMap()
        val sorted = phaseTimeline.sortedBy { it.first }
        val durations = linkedMapOf<String, Long>()
        for (index in 0 until sorted.lastIndex) {
            val current = sorted[index]
            val next = sorted[index + 1]
            val duration = (next.first - current.first).coerceAtLeast(0L)
            durations[current.second] = (durations[current.second] ?: 0L) + duration
        }
        return durations
    }

    private fun standardDeviation(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    private fun syntheticOverlay(snapshot: StoredProfileSnapshot): List<OverlayTimelinePoint> {
        val baseMetrics = snapshot.featureMeans
            .filterKeys { it.endsWith("_mean") }
            .mapKeys { it.key.removeSuffix("_mean") }
        return listOf(
            OverlayTimelinePoint(0L, emptyList(), baseMetrics, "setup", 1f),
            OverlayTimelinePoint(500L, emptyList(), baseMetrics, "hold", 1f),
            OverlayTimelinePoint(1000L, emptyList(), baseMetrics, "hold", 1f),
        )
    }

    private fun expandPhaseTimeline(phaseDurations: Map<String, Long>): List<Pair<Long, String>> {
        val events = mutableListOf<Pair<Long, String>>()
        var cursor = 0L
        phaseDurations.forEach { (phase, duration) ->
            events += cursor to phase
            cursor += duration.coerceAtLeast(1L)
        }
        if (events.isEmpty()) events += 0L to "setup"
        return events
    }
}
