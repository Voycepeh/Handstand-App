package com.inversioncoach.app.overlay

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode

private const val MIN_RENDER_VISIBILITY = 0.35f

data class OverlayRenderModel(
    val joints: List<JointPoint>,
    val connections: List<Pair<String, String>>,
    val idealLine: Pair<JointPoint, JointPoint>,
    val centerOfGravity: JointPoint? = null,
    val showBalanceLane: Boolean = true,
)

object OverlayGeometry {
    private val freestyleClassifier = FreestyleOrientationClassifier()
    private val freestyleStrategy = FreestyleOverlayStrategy()
    private val drillStrategy = FixedDrillSideOverlayStrategy()

    fun build(
        drillType: DrillType,
        sessionMode: SessionMode,
        joints: List<JointPoint>,
        drillCameraSide: DrillCameraSide,
        effectiveView: EffectiveView = EffectiveView.SIDE,
        freestyleViewMode: FreestyleViewMode? = null,
    ): OverlayRenderModel {
        val mode = freestyleViewMode ?: freestyleClassifier.classify(joints)
        return if (sessionMode == SessionMode.FREESTYLE) {
            freestyleStrategy.build(joints, mode, effectiveView)
        } else {
            val base = drillStrategy.build(joints, drillCameraSide)
            base.copy(
                idealLine = buildSupportLine(idealLineXForDrill(drillType, joints)),
                centerOfGravity = computeCenterOfGravity(joints),
                showBalanceLane = effectiveView == EffectiveView.SIDE,
            )
        }
    }

    private fun idealLineXForDrill(drillType: DrillType, joints: List<JointPoint>): Float {
        val jointPriority = when (drillType) {
            DrillType.FREESTYLE,
            DrillType.WALL_FACING_HANDSTAND_HOLD,
            DrillType.WALL_HANDSTAND,
            DrillType.BACK_TO_WALL_HANDSTAND,
            DrillType.WALL_HANDSTAND_PUSH_UP,
            DrillType.FREE_HANDSTAND,
            DrillType.PIKE_PUSH_UP,
            DrillType.ELEVATED_PIKE_PUSH_UP,
            DrillType.HANDSTAND_PUSH_UP,
            DrillType.WALL_PUSH_UP,
            DrillType.STANDARD_PUSH_UP,
            DrillType.INCLINE_OR_KNEE_PUSH_UP,
            DrillType.FOREARM_PLANK,
            DrillType.PARALLEL_BAR_DIP,
            -> listOf(listOf("left_wrist", "right_wrist"), listOf("left_shoulder", "right_shoulder"))

            DrillType.BODYWEIGHT_SQUAT,
            DrillType.REVERSE_LUNGE,
            DrillType.BURPEE,
            DrillType.STANDING_POSTURE_HOLD,
            -> listOf(listOf("left_ankle", "right_ankle"), listOf("left_hip", "right_hip"))

            DrillType.HANGING_KNEE_RAISE,
            DrillType.PULL_UP_OR_ASSISTED_PULL_UP,
            DrillType.L_SIT_HOLD,
            DrillType.HOLLOW_BODY_HOLD,
            DrillType.GLUTE_BRIDGE,
            DrillType.SIT_UP,
            -> listOf(listOf("left_hip", "right_hip"), listOf("left_shoulder", "right_shoulder"))
        }

        for (jointNames in jointPriority) {
            val xs = jointNames.mapNotNull { name -> joints.firstOrNull { it.name == name }?.x }
            if (xs.isNotEmpty()) return xs.average().toFloat().coerceIn(0.1f, 0.9f)
        }
        return 0.5f
    }
}

