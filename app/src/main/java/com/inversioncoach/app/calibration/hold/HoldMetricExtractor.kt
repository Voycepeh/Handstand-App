package com.inversioncoach.app.calibration.hold

import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.model.PoseFrame
import kotlin.math.abs
import kotlin.math.sqrt

class HoldMetricExtractor {
    fun extract(frame: PoseFrame, bodyProfile: UserBodyProfile?): HoldMetricSnapshot {
        val joints = frame.joints.associateBy { it.name }

        fun x(name: String): Float? = joints[name]?.x

        val wristShoulderOffset = listOfNotNull(
            x("left_wrist")?.let { wrist -> x("left_shoulder")?.let { abs(wrist - it) } },
            x("right_wrist")?.let { wrist -> x("right_shoulder")?.let { abs(wrist - it) } },
        ).averageOrNull()

        val shoulderHipOffset = listOfNotNull(
            x("left_shoulder")?.let { s -> x("left_hip")?.let { abs(s - it) } },
            x("right_shoulder")?.let { s -> x("right_hip")?.let { abs(s - it) } },
        ).averageOrNull()

        val hipAnkleOffset = listOfNotNull(
            x("left_hip")?.let { h -> x("left_ankle")?.let { abs(h - it) } },
            x("right_hip")?.let { h -> x("right_ankle")?.let { abs(h - it) } },
        ).averageOrNull()

        val torsoDeviation = shoulderHipOffset
        val symmetry = bilateralSymmetry(frame)
        val stabilityScore = (bodyProfile?.leftRightConsistency ?: symmetry)?.coerceIn(0f, 1f)

        return HoldMetricSnapshot(
            values = buildMap {
                wristShoulderOffset?.let { put("wrist_shoulder_offset", it) }
                shoulderHipOffset?.let { put("shoulder_hip_offset", it) }
                hipAnkleOffset?.let { put("hip_ankle_offset", it) }
                torsoDeviation?.let { put("torso_line_deviation", it) }
                symmetry?.let { put("left_right_symmetry", it) }
                stabilityScore?.let { put("stability_score", it) }
            },
        )
    }

    private fun bilateralSymmetry(frame: PoseFrame): Float? {
        val joints = frame.joints.associateBy { it.name }

        fun dist(a: String, b: String): Float? {
            val p1 = joints[a] ?: return null
            val p2 = joints[b] ?: return null
            val dx = p1.x - p2.x
            val dy = p1.y - p2.y
            return sqrt(dx * dx + dy * dy)
        }

        val leftArm = (dist("left_shoulder", "left_elbow") ?: return null) + (dist("left_elbow", "left_wrist") ?: return null)
        val rightArm = (dist("right_shoulder", "right_elbow") ?: return null) + (dist("right_elbow", "right_wrist") ?: return null)
        val denom = maxOf(leftArm, rightArm, 0.0001f)
        return (1f - abs(leftArm - rightArm) / denom).coerceIn(0f, 1f)
    }
}


private fun Iterable<Float>.averageOrNull(): Float? {
    val list = toList()
    return if (list.isEmpty()) null else list.average().toFloat()
}
