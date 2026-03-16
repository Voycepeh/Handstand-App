package com.inversioncoach.app.overlay

import androidx.compose.ui.graphics.Color
import com.inversioncoach.app.model.JointPoint

data class OverlayJointStyle(
    val color: Color,
    val radius: Float,
)

data class OverlayRenderModel(
    val joints: List<JointPoint>,
    val connections: List<Pair<String, String>>,
)

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

class FreestyleOverlayStrategy {
    fun build(joints: List<JointPoint>, viewMode: FreestyleViewMode): OverlayRenderModel {
        val visible = joints.filter { it.visibility >= MIN_RENDER_VISIBILITY }.associateBy { it.name }
        val keptNames = buildSet {
            add("nose")
            when (viewMode) {
                FreestyleViewMode.BILATERAL_VIEW -> {
                    SELECTED_JOINTS.filter { it != "nose" }.forEach { base ->
                        add("left_$base")
                        add("right_$base")
                    }
                }
                FreestyleViewMode.LEFT_SIDE_VIEW -> SELECTED_JOINTS.filter { it != "nose" }.forEach { base -> add("left_$base") }
                FreestyleViewMode.RIGHT_SIDE_VIEW -> SELECTED_JOINTS.filter { it != "nose" }.forEach { base -> add("right_$base") }
            }
        }

        val filtered = keptNames.mapNotNull { visible[it] }
        val connections = buildList {
            when (viewMode) {
                FreestyleViewMode.BILATERAL_VIEW -> {
                    addAll(sideConnections("left"))
                    addAll(sideConnections("right"))
                    addAll(BILATERAL_CONNECTORS)
                }
                FreestyleViewMode.LEFT_SIDE_VIEW -> addAll(sideConnections("left"))
                FreestyleViewMode.RIGHT_SIDE_VIEW -> addAll(sideConnections("right"))
            }
        }.filter { (from, to) -> visible[from] != null && visible[to] != null }

        return OverlayRenderModel(filtered, connections)
    }
}

class FixedDrillSideOverlayStrategy {
    fun build(joints: List<JointPoint>, side: DrillCameraSide): OverlayRenderModel {
        val sidePrefix = if (side == DrillCameraSide.LEFT) "left" else "right"
        val visible = joints.filter { it.visibility >= MIN_RENDER_VISIBILITY }.associateBy { it.name }
        val names = listOf("nose") + SELECTED_JOINTS.filter { it != "nose" }.map { "${sidePrefix}_$it" }
        val filtered = names.mapNotNull { visible[it] }
        val connections = sideConnections(sidePrefix).filter { (from, to) -> visible[from] != null && visible[to] != null }
        return OverlayRenderModel(filtered, connections)
    }
}

fun jointStyle(jointName: String, baseColor: Color, baseRadius: Float): OverlayJointStyle {
    val largeRadius = baseRadius * 2f
    return when {
        jointName == "nose" -> OverlayJointStyle(Color.Green, largeRadius)
        jointName.endsWith("_hip") -> OverlayJointStyle(Color.Red, largeRadius)
        else -> OverlayJointStyle(baseColor, baseRadius)
    }
}

private fun sideConnections(side: String): List<Pair<String, String>> =
    SIDE_CONNECTIONS.map { (from, to) ->
        from.replace("{side}", side) to to.replace("{side}", side)
    }
