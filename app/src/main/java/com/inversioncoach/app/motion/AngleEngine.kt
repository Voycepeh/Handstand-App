package com.inversioncoach.app.motion

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class AngleEngine {
    fun compute(frame: SmoothedPoseFrame): AngleFrame {
        val a = mutableMapOf<String, Float>()

        a["left_elbow_flexion"] = jointAngle(frame, JointId.LEFT_SHOULDER, JointId.LEFT_ELBOW, JointId.LEFT_WRIST)
        a["right_elbow_flexion"] = jointAngle(frame, JointId.RIGHT_SHOULDER, JointId.RIGHT_ELBOW, JointId.RIGHT_WRIST)
        a["left_shoulder_opening"] = jointAngle(frame, JointId.LEFT_ELBOW, JointId.LEFT_SHOULDER, JointId.LEFT_HIP)
        a["right_shoulder_opening"] = jointAngle(frame, JointId.RIGHT_ELBOW, JointId.RIGHT_SHOULDER, JointId.RIGHT_HIP)
        a["left_hip_flexion"] = jointAngle(frame, JointId.LEFT_SHOULDER, JointId.LEFT_HIP, JointId.LEFT_KNEE)
        a["right_hip_flexion"] = jointAngle(frame, JointId.RIGHT_SHOULDER, JointId.RIGHT_HIP, JointId.RIGHT_KNEE)
        a["left_knee_flexion"] = jointAngle(frame, JointId.LEFT_HIP, JointId.LEFT_KNEE, JointId.LEFT_ANKLE)
        a["right_knee_flexion"] = jointAngle(frame, JointId.RIGHT_HIP, JointId.RIGHT_KNEE, JointId.RIGHT_ANKLE)
        a["left_ankle_angle"] = segmentAngle(frame, JointId.LEFT_KNEE, JointId.LEFT_ANKLE)
        a["right_ankle_angle"] = segmentAngle(frame, JointId.RIGHT_KNEE, JointId.RIGHT_ANKLE)

        val trunkLean = trunkToVertical(frame)
        val pelvisTilt = pelvicTilt(frame)
        val lineDeviation = stackDeviation(frame)
        a["trunk_to_vertical"] = trunkLean
        a["wrist_to_shoulder_line"] = wristShoulderLine(frame)

        return AngleFrame(
            timestampMs = frame.timestampMs,
            anglesDeg = a,
            trunkLeanDeg = trunkLean,
            pelvicTiltDeg = pelvisTilt,
            lineDeviationNorm = lineDeviation,
        )
    }

    private fun jointAngle(frame: SmoothedPoseFrame, a: JointId, b: JointId, c: JointId): Float {
        val p1 = frame.filteredLandmarks[a] ?: return 0f
        val p2 = frame.filteredLandmarks[b] ?: return 0f
        val p3 = frame.filteredLandmarks[c] ?: return 0f
        val v1x = p1.x - p2.x
        val v1y = p1.y - p2.y
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y
        val dot = v1x * v2x + v1y * v2y
        val mag = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
        if (mag <= 1e-6f) return 0f
        val cos = (dot / mag).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cos).toDouble()).toFloat()
    }

    private fun segmentAngle(frame: SmoothedPoseFrame, from: JointId, to: JointId): Float {
        val p1 = frame.filteredLandmarks[from] ?: return 0f
        val p2 = frame.filteredLandmarks[to] ?: return 0f
        return Math.toDegrees(atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())).toFloat()
    }

    private fun trunkToVertical(frame: SmoothedPoseFrame): Float {
        val shoulder = midpoint(frame, JointId.LEFT_SHOULDER, JointId.RIGHT_SHOULDER) ?: return 0f
        val hip = midpoint(frame, JointId.LEFT_HIP, JointId.RIGHT_HIP) ?: return 0f
        val dx = shoulder.x - hip.x
        val dy = shoulder.y - hip.y
        return abs(Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble()))).toFloat()
    }

    private fun pelvicTilt(frame: SmoothedPoseFrame): Float {
        val leftHip = frame.filteredLandmarks[JointId.LEFT_HIP] ?: return 0f
        val rightHip = frame.filteredLandmarks[JointId.RIGHT_HIP] ?: return 0f
        return Math.toDegrees(atan2((rightHip.y - leftHip.y).toDouble(), (rightHip.x - leftHip.x).toDouble())).toFloat()
    }

    private fun wristShoulderLine(frame: SmoothedPoseFrame): Float {
        val shoulder = midpoint(frame, JointId.LEFT_SHOULDER, JointId.RIGHT_SHOULDER) ?: return 0f
        val wrist = midpoint(frame, JointId.LEFT_WRIST, JointId.RIGHT_WRIST) ?: return 0f
        return Math.toDegrees(atan2((wrist.y - shoulder.y).toDouble(), (wrist.x - shoulder.x).toDouble())).toFloat()
    }

    private fun stackDeviation(frame: SmoothedPoseFrame): Float {
        val shoulder = midpoint(frame, JointId.LEFT_SHOULDER, JointId.RIGHT_SHOULDER) ?: return 0f
        val hip = midpoint(frame, JointId.LEFT_HIP, JointId.RIGHT_HIP) ?: return 0f
        val ankle = midpoint(frame, JointId.LEFT_ANKLE, JointId.RIGHT_ANKLE) ?: return 0f
        return (abs(shoulder.x - hip.x) + abs(hip.x - ankle.x)) / 2f
    }

    private fun midpoint(frame: SmoothedPoseFrame, a: JointId, b: JointId): Landmark2D? {
        val pa = frame.filteredLandmarks[a] ?: return null
        val pb = frame.filteredLandmarks[b] ?: return null
        return Landmark2D((pa.x + pb.x) / 2f, (pa.y + pb.y) / 2f)
    }
}