private class FreestyleOverlayStrategy {
    fun build(joints: List<JointPoint>, viewMode: FreestyleViewMode, effectiveView: EffectiveView): OverlayRenderModel {
        val visible = joints.filter { it.visibility >= MIN_RENDER_VISIBILITY }.associateBy { it.name }
        val keptNames = buildSet {
            add("nose")
            when (viewMode) {
                FreestyleViewMode.FRONT,
                FreestyleViewMode.BACK,
                FreestyleViewMode.UNKNOWN,
                -> {
                    OverlaySkeletonSpec.baseJoints.forEach { base ->
                        add("left_$base")
                        add("right_$base")
                    }
                }

                FreestyleViewMode.LEFT_PROFILE -> OverlaySkeletonSpec.baseJoints
                    .forEach { base -> add("left_$base") }

                FreestyleViewMode.RIGHT_PROFILE -> OverlaySkeletonSpec.baseJoints
                    .forEach { base -> add("right_$base") }
            }
        }

        val filtered = keptNames.mapNotNull { visible[it] }
        val connections = buildList {
            when (viewMode) {
                FreestyleViewMode.FRONT,
                FreestyleViewMode.BACK,
                FreestyleViewMode.UNKNOWN,
                -> {
                    addAll(OverlaySkeletonSpec.sideConnections("left"))
                    addAll(OverlaySkeletonSpec.sideConnections("right"))
                    addAll(OverlaySkeletonSpec.bilateralConnectors)
                }

                FreestyleViewMode.LEFT_PROFILE -> addAll(OverlaySkeletonSpec.sideConnections("left"))
                FreestyleViewMode.RIGHT_PROFILE -> addAll(OverlaySkeletonSpec.sideConnections("right"))
            }
        }.filter { (from, to) -> visible[from] != null && visible[to] != null }

        val idealLineX = when (viewMode) {
            FreestyleViewMode.LEFT_PROFILE -> profileLineX(filtered, "left")
            FreestyleViewMode.RIGHT_PROFILE -> profileLineX(filtered, "right")
            FreestyleViewMode.FRONT,
            FreestyleViewMode.BACK,
            FreestyleViewMode.UNKNOWN,
            -> 0.5f
        }

        return OverlayRenderModel(
            joints = filtered,
            connections = connections,
            idealLine = buildSupportLine(idealLineX),
            centerOfGravity = computeCenterOfGravity(joints),
            showBalanceLane = effectiveView == EffectiveView.SIDE,
        )
    }

    private fun profileLineX(joints: List<JointPoint>, side: String): Float {
        val candidates = listOf("${side}_wrist", "${side}_shoulder", "${side}_hip")
            .mapNotNull { name -> joints.firstOrNull { it.name == name }?.x }
        return if (candidates.isEmpty()) 0.5f else candidates.average().toFloat().coerceIn(0.1f, 0.9f)
    }
}

private class FixedDrillSideOverlayStrategy {
    fun build(joints: List<JointPoint>, side: DrillCameraSide): OverlayRenderModel {
        val sidePrefix = if (side == DrillCameraSide.LEFT) "left" else "right"
        val visible = joints.filter { it.visibility >= MIN_RENDER_VISIBILITY }.associateBy { it.name }
        val names = listOf(OverlaySkeletonSpec.Nose) + OverlaySkeletonSpec.baseJoints.map { "${sidePrefix}_$it" }
        val filtered = names.mapNotNull { visible[it] }
        val connections = OverlaySkeletonSpec.sideConnections(sidePrefix).filter { (from, to) -> visible[from] != null && visible[to] != null }
        return OverlayRenderModel(
            joints = filtered,
            connections = connections,
            idealLine = buildSupportLine(0.5f),
            centerOfGravity = computeCenterOfGravity(filtered),
            showBalanceLane = true,
        )
    }
}


private fun buildSupportLine(x: Float): Pair<JointPoint, JointPoint> {
    val clampedX = x.coerceIn(0.1f, 0.9f)
    return JointPoint(name = "support_line_top", x = clampedX, y = 0f, z = 0f, visibility = 1f) to
        JointPoint(name = "support_line_bottom", x = clampedX, y = 1f, z = 0f, visibility = 1f)
}

private fun computeCenterOfGravity(joints: List<JointPoint>): JointPoint? {
    val weighted = listOf(
        "left_shoulder" to 0.17f,
        "right_shoulder" to 0.17f,
        "left_hip" to 0.33f,
        "right_hip" to 0.33f,
    ).mapNotNull { (name, weight) ->
        joints.firstOrNull { it.name == name }?.takeIf { it.visibility >= MIN_RENDER_VISIBILITY }?.let { joint -> joint to weight }
    }
    if (weighted.isEmpty()) return null
    val totalWeight = weighted.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val x = weighted.sumOf { (joint, weight) -> (joint.x * weight).toDouble() }.toFloat() / totalWeight
    val y = weighted.sumOf { (joint, weight) -> (joint.y * weight).toDouble() }.toFloat() / totalWeight
    val visibility = weighted.minOf { it.first.visibility }
    return JointPoint(name = "center_of_gravity", x = x, y = y, z = 0f, visibility = visibility)
}
