package com.inversioncoach.app.movementprofile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementComparisonEngineTest {
    @Test
    fun compareProducesDeterministicScoresAndTopDifferences() {
        val template = ReferenceTemplateDefinition(
            id = "template-1",
            templateName = "Template",
            drillId = "seed_free_handstand",
            description = "",
            phaseTimingMs = linkedMapOf("setup" to 1000L, "hold" to 3000L),
            alignmentTargets = mapOf("alignment_score" to 0.9f, "trunk_lean" to 3f),
            stabilityTargets = mapOf("alignment_score" to 0.05f),
            assetPath = "assets/template-1.json",
        )
        val analysis = UploadedVideoAnalysisResult(
            inferredView = CameraViewConstraint.SIDE_LEFT,
            phaseTimeline = listOf(
                0L to "setup",
                500L to "hold",
                2600L to "hold",
            ),
            overlayTimeline = listOf(
                OverlayTimelinePoint(0L, emptyList(), mapOf("alignment_score" to 0.7f, "trunk_lean" to 6f), "setup", 1f),
                OverlayTimelinePoint(500L, emptyList(), mapOf("alignment_score" to 0.75f, "trunk_lean" to 5.5f), "hold", 1f),
                OverlayTimelinePoint(1000L, emptyList(), mapOf("alignment_score" to 0.8f, "trunk_lean" to 5f), "hold", 1f),
            ),
            droppedFrames = 0,
            telemetry = emptyMap(),
            candidate = MovementTemplateCandidate(
                id = "candidate",
                sourceSessionId = "session",
                tentativeName = null,
                movementTypeGuess = MovementType.HOLD,
                detectedView = CameraViewConstraint.SIDE_LEFT,
                keyJoints = emptySet(),
                candidatePhases = emptyList(),
                candidateRomMetrics = emptyMap(),
                thresholdSuggestions = emptyMap(),
                confidence = 1f,
                status = CandidateStatus.DRAFT,
            ),
        )

        val result = MovementComparisonEngine().compare(template, analysis)

        assertEquals(2, result.phaseScores.size)
        assertTrue(result.overallSimilarityScore in 0..100)
        assertTrue(result.topDifferences.size <= 3)
        assertTrue(result.alignmentScore < 90)
    }
}
