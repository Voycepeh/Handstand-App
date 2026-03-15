package com.inversioncoach.app.motion

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class AlignmentScoringEngine(
    private val profile: DrillQualityProfile,
    private val settings: UserCalibrationSettings,
    private val smoothingAlpha: Float = 0.25f,
) {
    private var smoothedScore = 0f

    fun score(frame: AngleFrame, faults: List<FaultEvent>): AlignmentScoreSnapshot {
        val thresholds = settings.resolvedThresholds()
        val componentScores = mapOf(
            "shoulder_stack" to scoreByTolerance(abs(frame.anglesDeg["wrist_to_shoulder_line"] ?: 0f) / 120f, 0.18f, thresholds),
            "hip_stack" to scoreByTolerance(abs(frame.pelvicTiltDeg) / 75f, 0.16f, thresholds),
            "ankle_stack" to scoreByTolerance(frame.lineDeviationNorm, thresholds.acceptableLineDeviation, thresholds),
            "elbow_lock" to scoreByDegrees(avg(frame.anglesDeg["left_elbow_flexion"], frame.anglesDeg["right_elbow_flexion"]), 170f, 40f),
            "knee_extension" to scoreByDegrees(avg(frame.anglesDeg["left_knee_flexion"], frame.anglesDeg["right_knee_flexion"]), 172f, 35f),
            "trunk_straightness" to scoreByTolerance(frame.trunkLeanDeg / 60f, 0.2f, thresholds),
        )

        val weighted = profile.componentWeights.entries.sumOf { (k, w) -> (componentScores[k] ?: 0) * w.toDouble() }
        val raw = weighted.toInt().coerceIn(0, 100)
        smoothedScore = if (smoothedScore == 0f) raw.toFloat() else (smoothingAlpha * raw + (1f - smoothingAlpha) * smoothedScore)

        val lowestComponent = componentScores.minByOrNull { it.value }?.key.orEmpty()
        val dominantFault = faults.firstOrNull()?.code?.let { profile.dominantFaultMapping[it] ?: it } ?: lowestComponent

        return AlignmentScoreSnapshot(
            rawScore = raw,
            smoothedScore = smoothedScore.toInt().coerceIn(0, 100),
            dominantFault = dominantFault,
            componentScores = componentScores,
        )
    }

    private fun scoreByTolerance(value: Float, tolerance: Float, thresholds: QualityThresholds): Int {
        val strictnessMultiplier = thresholds.acceptableLineDeviation / 0.14f
        val normalized = 1f - (value / max(tolerance * strictnessMultiplier, 0.02f))
        return normalized.coerceIn(0f, 1f).toScore()
    }

    private fun scoreByDegrees(value: Float, target: Float, range: Float): Int {
        val normalized = 1f - (abs(target - value) / range)
        return normalized.coerceIn(0f, 1f).toScore()
    }

    private fun avg(a: Float?, b: Float?): Float = listOfNotNull(a, b).average().toFloat()
}

