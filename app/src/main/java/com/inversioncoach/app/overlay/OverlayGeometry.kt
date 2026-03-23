package com.inversioncoach.app.overlay

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode

private const val MIN_RENDER_VISIBILITY = 0.35f
private val SELECTED_JOINTS = listOf("nose", "shoulder", "elbow", "wrist", "hip", "knee", "ankle")

private val SIDE_CONNECTIONS = listOf(
    "nose" to "{side}_shoulder",
    "{side}_shoulder" to "{side}_elbow",
    "{side}_elbow" to "{side}_wrist",
    "{side}_shoulder" to "{side}_hip",
    "{side}_hip" to "{side}_knee",
    "{side}_knee" to "{side}_ankle",
)

private val BILATERAL_CONNECTORS = listOf(
    "left_shoulder" to "right_shoulder",
    "left_hip" to "right_hip",
)

data class OverlayRenderModel(
    val joints: List<JointPoint>,
    val connections: List<Pair<String, String>>,
    val idealLine: Pair<JointPoint, JointPoint>,
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
        freestyleViewMode: FreestyleViewMode? = null,
    ): OverlayRenderModel {
        val mode = freestyleViewMode ?: freestyleClassifier.classify(joints)
        return if (sessionMode == SessionMode.FREESTYLE) {
            freestyleStrategy.build(joints, mode)
        } else {
            val base = drillStrategy.build(joints, drillCameraSide)
            base.copy(idealLine = buildSupportLine(idealLineXForDrill(drillType, joints)))
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
    fun build(joints: List<JointPoint>, viewMode: FreestyleViewMode): OverlayRenderModel {
        val visible = joints.filter { it.visibility >= MIN_RENDER_VISIBILITY }.associateBy { it.name }
        val keptNames = buildSet {
            add("nose")
            when (viewMode) {
                FreestyleViewMode.FRONT,
                FreestyleViewMode.BACK,
                FreestyleViewMode.UNKNOWN,
                -> {
                    SELECTED_JOINTS.filter { it != "nose" }.forEach { base ->
                        add("left_$base")
                        add("right_$base")
                    }
                }

                FreestyleViewMode.LEFT_PROFILE -> SELECTED_JOINTS.filter { it != "nose" }
                    .forEach { base -> add("left_$base") }

                FreestyleViewMode.RIGHT_PROFILE -> SELECTED_JOINTS.filter { it != "nose" }
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
                    addAll(sideConnections("left"))
                    addAll(sideConnections("right"))
                    addAll(BILATERAL_CONNECTORS)
                }

                FreestyleViewMode.LEFT_PROFILE -> addAll(sideConnections("left"))
                FreestyleViewMode.RIGHT_PROFILE -> addAll(sideConnections("right"))
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

        return OverlayRenderModel(filtered, connections, idealLine = buildSupportLine(idealLineX))
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
        val names = listOf("nose") + SELECTED_JOINTS.filter { it != "nose" }.map { "${sidePrefix}_$it" }
        val filtered = names.mapNotNull { visible[it] }
        val connections = sideConnections(sidePrefix).filter { (from, to) -> visible[from] != null && visible[to] != null }
        return OverlayRenderModel(filtered, connections, idealLine = buildSupportLine(0.5f))
    }
}

private fun sideConnections(side: String): List<Pair<String, String>> =
    SIDE_CONNECTIONS.map { (from, to) ->
        from.replace("{side}", side) to to.replace("{side}", side)
    }


private fun buildSupportLine(x: Float): Pair<JointPoint, JointPoint> {
    val clampedX = x.coerceIn(0.1f, 0.9f)
    return JointPoint(name = "support_line_top", x = clampedX, y = 0f, z = 0f, visibility = 1f) to
        JointPoint(name = "support_line_bottom", x = clampedX, y = 1f, z = 0f, visibility = 1f)
}
