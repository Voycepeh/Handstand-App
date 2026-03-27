package com.inversioncoach.app.calibration.rep

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepTemplateBuilderTest {
    private val builder = RepTemplateBuilder(normalizer = RepTimeNormalizer(outputLength = 8))

    @Test
    fun buildsTemplateFromMultipleReps() {
        val template = builder.build(
            drillType = DrillType.PIKE_PUSH_UP,
            profileVersion = 2,
            reps = listOf(makeRep(0L, 0f), makeRep(1000L, 0.01f)),
        )

        assertNotNull(template)
        assertEquals(DrillType.PIKE_PUSH_UP, template?.drillType)
        assertTrue(template?.temporalMetrics?.isNotEmpty() == true)
        assertTrue(template?.expectedRepFrames ?: 0 >= 1)
    }

    @Test
    fun containsExpectedMetricKeys() {
        val template = builder.build(
            drillType = DrillType.PIKE_PUSH_UP,
            profileVersion = 1,
            reps = listOf(makeRep(0L, 0f), makeRep(900L, 0.02f)),
        )

        val keys = template?.temporalMetrics?.map { it.metricKey }.orEmpty()
        assertTrue(keys.contains("elbow_flexion"))
        assertTrue(keys.contains("shoulder_flexion"))
    }

    @Test
    fun returnsNullOnEmptyInput() {
        val template = builder.build(
            drillType = DrillType.PIKE_PUSH_UP,
            profileVersion = 1,
            reps = emptyList(),
        )

        assertNull(template)
    }

    private fun makeRep(startTs: Long, xOffset: Float): List<PoseFrame> = (0..11).map { i ->
        val p = i / 11f
        PoseFrame(
            timestampMs = startTs + i * 33L,
            confidence = 0.95f,
            joints = listOf(
                JointPoint("left_shoulder", 0.4f + xOffset, 0.3f + p * 0.05f, 0f, 0.9f),
                JointPoint("right_shoulder", 0.6f + xOffset, 0.3f + p * 0.05f, 0f, 0.9f),
                JointPoint("left_elbow", 0.35f + xOffset, 0.4f + p * 0.07f, 0f, 0.9f),
                JointPoint("right_elbow", 0.65f + xOffset, 0.4f + p * 0.07f, 0f, 0.9f),
                JointPoint("left_wrist", 0.32f + xOffset, 0.55f + p * 0.07f, 0f, 0.9f),
                JointPoint("right_wrist", 0.68f + xOffset, 0.55f + p * 0.07f, 0f, 0.9f),
                JointPoint("left_hip", 0.44f + xOffset, 0.58f, 0f, 0.9f),
                JointPoint("right_hip", 0.56f + xOffset, 0.58f, 0f, 0.9f),
                JointPoint("left_knee", 0.44f + xOffset, 0.75f + p * 0.04f, 0f, 0.9f),
                JointPoint("right_knee", 0.56f + xOffset, 0.75f + p * 0.04f, 0f, 0.9f),
            ),
        )
    }
}
