package com.inversioncoach.app.motion

import com.inversioncoach.app.model.PoseFrame as LegacyPoseFrame

class MotionAnalysisPipeline {
    private val smoother = TemporalPoseSmoother()
    private val angleEngine = AngleEngine()
    private val phaseDetector = MovementPhaseDetector(
        thresholds = PhaseThresholds(
            downStartDeg = 165f,
            bottomDeg = 95f,
            upStartDeg = 110f,
            topDeg = 168f,
        ),
        trackedAngle = "left_elbow_flexion",
    )
    private val faultEngine = FaultDetectionEngine()
    private val feedbackEngine = FeedbackEngine()

    data class Output(
        val smoothed: SmoothedPoseFrame,
        val angles: AngleFrame,
        val movement: MovementState,
        val faults: List<FaultEvent>,
        val cue: LiveCue?,
    )

    fun analyze(frame: LegacyPoseFrame): Output {
        val motionFrame = PoseFrame(
            timestampMs = frame.timestampMs,
            landmarks = frame.joints.mapNotNull { raw ->
                mapJoint(raw.name)?.let { it to Landmark2D(raw.x, raw.y) }
            }.toMap(),
            confidenceByLandmark = frame.joints.mapNotNull { raw -> mapJoint(raw.name)?.let { it to raw.visibility } }.toMap(),
        )

        val smoothed = smoother.smooth(motionFrame)
        val angles = angleEngine.compute(smoothed)
        val movement = phaseDetector.update(angles)
        val faults = faultEngine.detect(angles, movement)
        val cue = feedbackEngine.selectCue(faults, frame.timestampMs)

        return Output(smoothed, angles, movement, faults, cue)
    }

    private fun mapJoint(name: String): JointId? = when (name) {
        "nose" -> JointId.NOSE
        "left_shoulder" -> JointId.LEFT_SHOULDER
        "right_shoulder" -> JointId.RIGHT_SHOULDER
        "left_elbow" -> JointId.LEFT_ELBOW
        "right_elbow" -> JointId.RIGHT_ELBOW
        "left_wrist" -> JointId.LEFT_WRIST
        "right_wrist" -> JointId.RIGHT_WRIST
        "left_hip" -> JointId.LEFT_HIP
        "right_hip" -> JointId.RIGHT_HIP
        "left_knee" -> JointId.LEFT_KNEE
        "right_knee" -> JointId.RIGHT_KNEE
        "left_ankle" -> JointId.LEFT_ANKLE
        "right_ankle" -> JointId.RIGHT_ANKLE
        else -> null
    }
}
