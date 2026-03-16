package com.inversioncoach.app.ui.live

import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameValidityGateTest {

    @Test
    fun rejectsHandstandFrameWhenRequiredWristsAreMissing() {
        val drillType = DrillType.WALL_HANDSTAND
        val gate = FrameValidityGate(drillType, DrillConfigs.byType(drillType))

        val frame = PoseFrame(
            timestampMs = 1_000L,
            joints = listOf(
                joint("left_shoulder", 0.45f, 0.25f),
                joint("right_shoulder", 0.48f, 0.24f),
                joint("left_hip", 0.46f, 0.45f),
                joint("right_hip", 0.49f, 0.44f),
                joint("left_ankle", 0.47f, 0.78f),
                joint("right_ankle", 0.5f, 0.79f),
            ),
            confidence = 0.95f,
        )

        val result = gate.evaluate(frame)

        assertFalse(result.isValid)
        assertEquals("missing_required_landmarks", result.reason)
    }

    @Test
    fun acceptsReasonableHandstandFrameWhenRequiredJointsPresent() {
        val drillType = DrillType.WALL_HANDSTAND
        val gate = FrameValidityGate(drillType, DrillConfigs.byType(drillType))

        val frame = PoseFrame(
            timestampMs = 1_000L,
            joints = listOf(
                joint("left_shoulder", 0.45f, 0.25f),
                joint("right_shoulder", 0.48f, 0.24f),
                joint("left_hip", 0.46f, 0.45f),
                joint("right_hip", 0.49f, 0.44f),
                joint("left_ankle", 0.47f, 0.78f),
                joint("right_ankle", 0.5f, 0.79f),
                joint("left_wrist", 0.44f, 0.62f),
                joint("right_wrist", 0.51f, 0.63f),
            ),
            confidence = 0.95f,
        )

        val result = gate.evaluate(frame)

        assertTrue(result.isValid)
        assertEquals("none", result.reason)
    }


    @Test
    fun acceptsFreestyleFrontViewWithoutWrongOrientation() {
        val drillType = DrillType.FREESTYLE
        val gate = FrameValidityGate(drillType, DrillConfigs.byType(drillType))

        val frame = PoseFrame(
            timestampMs = 1_000L,
            joints = listOf(
                joint("left_shoulder", 0.35f, 0.25f),
                joint("right_shoulder", 0.65f, 0.25f),
                joint("left_hip", 0.42f, 0.45f),
                joint("right_hip", 0.58f, 0.45f),
                joint("left_ankle", 0.44f, 0.78f),
                joint("right_ankle", 0.56f, 0.79f),
                joint("left_wrist", 0.38f, 0.62f),
                joint("right_wrist", 0.62f, 0.63f),
            ),
            confidence = 0.95f,
        )

        val result = gate.evaluate(frame)

        assertTrue(result.isValid)
        assertEquals("none", result.reason)
    }

    private fun joint(name: String, x: Float, y: Float): JointPoint = JointPoint(
        name = name,
        x = x,
        y = y,
        z = 0f,
        visibility = 0.95f,
    )
}
