package com.inversioncoach.app.motion

import com.inversioncoach.app.calibration.HoldMetricTemplate
import com.inversioncoach.app.calibration.HoldTemplate
import com.inversioncoach.app.calibration.HoldTemplateSource
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldTemplateComparatorTest {
    private val comparator = HoldTemplateComparator()

    @Test
    fun highScoreForCloseMatch() {
        val template = baseTemplate(0.20f)
        val frame = frameWithWristShoulderOffset(0.21f)

        val score = comparator.similarityScore(frame, template, null)

        assertTrue(score >= 70)
    }

    @Test
    fun lowerScoreForPoorAlignment() {
        val template = baseTemplate(0.20f)
        val frame = frameWithWristShoulderOffset(0.55f)

        val score = comparator.similarityScore(frame, template, null)

        assertTrue(score < 70)
    }


    @Test
    fun honorsMetricWeightsWhenCombiningScores() {
        val template = HoldTemplate(
            drillType = DrillType.FREE_HANDSTAND,
            profileVersion = 1,
            metrics = listOf(
                HoldMetricTemplate("wrist_shoulder_offset", 0.20f, 0.04f, 0.08f, 1.0f),
                HoldMetricTemplate("shoulder_hip_offset", 0.10f, 0.04f, 0.08f, 0.1f),
            ),
            minStableDurationMs = 2000L,
            source = HoldTemplateSource.DEFAULT_BASELINE,
        )

        val frame = frameWithWristShoulderOffset(0.20f)
        val score = comparator.similarityScore(frame, template, null)

        assertTrue(score >= 90)
    }

    private fun baseTemplate(wristShoulder: Float) = HoldTemplate(
        drillType = DrillType.FREE_HANDSTAND,
        profileVersion = 1,
        metrics = listOf(
            HoldMetricTemplate("wrist_shoulder_offset", wristShoulder, 0.04f, 0.08f, 1f),
            HoldMetricTemplate("shoulder_hip_offset", 0.10f, 0.04f, 0.08f, 1f),
        ),
        minStableDurationMs = 2000L,
        source = HoldTemplateSource.DEFAULT_BASELINE,
    )

    private fun frameWithWristShoulderOffset(offset: Float): PoseFrame = PoseFrame(
        timestampMs = 1L,
        joints = listOf(
            JointPoint("left_shoulder", 0.3f, 0.2f, 0f, 0.9f),
            JointPoint("right_shoulder", 0.7f, 0.2f, 0f, 0.9f),
            JointPoint("left_wrist", 0.3f - offset, 0.4f, 0f, 0.9f),
            JointPoint("right_wrist", 0.7f + offset, 0.4f, 0f, 0.9f),
            JointPoint("left_elbow", 0.2f, 0.3f, 0f, 0.9f),
            JointPoint("right_elbow", 0.8f, 0.3f, 0f, 0.9f),
            JointPoint("left_hip", 0.35f, 0.6f, 0f, 0.9f),
            JointPoint("right_hip", 0.65f, 0.6f, 0f, 0.9f),
            JointPoint("left_ankle", 0.35f, 0.95f, 0f, 0.9f),
            JointPoint("right_ankle", 0.65f, 0.95f, 0f, 0.9f),
        ),
        confidence = 0.95f,
    )
}
