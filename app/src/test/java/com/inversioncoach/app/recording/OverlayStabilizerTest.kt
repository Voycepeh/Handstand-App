package com.inversioncoach.app.recording

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SmoothedPoseFrame
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStabilizerTest {
    private val stabilizer = OverlayStabilizer()

    @Test
    fun holdsLastGoodLandmarksAcrossSingleBadFrame() {
        val good = SmoothedPoseFrame(
            timestampMs = 1000L,
            confidence = 0.9f,
            joints = listOf(
                JointPoint("left_shoulder", 0.3f, 0.4f, 0f, 0.9f),
                JointPoint("right_shoulder", 0.35f, 0.4f, 0f, 0.9f),
                JointPoint("left_hip", 0.3f, 0.6f, 0f, 0.9f),
                JointPoint("right_hip", 0.35f, 0.6f, 0f, 0.9f),
                JointPoint("left_ankle", 0.3f, 0.8f, 0f, 0.9f),
                JointPoint("right_ankle", 0.35f, 0.8f, 0f, 0.9f),
            ),
        )
        val bad = good.copy(
            timestampMs = 1100L,
            confidence = 0.2f,
            joints = good.joints.map { it.copy(visibility = 0.1f) },
        )

        val first = stabilizer.stabilize(good, SessionMode.DRILL)
        val second = stabilizer.stabilize(bad, SessionMode.DRILL)

        assertTrue(first.smoothedLandmarks.isNotEmpty())
        assertTrue(second.smoothedLandmarks == first.smoothedLandmarks)
    }
}
