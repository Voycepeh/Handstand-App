package com.inversioncoach.app.pose

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.model.SmoothedPoseFrame

class PoseSmoothingEngine {
    private var lastFrame: SmoothedPoseFrame? = null
    private var holdFrame: SmoothedPoseFrame? = null

    fun smooth(frame: PoseFrame): SmoothedPoseFrame {
        val previous = lastFrame
        val smoothed = if (previous == null) {
            SmoothedPoseFrame(
                timestampMs = frame.timestampMs,
                joints = frame.joints,
                confidence = frame.confidence,
                analysisWidth = frame.analysisWidth,
                analysisHeight = frame.analysisHeight,
                analysisRotationDegrees = frame.analysisRotationDegrees,
                mirrored = frame.mirrored,
            )
        } else {
            val joints = frame.joints.map { joint ->
                val old = previous.joints.firstOrNull { it.name == joint.name } ?: return@map joint
                val alpha = alphaFor(joint)
                if (joint.visibility < 0.2f) {
                    holdFrame?.joints?.firstOrNull { it.name == joint.name }?.copy(visibility = old.visibility * 0.8f) ?: old
                } else {
                    JointPoint(
                        name = joint.name,
                        x = alpha * joint.x + (1f - alpha) * old.x,
                        y = alpha * joint.y + (1f - alpha) * old.y,
                        z = alpha * joint.z + (1f - alpha) * old.z,
                        visibility = alpha * joint.visibility + (1f - alpha) * old.visibility,
                    )
                }
            }
            SmoothedPoseFrame(
                timestampMs = frame.timestampMs,
                joints = joints,
                confidence = frame.confidence,
                analysisWidth = frame.analysisWidth,
                analysisHeight = frame.analysisHeight,
                analysisRotationDegrees = frame.analysisRotationDegrees,
                mirrored = frame.mirrored,
            )
        }
        holdFrame = smoothed
        lastFrame = smoothed
        return smoothed
    }

    fun reset() {
        lastFrame = null
        holdFrame = null
    }

    private fun alphaFor(joint: JointPoint): Float = when {
        joint.name.contains("hip") || joint.name.contains("shoulder") || joint.name.contains("nose") -> 0.22f
        joint.name.contains("wrist") || joint.name.contains("elbow") || joint.name.contains("ankle") -> 0.45f
        else -> 0.35f
    }
}
