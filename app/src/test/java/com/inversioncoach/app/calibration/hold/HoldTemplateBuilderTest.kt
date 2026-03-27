package com.inversioncoach.app.calibration.hold

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldTemplateBuilderTest {
    private val builder = HoldTemplateBuilder()

    @Test
    fun buildsTemplateFromStableFrames() {
        val frames = (0 until 20).map { index -> stableFrame(index.toLong(), noseX = 0.5f + (index % 2) * 0.001f) }

        val template = builder.build(DrillType.FREE_HANDSTAND, 3, null, frames)

        assertNotNull(template)
        assertEquals(DrillType.FREE_HANDSTAND, template?.drillType)
        assertEquals(3, template?.profileVersion)
        assertTrue(template!!.metrics.isNotEmpty())
    }

    @Test
    fun returnsNullIfNoStableWindow() {
        val frames = (0 until 20).map { index -> stableFrame(index.toLong(), noseX = index * 0.2f) }

        val template = builder.build(DrillType.FREE_HANDSTAND, 2, null, frames)

        assertNull(template)
    }

    @Test
    fun storesExpectedMetricKeys() {
        val frames = (0 until 20).map { index -> stableFrame(index.toLong(), noseX = 0.5f) }

        val template = builder.build(DrillType.FREE_HANDSTAND, 2, null, frames)!!
        val keys = template.metrics.map { it.metricKey }.toSet()

        assertTrue(keys.contains("wrist_shoulder_offset"))
        assertTrue(keys.contains("shoulder_hip_offset"))
        assertTrue(keys.contains("hip_ankle_offset"))
        assertTrue(keys.contains("torso_line_deviation"))
        assertTrue(keys.contains("left_right_symmetry"))
        assertTrue(keys.contains("stability_score"))
    }

    private fun stableFrame(timestampMs: Long, noseX: Float): PoseFrame = PoseFrame(
        timestampMs = timestampMs,
        joints = listOf(
            JointPoint("nose", noseX, 0.1f, 0f, 0.9f),
            JointPoint("left_shoulder", 0.45f, 0.2f, 0f, 0.9f),
            JointPoint("right_shoulder", 0.55f, 0.2f, 0f, 0.9f),
            JointPoint("left_elbow", 0.42f, 0.3f, 0f, 0.9f),
            JointPoint("right_elbow", 0.58f, 0.3f, 0f, 0.9f),
            JointPoint("left_wrist", 0.40f, 0.4f, 0f, 0.9f),
            JointPoint("right_wrist", 0.60f, 0.4f, 0f, 0.9f),
            JointPoint("left_hip", 0.46f, 0.55f, 0f, 0.9f),
            JointPoint("right_hip", 0.54f, 0.55f, 0f, 0.9f),
            JointPoint("left_ankle", 0.47f, 0.95f, 0f, 0.9f),
            JointPoint("right_ankle", 0.53f, 0.95f, 0f, 0.9f),
        ),
        confidence = 0.95f,
    )
}
