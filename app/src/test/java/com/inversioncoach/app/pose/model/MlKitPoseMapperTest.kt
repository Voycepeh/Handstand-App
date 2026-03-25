package com.inversioncoach.app.pose.model

import com.google.mlkit.vision.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitPoseMapperTest {
    @Test
    fun mapper_producesLegacyPoseFrameFromInternalPoseFrame() {
        val mapper = MlKitPoseMapper()
        val internal = PoseFrame(
            timestampMs = 42L,
            joints = listOf(JointLandmark(JointType.LEFT_SHOULDER, 0.2f, 0.3f, 0f, 0.9f)),
            confidence = 0.85f,
            landmarksDetected = 1,
        )

        val legacy = mapper.toLegacy(internal)

        assertEquals(42L, legacy.timestampMs)
        assertEquals("left_shoulder", legacy.joints.first().name)
        assertEquals(0.85f, legacy.confidence)
        assertEquals(JointType.LEFT_SHOULDER, mapper.landmarkType(PoseLandmark.LEFT_SHOULDER))
    }
}
