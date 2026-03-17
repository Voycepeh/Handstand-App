package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertNotNull
import org.junit.Test

class JointAngleEngineMappingTest {

    @Test
    fun unknownLandmarkNamesAreIgnoredWithoutCrashing() {
        val frame = PoseFrame(
            timestampMs = 0L,
            confidence = 0.9f,
            joints = listOf(
                JointPoint("left_shoulder", 0.4f, 0.2f, 0f, 0.8f),
                JointPoint("right_shoulder", 0.6f, 0.2f, 0f, 0.8f),
                JointPoint("left_eye_inner", 0.45f, 0.1f, 0f, 0.8f),
            ),
        )

        val result = JointAngleEngine().compute(frame)

        assertNotNull(result)
    }
}
