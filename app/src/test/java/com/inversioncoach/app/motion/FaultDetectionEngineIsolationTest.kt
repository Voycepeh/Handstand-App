package com.inversioncoach.app.motion

import org.junit.Assert.assertTrue
import org.junit.Test

class FaultDetectionEngineIsolationTest {

    @Test
    fun squatPattern_doesNotEmitPushFaults() {
        val engine = FaultDetectionEngine(
            movementPattern = MovementPattern.SQUAT_PATTERN,
            allowedFaultCodes = listOf("knee_valgus", "heel_lift", "depth_short"),
            persistenceFrames = 1,
        )

        val angleFrame = AngleFrame(
            timestampMs = 1_000L,
            anglesDeg = mapOf(
                "left_shoulder_opening" to 30f,
                "right_shoulder_opening" to 30f,
                "left_elbow_flexion" to 160f,
            ),
            trunkLeanDeg = 80f,
            pelvicTiltDeg = 0f,
            lineDeviationNorm = 1.2f,
        )
        val movement = MovementState(MovementPhase.BOTTOM, repProgress = 0.5f, confidence = 1f, startedAt = 0L, completedRepCount = 0)

        val faults = engine.detect(angleFrame, movement)

        assertTrue(faults.isEmpty())
    }
}
