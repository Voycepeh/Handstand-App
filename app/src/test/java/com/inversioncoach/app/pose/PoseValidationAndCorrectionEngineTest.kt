package com.inversioncoach.app.pose

import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PoseValidationAndCorrectionEngineTest {
    @Test
    fun marksCollapsedHipAsUnreliableWhenInverted() {
        val engine = PoseValidationAndCorrectionEngine()
        val frame = PoseFrame(
            timestampMs = 1L,
            joints = listOf(
                jp("left_shoulder", 0.4f, 0.7f),
                jp("right_shoulder", 0.6f, 0.7f),
                jp("left_hip", 0.45f, 0.45f),
                jp("right_hip", 0.55f, 0.45f),
                jp("left_knee", 0.47f, 0.47f),
                jp("right_knee", 0.57f, 0.47f),
            ),
            confidence = 0.9f,
        )

        val result = engine.process(frame, null)
        assertTrue(result.inversionDetected)
        assertTrue("left_hip" in result.unreliableJointNames || "right_hip" in result.unreliableJointNames)
    }

    @Test
    fun usesProfileFemurForFallbackThreshold() {
        val engine = PoseValidationAndCorrectionEngine(hipCollapseRatioThreshold = 0.8f)
        val profile = UserBodyProfile(
            shoulderWidthNormalized = 0.8f,
            hipWidthNormalized = 0.7f,
            torsoLengthNormalized = 0.9f,
            upperArmLengthNormalized = 0.4f,
            forearmLengthNormalized = 0.35f,
            femurLengthNormalized = 0.5f,
            shinLengthNormalized = 0.5f,
            leftRightConsistency = 0.95f,
        )
        val frame = PoseFrame(
            timestampMs = 1L,
            joints = listOf(
                jp("left_shoulder", 0.4f, 0.75f), jp("right_shoulder", 0.6f, 0.75f),
                jp("left_hip", 0.45f, 0.45f), jp("right_hip", 0.55f, 0.45f),
                jp("left_knee", 0.49f, 0.46f), jp("right_knee", 0.59f, 0.46f),
            ),
            confidence = 0.8f,
        )

        val result = engine.process(frame, profile)
        assertTrue(result.unreliableJointNames.isNotEmpty())
    }

    private fun jp(name: String, x: Float, y: Float) = JointPoint(name, x, y, 0f, 0.9f)

    @Test
    fun resetClearsPreviousJointHistory() {
        val engine = PoseValidationAndCorrectionEngine(discontinuityThreshold = 0.05f)
        val stable = PoseFrame(1L, listOf(jp("left_shoulder", 0.2f, 0.8f), jp("right_shoulder", 0.8f, 0.8f), jp("left_hip", 0.3f, 0.7f), jp("right_hip", 0.7f, 0.7f)), 0.9f)
        engine.process(stable, null)
        engine.reset()
        val moved = stable.copy(timestampMs = 2L, joints = stable.joints.map { it.copy(x = it.x + 0.2f) })
        val result = engine.process(moved, null)
        assertEquals(0, result.unreliableJointNames.size)
    }
}
