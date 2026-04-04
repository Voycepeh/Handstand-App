package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.overlay.OverlaySkeletonSpec
import kotlin.math.sqrt

object DrillStudioPoseUtils {
    private val canonicalAliases = mapOf(
        "head" to "nose",
        "shoulder_left" to "left_shoulder",
        "shoulder_right" to "right_shoulder",
        "elbow_left" to "left_elbow",
        "elbow_right" to "right_elbow",
        "wrist_left" to "left_wrist",
        "wrist_right" to "right_wrist",
        "hip_left" to "left_hip",
        "hip_right" to "right_hip",
        "knee_left" to "left_knee",
        "knee_right" to "right_knee",
        "ankle_left" to "left_ankle",
        "ankle_right" to "right_ankle",
    )

    val mirrorPairs = listOf(
        "left_shoulder" to "right_shoulder",
        "left_elbow" to "right_elbow",
        "left_wrist" to "right_wrist",
        "left_hip" to "right_hip",
        "left_knee" to "right_knee",
        "left_ankle" to "right_ankle",
    )

    val connectedPairs = OverlaySkeletonSpec.sideConnections("left") +
        OverlaySkeletonSpec.sideConnections("right") +
        OverlaySkeletonSpec.bilateralConnectors

    fun normalizeJointNames(joints: Map<String, JointPoint>): Map<String, JointPoint> {
        val normalized = joints.entries
            .associate { (name, point) -> (canonicalAliases[name] ?: name) to point }
            .toMutableMap()

        inferIfMissing(normalized, "left_elbow", "left_shoulder", "left_wrist")
        inferIfMissing(normalized, "right_elbow", "right_shoulder", "right_wrist")
        inferIfMissing(normalized, "left_knee", "left_hip", "left_ankle")
        inferIfMissing(normalized, "right_knee", "right_hip", "right_ankle")

        return OverlaySkeletonSpec.canonicalJointOrder
            .mapNotNull { joint -> normalized[joint]?.let { joint to it } }
            .toMap()
    }

    fun renderPoseWithFallback(
        joints: Map<String, JointPoint>,
        fallback: Map<String, JointPoint>,
    ): Map<String, JointPoint> {
        val normalizedFallback = normalizeJointNames(fallback)
        val normalizedJoints = normalizeJointNames(joints)
        return normalizeJointNames(normalizedFallback + normalizedJoints)
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
            normalized["left_shoulder"] ?: JointPoint(0.4f, 0.35f),
            normalized["left_hip"] ?: JointPoint(0.45f, 0.55f),
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
        if (joint !in setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip")) return point
        val shoulderWidth = bodyProfile?.shoulderWidthNormalized
            ?: distance(pose["left_shoulder"] ?: point, pose["right_shoulder"] ?: point).coerceAtLeast(0.08f)
        val hipWidth = bodyProfile?.hipWidthNormalized
            ?: distance(pose["left_hip"] ?: point, pose["right_hip"] ?: point).coerceAtLeast(0.08f)
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
                setOf("left_shoulder", "right_shoulder") -> profile.shoulderWidthNormalized
                setOf("left_hip", "right_hip") -> profile.hipWidthNormalized
                setOf("left_shoulder", "left_hip"), setOf("right_shoulder", "right_hip") -> profile.torsoLengthNormalized
                setOf("left_shoulder", "left_elbow"), setOf("right_shoulder", "right_elbow") ->
                    profile.upperArmLengthNormalized
                setOf("left_elbow", "left_wrist"), setOf("right_elbow", "right_wrist") ->
                    profile.forearmLengthNormalized
                setOf("left_hip", "left_knee"), setOf("right_hip", "right_knee") ->
                    profile.femurLengthNormalized
                setOf("left_knee", "left_ankle"), setOf("right_knee", "right_ankle") ->
                    profile.shinLengthNormalized
                setOf("left_shoulder", "left_wrist"), setOf("right_shoulder", "right_wrist") ->
                    (profile.upperArmLengthNormalized + profile.forearmLengthNormalized)
                setOf("left_hip", "left_ankle"), setOf("right_hip", "right_ankle") ->
                    (profile.femurLengthNormalized + profile.shinLengthNormalized)
                else -> distance(pose[a] ?: JointPoint(0.5f, 0.5f), pose[b] ?: JointPoint(0.5f, 0.5f))
            }
        }
        return distance(pose[a] ?: JointPoint(0.5f, 0.5f), pose[b] ?: JointPoint(0.5f, 0.5f)).coerceAtLeast(0.08f)
    }

    private fun inferIfMissing(
        joints: MutableMap<String, JointPoint>,
        target: String,
        start: String,
        end: String,
    ) {
        if (joints[target] != null) return
        val a = joints[start] ?: return
        val b = joints[end] ?: return
        joints[target] = JointPoint((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
    }

    private fun distance(a: JointPoint, b: JointPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
