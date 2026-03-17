package com.inversioncoach.app.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class MovementTrackingTest {
    @Test
    fun repBasedCountsOnlyAlignedRep() {
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

        val goodRep = listOf(170f to true, 150f to true, 90f to true, 120f to true, 170f to true)
        val badRep = listOf(170f to true, 150f to false, 90f to false, 120f to false, 170f to true)
        var ts = 0L
        (goodRep + badRep).forEach { (angle, aligned) ->
            ts += 10L
            detector.update(
                frame = AngleFrame(
                    timestampMs = ts,
                    anglesDeg = mapOf("left_elbow_flexion" to angle),
                    trunkLeanDeg = 0f,
                    pelvicTiltDeg = 0f,
                    lineDeviationNorm = 0f,
                ),
                isAligned = aligned,
            )
        }

        val snapshot = detector.snapshot()
        assertEquals(2, snapshot.rawRepAttempts)
        assertEquals(1, snapshot.validRepCount)
    }

    @Test
    fun holdTrackerTracksSegmentsAndBestStreak() {
        val tracker = HoldAlignmentTracker(minUnalignedDurationToBreakMs = 200)
        val sequence = listOf(
            0L to true,
            5000L to true,
            7000L to false,
            13000L to false,
            16000L to true,
            19000L to true,
        )

        var snapshot = tracker.snapshot()
        sequence.forEach { (ts, aligned) ->
            snapshot = tracker.update(ts, aligned)
        }

        assertEquals(19000L, snapshot.totalSessionDurationMs)
        assertEquals(8000L, snapshot.totalAlignedDurationMs)
        assertEquals(5000L, snapshot.bestAlignedStreakMs)
        assertEquals(3000L, snapshot.currentAlignedStreakMs)
    }

}
