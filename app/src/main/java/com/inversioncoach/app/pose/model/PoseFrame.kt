package com.inversioncoach.app.pose.model

data class PoseFrame(
    val timestampMs: Long,
    val joints: List<JointLandmark>,
    val confidence: Float,
    val landmarksDetected: Int,
    val inferenceTimeMs: Long = 0L,
    val droppedFrames: Int = 0,
    val rejectionReason: String = "none",
    val analysisWidth: Int = 0,
    val analysisHeight: Int = 0,
    val analysisRotationDegrees: Int = 0,
    val mirrored: Boolean = false,
)