class StabilityAnalysisEngine(
    private val windowSize: Int = 24,
) {
    private data class Sample(val ts: Long, val deviation: Float)
    private val window = ArrayDeque<Sample>()

    fun analyze(frame: SmoothedPoseFrame): StabilitySnapshot {
        val centerlineDeviation = centerlineDeviation(frame)
        window.addLast(Sample(frame.timestampMs, centerlineDeviation))
        while (window.size > windowSize) window.removeFirst()

        val values = window.map { it.deviation }
        val mean = if (values.isEmpty()) 0f else values.average().toFloat()
        val variance = if (values.isEmpty()) 0f else values.sumOf { (it - mean) * (it - mean).toDouble() }.toFloat() / values.size
        val amplitude = sqrt(variance).coerceAtLeast(0f)
        val frequency = estimateFrequency(values, window.map { it.ts })
        val normalizedPenalty = (centerlineDeviation * 3.6f) + (amplitude * 5.5f) + (frequency * 0.5f)
        val stabilityScore = (100f - normalizedPenalty * 100f).toInt().coerceIn(0, 100)

        return StabilitySnapshot(centerlineDeviation, amplitude, frequency, stabilityScore)
    }

    private fun centerlineDeviation(frame: SmoothedPoseFrame): Float {
        fun point(j: JointId): Landmark2D? = frame.filteredLandmarks[j]
        val shoulders = midpoint(point(JointId.LEFT_SHOULDER), point(JointId.RIGHT_SHOULDER)) ?: return 0f
        val hips = midpoint(point(JointId.LEFT_HIP), point(JointId.RIGHT_HIP)) ?: return 0f
        val ankles = midpoint(point(JointId.LEFT_ANKLE), point(JointId.RIGHT_ANKLE)) ?: return 0f
        return ((abs(shoulders.x - hips.x) + abs(hips.x - ankles.x)) / 2f).coerceAtLeast(0f)
    }

    private fun estimateFrequency(values: List<Float>, timestamps: List<Long>): Float {
        if (values.size < 3) return 0f
        var zeroCrossings = 0
        val centered = values.map { it - values.average().toFloat() }
        for (i in 1 until centered.size) {
            if ((centered[i - 1] <= 0f && centered[i] > 0f) || (centered[i - 1] >= 0f && centered[i] < 0f)) zeroCrossings++
        }
        val durationS = ((timestamps.last() - timestamps.first()).coerceAtLeast(1L)) / 1000f
        return (zeroCrossings / 2f) / durationS
    }

    private fun midpoint(a: Landmark2D?, b: Landmark2D?): Landmark2D? {
        if (a == null || b == null) return null
        return Landmark2D((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
}

class HoldQualityTracker(
    private val thresholds: QualityThresholds,
) {
    private var lastTs: Long? = null
    private var totalMs = 0L
    private var alignedMs = 0L
    private var bestStreakMs = 0L
    private var currentStreakMs = 0L
    private var pendingAlignedMs = 0L
    private var scoreIntegral = 0L

    fun update(timestampMs: Long, alignmentScore: Int): HoldQualitySnapshot {
        val delta = ((lastTs?.let { timestampMs - it } ?: 0L)).coerceAtLeast(0L)
        lastTs = timestampMs
        totalMs += delta
        scoreIntegral += alignmentScore * delta

        val isGood = alignmentScore >= thresholds.holdAlignedThreshold
        if (isGood) {
            pendingAlignedMs += delta
            if (pendingAlignedMs >= thresholds.alignmentPersistenceMs) {
                alignedMs += delta
                currentStreakMs += delta
                bestStreakMs = max(bestStreakMs, currentStreakMs)
            }
        } else {
            pendingAlignedMs = 0L
            currentStreakMs = 0L
        }

        return snapshot()
    }

    fun snapshot(): HoldQualitySnapshot {
        val avgScore = if (totalMs <= 0L) 0 else (scoreIntegral / totalMs).toInt().coerceIn(0, 100)
        val rate = if (totalMs <= 0L) 0f else alignedMs.toFloat() / totalMs
        return HoldQualitySnapshot(totalMs, alignedMs, rate, bestStreakMs, avgScore, alignedMs)
    }
}

class RepQualityEvaluator(
    private val profile: DrillQualityProfile,
    private val thresholds: QualityThresholds,
) {
    private var lastRawAttempts = 0
    private var repCount = 0
    private val results = mutableListOf<RepQualityResult>()
    private var cycleStartTs = 0L
    private var minElbowInCycle = 180f
    private var maxElbowAtTop = 0f
    private var belowFloorDurationMs = 0L
    private var lastTs = 0L

    fun update(
        timestampMs: Long,
        movement: MovementState,
        repTracking: RepTrackingSnapshot?,
        alignmentScore: Int,
        dominantFault: String,
        angles: AngleFrame,
    ): RepQualitySnapshot {
        if (movement.currentPhase == MovementPhase.ECCENTRIC && cycleStartTs == 0L) {
            cycleStartTs = timestampMs
            minElbowInCycle = 180f
            maxElbowAtTop = 0f
            belowFloorDurationMs = 0L
        }
        val delta = if (lastTs == 0L) 0L else (timestampMs - lastTs).coerceAtLeast(0L)
        lastTs = timestampMs

        val elbow = listOfNotNull(angles.anglesDeg["left_elbow_flexion"], angles.anglesDeg["right_elbow_flexion"]).average().toFloat()
        minElbowInCycle = minOf(minElbowInCycle, elbow)
        if (movement.currentPhase == MovementPhase.TOP) maxElbowAtTop = max(maxElbowAtTop, elbow)
        if (alignmentScore < thresholds.minimumGoodFormScore) belowFloorDurationMs += delta

        var latest: RepQualityResult? = null
        val raw = repTracking?.rawRepAttempts ?: lastRawAttempts
        if (raw > lastRawAttempts) {
            repCount += 1
            latest = finalizeRep(timestampMs, alignmentScore, dominantFault, movement.currentPhase)
            results += latest
            cycleStartTs = 0L
            lastRawAttempts = raw
        }

        val accepted = results.count { it.repAccepted }
        val rejected = results.size - accepted
        val avg = if (results.isEmpty()) 0 else results.map { it.repScore }.average().toInt()
        val best = results.maxOfOrNull { it.repScore } ?: 0
        val commonFailure = results.filter { !it.repAccepted }
            .groupingBy { it.failureReason }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()

        return RepQualitySnapshot(
            totalRepsDetected = results.size,
            acceptedReps = accepted,
            rejectedReps = rejected,
            averageRepQuality = avg,
            bestRepScore = best,
            mostCommonFailureReason = commonFailure,
            latestRep = latest,
        )
    }

    private fun finalizeRep(ts: Long, alignmentScore: Int, dominantFault: String, phase: MovementPhase): RepQualityResult {
        val durationMs = if (cycleStartTs > 0L) (ts - cycleStartTs).coerceAtLeast(1L) else 1L
        val depthScore = (1f - abs(minElbowInCycle - profile.repDepthTargetDeg) / 90f).coerceIn(0f, 1f).toScore()
        val lineScore = alignmentScore
        val shoulderScore = if (dominantFault == "shoulder_stack") 50 else 85
        val lockoutScore = (1f - abs(maxElbowAtTop - profile.repLockoutTargetDeg) / 50f).coerceIn(0f, 1f).toScore()
        val tempoScore = (1f - abs(durationMs - 1800f) / 1800f).coerceIn(0f, 1f).toScore()
        val repScore = (depthScore * 0.28f + lineScore * 0.27f + shoulderScore * 0.15f + lockoutScore * 0.2f + tempoScore * 0.1f).toInt()

        val faults = mutableListOf<String>()
        if (depthScore < 60) faults += "depth"
        if (lineScore < thresholds.minimumGoodFormScore) faults += "body_line"
        if (shoulderScore < 65) faults += "shoulder_position"
        if (lockoutScore < 60) faults += "lockout"
        if (tempoScore < 50) faults += "tempo"

        val accepted = phase == MovementPhase.TOP &&
            depthScore >= 55 &&
            lockoutScore >= 55 &&
            repScore >= thresholds.repAcceptanceThreshold &&
            belowFloorDurationMs <= thresholds.allowedRepAlignmentDropMs

        val reason = when {
            phase != MovementPhase.TOP -> "invalid_phase_sequence"
            depthScore < 55 -> "depth_not_reached"
            lockoutScore < 55 -> "top_not_completed"
            belowFloorDurationMs > thresholds.allowedRepAlignmentDropMs -> "alignment_floor_exceeded"
            repScore < thresholds.repAcceptanceThreshold -> "rep_score_below_threshold"
            else -> "accepted"
        }

        return RepQualityResult(repCount, repScore, accepted, faults, reason)
    }
}
