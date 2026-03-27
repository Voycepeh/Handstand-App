package com.inversioncoach.app.calibration

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationEngineTest {
    @Test
    fun buildsProfileWhenAllStepsPresent() {
        val session = CalibrationSession(DrillType.FREE_HANDSTAND)
        CalibrationStep.entries.forEach {
            session.record(
                CalibrationCapture(
                    step = it,
                    startedAtMs = 0L,
                    completedAtMs = 1000L,
                    acceptedFrames = listOf(sampleFrame()),
                    rejectedFrameCount = 0,
                ),
            )
        }
        val profile = CalibrationEngine().buildProfile(session)
        assertNotNull(profile)
    }

    @Test
    fun returnsNullWhenCalibrationMissing() {
        val session = CalibrationSession(DrillType.FREE_HANDSTAND)
        session.record(
            CalibrationCapture(
                step = CalibrationStep.FRONT_NEUTRAL,
                startedAtMs = 0L,
                completedAtMs = 1000L,
                acceptedFrames = listOf(sampleFrame()),
                rejectedFrameCount = 0,
            ),
        )
        assertNull(CalibrationEngine().buildProfile(session))
    }

    private fun sampleFrame(): PoseFrame = PoseFrame(
        timestampMs = 0L,
        joints = listOf(
            JointPoint("left_shoulder", 0.3f, 0.2f, 0f, 0.9f),
            JointPoint("right_shoulder", 0.7f, 0.2f, 0f, 0.9f),
            JointPoint("left_hip", 0.35f, 0.6f, 0f, 0.9f),
            JointPoint("right_hip", 0.65f, 0.6f, 0f, 0.9f),
            JointPoint("left_elbow", 0.2f, 0.3f, 0f, 0.9f),
            JointPoint("right_elbow", 0.8f, 0.3f, 0f, 0.9f),
            JointPoint("left_wrist", 0.15f, 0.4f, 0f, 0.9f),
            JointPoint("right_wrist", 0.85f, 0.4f, 0f, 0.9f),
            JointPoint("left_knee", 0.35f, 0.8f, 0f, 0.9f),
            JointPoint("right_knee", 0.65f, 0.8f, 0f, 0.9f),
            JointPoint("left_ankle", 0.35f, 0.95f, 0f, 0.9f),
            JointPoint("right_ankle", 0.65f, 0.95f, 0f, 0.9f),
        ),
        confidence = 0.95f,
    )
}
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
