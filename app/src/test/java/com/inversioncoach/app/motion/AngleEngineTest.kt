package com.inversioncoach.app.motion

import org.junit.Assert.assertTrue
import org.junit.Test

class AngleEngineTest {
    private val engine = AngleEngine()

    @Test
    fun computesElbowFlexionNearNinetyDegrees() {
        val frame = SmoothedPoseFrame(
            timestampMs = 1L,
            filteredLandmarks = mapOf(
                JointId.LEFT_SHOULDER to Landmark2D(0f, 1f),
                JointId.LEFT_ELBOW to Landmark2D(0f, 0f),
                JointId.LEFT_WRIST to Landmark2D(1f, 0f),
                JointId.RIGHT_SHOULDER to Landmark2D(0.1f, 1f),
                JointId.RIGHT_HIP to Landmark2D(0.1f, 2f),
                JointId.LEFT_HIP to Landmark2D(0f, 2f),
                JointId.RIGHT_ELBOW to Landmark2D(0.1f, 0f),
                JointId.RIGHT_WRIST to Landmark2D(1.1f, 0f),
                JointId.LEFT_KNEE to Landmark2D(0f, 3f),
                JointId.RIGHT_KNEE to Landmark2D(0.1f, 3f),
                JointId.LEFT_ANKLE to Landmark2D(0f, 4f),
                JointId.RIGHT_ANKLE to Landmark2D(0.1f, 4f),
            ),
            velocityByLandmark = emptyMap(),
        )

        val angles = engine.compute(frame)
        val elbow = angles.anglesDeg["left_elbow_flexion"] ?: 0f
        assertTrue("elbow=$elbow", elbow in 85f..95f)
    }
}
