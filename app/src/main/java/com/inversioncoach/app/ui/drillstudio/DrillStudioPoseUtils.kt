package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.drills.catalog.JointPoint
import kotlin.math.sqrt

object DrillStudioPoseUtils {
    private val canonicalAliases = mapOf(
        "left_shoulder" to "shoulder_left",
        "right_shoulder" to "shoulder_right",
        "left_hip" to "hip_left",
        "right_hip" to "hip_right",
        "left_ankle" to "ankle_left",
        "right_ankle" to "ankle_right",
        "left_wrist" to "wrist_left",
        "right_wrist" to "wrist_right",
        "nose" to "head",
    )

    val mirrorPairs = listOf(
        "shoulder_left" to "shoulder_right",
        "wrist_left" to "wrist_right",
        "hip_left" to "hip_right",
        "ankle_left" to "ankle_right",
    )

    val connectedPairs = listOf(
        "head" to "shoulder_left",
        "head" to "shoulder_right",
        "shoulder_left" to "wrist_left",
        "shoulder_right" to "wrist_right",
        "shoulder_left" to "hip_left",
        "shoulder_right" to "hip_right",
        "hip_left" to "ankle_left",
        "hip_right" to "ankle_right",
    )

    fun normalizeJointNames(joints: Map<String, JointPoint>): Map<String, JointPoint> {
        return joints.entries
            .associate { (name, point) -> (canonicalAliases[name] ?: name) to point }
    }

    fun mirrorWithSemanticSwap(joints: Map<String, JointPoint>): Map<String, JointPoint> {
        val normalized = normalizeJointNames(joints).toMutableMap()
        mirrorPairs.forEach { (left, right) ->
            val leftPoint = normalized[left]
            val rightPoint = normalized[right]
            if (leftPoint != null || rightPoint != null) {
                if (rightPoint != null) normalized[left] = JointPoint(1f - rightPoint.x, rightPoint.y)
                if (leftPoint != null) normalized[right] = JointPoint(1f - leftPoint.x, leftPoint.y)
            }
        }
        return normalized.mapValues { (joint, point) ->
            if (mirrorPairs.any { it.first == joint || it.second == joint }) point else JointPoint(1f - point.x, point.y)
        }
    }

    fun nearestJointWithinRadius(
        joints: Map<String, JointPoint>,
        touch: JointPoint,
        hitRadius: Float,
    ): String? {
        val nearest = joints.minByOrNull { (_, p) -> distance(p, touch) } ?: return null
        return if (distance(nearest.value, touch) <= hitRadius) nearest.key else null
    }

    fun applyAnatomicalGuardrails(
        pose: Map<String, JointPoint>,
        joint: String,
        target: JointPoint,
        bodyProfile: UserBodyProfile?,
    ): JointPoint {
        val normalized = normalizeJointNames(pose)
        val clampedTarget = JointPoint(target.x.coerceIn(0.05f, 0.95f), target.y.coerceIn(0.05f, 0.95f))
        val neighbors = connectedPairs.filter { it.first == joint || it.second == joint }.map { if (it.first == joint) it.second else it.first }
        if (neighbors.isEmpty()) return clampedTarget

        val torsoLen = bodyProfile?.torsoLengthNormalized ?: distance(
            normalized["shoulder_left"] ?: JointPoint(0.4f, 0.35f),
            normalized["hip_left"] ?: JointPoint(0.45f, 0.55f),
        )
        val maxMove = (torsoLen * 1.3f).coerceIn(0.08f, 0.35f)
        var constrained = clampedTarget

        neighbors.forEach { neighbor ->
            val neighborPoint = normalized[neighbor] ?: return@forEach
            val referenceLength = desiredLengthFor(joint, neighbor, normalized, bodyProfile)
            val minLen = referenceLength * 0.65f
            val maxLen = referenceLength * 1.35f
            val dx = constrained.x - neighborPoint.x
            val dy = constrained.y - neighborPoint.y
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
            val clampedLen = len.coerceIn(minLen, maxLen.coerceAtMost(maxMove))
            constrained = JointPoint(
                x = (neighborPoint.x + (dx / len) * clampedLen).coerceIn(0.05f, 0.95f),
                y = (neighborPoint.y + (dy / len) * clampedLen).coerceIn(0.05f, 0.95f),
            )
        }

        return keepTorsoCoherent(normalized, joint, constrained, bodyProfile)
    }

    private fun keepTorsoCoherent(
        pose: Map<String, JointPoint>,
        joint: String,
        point: JointPoint,
        bodyProfile: UserBodyProfile?,
    ): JointPoint {
        if (joint !in setOf("shoulder_left", "shoulder_right", "hip_left", "hip_right")) return point
        val shoulderWidth = bodyProfile?.shoulderWidthNormalized
            ?: distance(pose["shoulder_left"] ?: point, pose["shoulder_right"] ?: point).coerceAtLeast(0.08f)
        val hipWidth = bodyProfile?.hipWidthNormalized
            ?: distance(pose["hip_left"] ?: point, pose["hip_right"] ?: point).coerceAtLeast(0.08f)
        val ratio = (shoulderWidth / hipWidth).coerceIn(0.65f, 1.65f)
        val minX = if (joint.endsWith("left")) 0.05f else 0.35f
        val maxX = if (joint.endsWith("left")) 0.65f else 0.95f
        return point.copy(
            x = point.x.coerceIn(minX, maxX),
            y = point.y.coerceIn(0.05f, (0.85f / ratio).coerceIn(0.5f, 0.9f)),
        )
    }

    private fun desiredLengthFor(
        a: String,
        b: String,
        pose: Map<String, JointPoint>,
        bodyProfile: UserBodyProfile?,
    ): Float {
        val normalizedPair = setOf(a, b)
        bodyProfile?.let { profile ->
            return when (normalizedPair) {
                setOf("shoulder_left", "shoulder_right") -> profile.shoulderWidthNormalized
                setOf("hip_left", "hip_right") -> profile.hipWidthNormalized
                setOf("shoulder_left", "hip_left"), setOf("shoulder_right", "hip_right") -> profile.torsoLengthNormalized
                setOf("shoulder_left", "wrist_left"), setOf("shoulder_right", "wrist_right") ->
                    (profile.upperArmLengthNormalized + profile.forearmLengthNormalized)
                setOf("hip_left", "ankle_left"), setOf("hip_right", "ankle_right") ->
                    (profile.femurLengthNormalized + profile.shinLengthNormalized)
                else -> distance(pose[a] ?: JointPoint(0.5f, 0.5f), pose[b] ?: JointPoint(0.5f, 0.5f))
            }
        }
        return distance(pose[a] ?: JointPoint(0.5f, 0.5f), pose[b] ?: JointPoint(0.5f, 0.5f)).coerceAtLeast(0.08f)
    }

    private fun distance(a: JointPoint, b: JointPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
