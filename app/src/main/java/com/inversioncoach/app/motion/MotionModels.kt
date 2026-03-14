package com.inversioncoach.app.motion

enum class JointId {
    NOSE,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
}

data class Landmark2D(
    val x: Float,
    val y: Float,
)

data class PoseFrame(
    val timestampMs: Long,
    val landmarks: Map<JointId, Landmark2D>,
    val confidenceByLandmark: Map<JointId, Float>,
)

data class SmoothedPoseFrame(
    val timestampMs: Long,
    val filteredLandmarks: Map<JointId, Landmark2D>,
    val velocityByLandmark: Map<JointId, Landmark2D>,
)

data class AngleFrame(
    val timestampMs: Long,
    val anglesDeg: Map<String, Float>,
    val trunkLeanDeg: Float,
    val pelvicTiltDeg: Float,
    val lineDeviationNorm: Float,
)

enum class MovementPhase {
    SETUP,
    ECCENTRIC,
    BOTTOM,
    CONCENTRIC,
    TOP,
    HOLD,
    RESET,
}

data class MovementState(
    val currentPhase: MovementPhase,
    val repProgress: Float,
    val confidence: Float,
    val startedAt: Long,
    val completedRepCount: Int,
)

enum class FaultSeverity { LOW, MEDIUM, HIGH }

enum class BodySide { LEFT, RIGHT, BOTH, NONE }

data class FaultEvent(
    val code: String,
    val severity: FaultSeverity,
    val message: String,
    val side: BodySide,
    val startTimestampMs: Long,
    val endTimestampMs: Long? = null,
)
