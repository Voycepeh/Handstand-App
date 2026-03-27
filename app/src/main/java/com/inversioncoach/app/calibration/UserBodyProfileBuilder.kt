package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.PoseFrame
import kotlin.math.abs
import kotlin.math.sqrt

class UserBodyProfileBuilder {

    fun build(
        frontFrames: List<PoseFrame>,
        sideFrames: List<PoseFrame>,
        overheadFrames: List<PoseFrame>,
        holdFrames: List<PoseFrame>,
    ): UserBodyProfile {
        val all = frontFrames + sideFrames + overheadFrames + holdFrames

        fun avg(values: List<Float>): Float =
            if (values.isEmpty()) 0f else values.average().toFloat()

        val shoulderWidth = avg(frontFrames.mapNotNull { normalizedDistance(it, "left_shoulder", "right_shoulder") })
        val hipWidth = avg(frontFrames.mapNotNull { normalizedDistance(it, "left_hip", "right_hip") })
        val torsoLength = avg(sideFrames.mapNotNull { torsoLength(it) })
        val upperArm = avg(
            overheadFrames.mapNotNull { segmentLength(it, "left_shoulder", "left_elbow") } +
                overheadFrames.mapNotNull { segmentLength(it, "right_shoulder", "right_elbow") },
        )
        val forearm = avg(
            overheadFrames.mapNotNull { segmentLength(it, "left_elbow", "left_wrist") } +
                overheadFrames.mapNotNull { segmentLength(it, "right_elbow", "right_wrist") },
        )
        val femur = avg(
            all.mapNotNull { segmentLength(it, "left_hip", "left_knee") } +
                all.mapNotNull { segmentLength(it, "right_hip", "right_knee") },
        )
        val shin = avg(
            all.mapNotNull { segmentLength(it, "left_knee", "left_ankle") } +
                all.mapNotNull { segmentLength(it, "right_knee", "right_ankle") },
        )

        val leftRightConsistency = avg(all.mapNotNull { bilateralConsistency(it) })

        val anchor = avg(
            listOf(
                shoulderWidth,
                hipWidth,
                torsoLength,
                femur,
            ).filter { it > 0f },
        ).takeIf { it > 0.0001f } ?: 1f

        return UserBodyProfile(
            version = 1,
            shoulderWidthNormalized = shoulderWidth / anchor,
            hipWidthNormalized = hipWidth / anchor,
            torsoLengthNormalized = torsoLength / anchor,
            upperArmLengthNormalized = upperArm / anchor,
            forearmLengthNormalized = forearm / anchor,
            femurLengthNormalized = femur / anchor,
            shinLengthNormalized = shin / anchor,
            leftRightConsistency = leftRightConsistency,
        )
    }

    private fun normalizedDistance(frame: PoseFrame, a: String, b: String): Float? {
        val p1 = frame.joints.firstOrNull { it.name == a } ?: return null
        val p2 = frame.joints.firstOrNull { it.name == b } ?: return null
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun segmentLength(frame: PoseFrame, a: String, b: String): Float? =
        normalizedDistance(frame, a, b)

    private fun torsoLength(frame: PoseFrame): Float? {
        val left = normalizedDistance(frame, "left_shoulder", "left_hip")
        val right = normalizedDistance(frame, "right_shoulder", "right_hip")
        return listOfNotNull(left, right).takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }

    private fun bilateralConsistency(frame: PoseFrame): Float? {
        val upperLeft = segmentLength(frame, "left_shoulder", "left_elbow") ?: return null
        val upperRight = segmentLength(frame, "right_shoulder", "right_elbow") ?: return null
        val denom = maxOf(upperLeft, upperRight, 0.0001f)
        return (1f - abs(upperLeft - upperRight) / denom).coerceIn(0f, 1f)
    }
}
