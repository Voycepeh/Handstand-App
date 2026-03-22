package com.inversioncoach.app.pose

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PoseSmoothingEngineTest {
    @Test
    fun torsoSmoothingIsStrongerThanWristSmoothing() {
        val engine = PoseSmoothingEngine()
        engine.smooth(frame(0.1f, 0.1f))
        val smoothed = engine.smooth(frame(0.9f, 0.9f))
        val hip = smoothed.joints.first { it.name == "left_hip" }
        val wrist = smoothed.joints.first { it.name == "left_wrist" }
        assertTrue(hip.x < wrist.x)
    }

    @Test
    fun resetClearsPreviousFrameState() {
        val engine = PoseSmoothingEngine()
        engine.smooth(frame(0.1f, 0.1f))
        engine.reset()
        val smoothed = engine.smooth(frame(0.9f, 0.9f))
        assertEquals(0.9f, smoothed.joints.first { it.name == "left_hip" }.x, 0.0001f)
    }

    private fun frame(hipX: Float, wristX: Float) = PoseFrame(
        timestampMs = System.currentTimeMillis(),
        joints = listOf(
            JointPoint("left_hip", hipX, 0.5f, 0f, 0.9f),
            JointPoint("left_wrist", wristX, 0.5f, 0f, 0.9f),
        ),
        confidence = 0.9f,
    )
}
