package com.inversioncoach.app.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementPhaseDetectorTest {
    @Test
    fun countsRepAfterBottomToTopTransition() {
        val detector = MovementPhaseDetector(
            thresholds = PhaseThresholds(
                downStartDeg = 165f,
                bottomDeg = 95f,
                upStartDeg = 110f,
                topDeg = 168f,
                minDwellMs = 1,
            ),
            trackedAngle = "left_elbow_flexion",
        )

        val timeline = listOf(170f, 150f, 90f, 120f, 170f)
        var ts = 0L
        var last = MovementState(MovementPhase.SETUP, 0f, 0f, 0L, 0)

        timeline.forEach { angle ->
            ts += 10
            last = detector.update(
                AngleFrame(
                    timestampMs = ts,
                    anglesDeg = mapOf("left_elbow_flexion" to angle),
                    trunkLeanDeg = 0f,
                    pelvicTiltDeg = 0f,
                    lineDeviationNorm = 0f,
                ),
            )
        }

        assertEquals(1, last.completedRepCount)
        assertTrue(last.repProgress >= 0.75f)
    }
}
