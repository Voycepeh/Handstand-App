package com.inversioncoach.app.pose.model

import com.google.mlkit.vision.pose.PoseLandmark
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame as LegacyPoseFrame

class MlKitPoseMapper {

    fun landmarkType(type: Int): JointType = when (type) {
        PoseLandmark.NOSE -> JointType.NOSE
        PoseLandmark.LEFT_EYE_INNER -> JointType.LEFT_EYE_INNER
        PoseLandmark.LEFT_EYE -> JointType.LEFT_EYE
        PoseLandmark.LEFT_EYE_OUTER -> JointType.LEFT_EYE_OUTER
        PoseLandmark.RIGHT_EYE_INNER -> JointType.RIGHT_EYE_INNER
        PoseLandmark.RIGHT_EYE -> JointType.RIGHT_EYE
        PoseLandmark.RIGHT_EYE_OUTER -> JointType.RIGHT_EYE_OUTER
        PoseLandmark.LEFT_EAR -> JointType.LEFT_EAR
        PoseLandmark.RIGHT_EAR -> JointType.RIGHT_EAR
        PoseLandmark.LEFT_MOUTH -> JointType.MOUTH_LEFT
        PoseLandmark.RIGHT_MOUTH -> JointType.MOUTH_RIGHT
        PoseLandmark.LEFT_SHOULDER -> JointType.LEFT_SHOULDER
        PoseLandmark.RIGHT_SHOULDER -> JointType.RIGHT_SHOULDER
        PoseLandmark.LEFT_ELBOW -> JointType.LEFT_ELBOW
        PoseLandmark.RIGHT_ELBOW -> JointType.RIGHT_ELBOW
        PoseLandmark.LEFT_WRIST -> JointType.LEFT_WRIST
        PoseLandmark.RIGHT_WRIST -> JointType.RIGHT_WRIST
        PoseLandmark.LEFT_PINKY -> JointType.LEFT_PINKY
        PoseLandmark.RIGHT_PINKY -> JointType.RIGHT_PINKY
        PoseLandmark.LEFT_INDEX -> JointType.LEFT_INDEX
        PoseLandmark.RIGHT_INDEX -> JointType.RIGHT_INDEX
        PoseLandmark.LEFT_THUMB -> JointType.LEFT_THUMB
        PoseLandmark.RIGHT_THUMB -> JointType.RIGHT_THUMB
        PoseLandmark.LEFT_HIP -> JointType.LEFT_HIP
        PoseLandmark.RIGHT_HIP -> JointType.RIGHT_HIP
        PoseLandmark.LEFT_KNEE -> JointType.LEFT_KNEE
        PoseLandmark.RIGHT_KNEE -> JointType.RIGHT_KNEE
        PoseLandmark.LEFT_ANKLE -> JointType.LEFT_ANKLE
        PoseLandmark.RIGHT_ANKLE -> JointType.RIGHT_ANKLE
        PoseLandmark.LEFT_HEEL -> JointType.LEFT_HEEL
        PoseLandmark.RIGHT_HEEL -> JointType.RIGHT_HEEL
        PoseLandmark.LEFT_FOOT_INDEX -> JointType.LEFT_FOOT_INDEX
        PoseLandmark.RIGHT_FOOT_INDEX -> JointType.RIGHT_FOOT_INDEX
        else -> JointType.UNKNOWN
    }

    fun toLegacy(frame: PoseFrame): LegacyPoseFrame = LegacyPoseFrame(
        timestampMs = frame.timestampMs,
        joints = frame.joints.map { landmark ->
            JointPoint(
                name = if (landmark.jointType == JointType.UNKNOWN) "unknown" else landmark.jointType.legacyName,
                x = landmark.x,
                y = landmark.y,
                z = landmark.z,
                visibility = landmark.visibility,
            )
        },
        confidence = frame.confidence,
        landmarksDetected = frame.landmarksDetected,
        inferenceTimeMs = frame.inferenceTimeMs,
        droppedFrames = frame.droppedFrames,
        rejectionReason = frame.rejectionReason,
        analysisWidth = frame.analysisWidth,
        analysisHeight = frame.analysisHeight,
        analysisRotationDegrees = frame.analysisRotationDegrees,
        mirrored = frame.mirrored,
    )
}
