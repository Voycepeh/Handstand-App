package com.inversioncoach.app.pose

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.model.SmoothedPoseFrame

class PoseSmoother(
    private val alpha: Float = 0.35f,
) {
    private var lastFrame: SmoothedPoseFrame? = null

    fun smooth(current: PoseFrame): SmoothedPoseFrame {
        val previous = lastFrame
        val joints = if (previous == null) {
            current.joints
        } else {
            current.joints.map { joint ->
                val old = previous.joints.firstOrNull { it.name == joint.name }
                if (old == null) joint else JointPoint(
                    name = joint.name,
                    x = alpha * joint.x + (1f - alpha) * old.x,
                    y = alpha * joint.y + (1f - alpha) * old.y,
                    z = alpha * joint.z + (1f - alpha) * old.z,
                    visibility = alpha * joint.visibility + (1f - alpha) * old.visibility,
                )
            }
        }
        return SmoothedPoseFrame(current.timestampMs, joints, current.confidence).also { lastFrame = it }
    }
}
