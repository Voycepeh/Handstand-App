package com.inversioncoach.app.calibration.rep

import com.inversioncoach.app.calibration.RepTemplate
import com.inversioncoach.app.calibration.RepTemplateSource
import com.inversioncoach.app.calibration.TemporalMetricProfile
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertTrue
import org.junit.Test

class RepSimilarityComparatorTest {
    private val comparator = RepSimilarityComparator(normalizer = RepTimeNormalizer(outputLength = 8))

    @Test
    fun closeRepScoresHigherThanPoorRep() {
        val template = RepTemplate(
            drillType = DrillType.PIKE_PUSH_UP,
            profileVersion = 1,
            temporalMetrics = listOf(
                TemporalMetricProfile(
                    metricKey = "wrist_shoulder_offset",
                    meanSeries = listOf(0.1f, 0.12f, 0.15f, 0.2f, 0.2f, 0.15f, 0.12f, 0.1f),
                    toleranceSeries = List(8) { 0.05f },
                ),
            ),
            expectedRepFrames = 12,
            minRomThreshold = null,
            source = RepTemplateSource.CLEAN_REP_CAPTURE,
        )

        val closeScore = comparator.similarityScore(makeRep(0f), template)
        val poorScore = comparator.similarityScore(makeRep(0.3f), template)

        assertTrue(closeScore > poorScore)
        assertTrue(closeScore >= 70)
    }

    private fun makeRep(offset: Float): List<PoseFrame> = (0..11).map { i ->
        val p = i / 11f
        PoseFrame(
            timestampMs = i * 33L,
            confidence = 0.95f,
            joints = listOf(
                JointPoint("left_shoulder", 0.4f, 0.3f, 0f, 0.9f),
                JointPoint("right_shoulder", 0.6f, 0.3f, 0f, 0.9f),
                JointPoint("left_wrist", 0.3f + p * 0.1f + offset, 0.5f, 0f, 0.9f),
                JointPoint("right_wrist", 0.7f - p * 0.1f + offset, 0.5f, 0f, 0.9f),
            ),
        )
    }
}
