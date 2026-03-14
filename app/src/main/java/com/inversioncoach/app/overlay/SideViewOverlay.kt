package com.inversioncoach.app.overlay

import com.inversioncoach.app.model.JointPoint

private const val MIN_RENDER_VISIBILITY = 0.35f
private const val SIDE_SWITCH_ADVANTAGE = 0.75f
private const val SIDE_SWITCH_FRAMES = 6

enum class OverlayMode {
    NORMAL_SIDE_VIEW,
    DEBUG_SIDE_VIEW,
    DEBUG_ALL_LANDMARKS,
}

enum class TrackedSide {
    LEFT,
    RIGHT,
}

enum class SideJoint(val baseName: String, val label: String) {
    SHOULDER("shoulder", "SH"),
    ELBOW("elbow", "EL"),
    WRIST("wrist", "WR"),
    HIP("hip", "HIP"),
    KNEE("knee", "KN"),
    ANKLE("ankle", "AN"),
    HEEL("heel", ""),
    FOOT_INDEX("foot_index", ""),
}

data class SideSelectionState(
    val trackedSide: TrackedSide = TrackedSide.LEFT,
    val leftScore: Float = 0f,
    val rightScore: Float = 0f,
    val switchStreak: Int = 0,
)

class TrackedSideSelector {
    private var state = SideSelectionState()

    fun determineTrackedSide(fullLandmarks: List<JointPoint>): SideSelectionState {
        val lookup = fullLandmarks.associateBy { it.name }
        val leftScore = sideScore(TrackedSide.LEFT, lookup)
        val rightScore = sideScore(TrackedSide.RIGHT, lookup)
        val leading = if (leftScore >= rightScore) TrackedSide.LEFT else TrackedSide.RIGHT
        val trailing = if (leading == TrackedSide.LEFT) rightScore else leftScore
        val leaderScore = if (leading == TrackedSide.LEFT) leftScore else rightScore
        val shouldSwitch =
            leading != state.trackedSide &&
                leaderScore - trailing >= SIDE_SWITCH_ADVANTAGE

        state = if (shouldSwitch) {
            val nextStreak = state.switchStreak + 1
            if (nextStreak >= SIDE_SWITCH_FRAMES) {
                SideSelectionState(
                    trackedSide = leading,
                    leftScore = leftScore,
                    rightScore = rightScore,
                    switchStreak = 0,
                )
            } else {
                state.copy(
                    leftScore = leftScore,
                    rightScore = rightScore,
                    switchStreak = nextStreak,
                )
            }
        } else {
            state.copy(
                leftScore = leftScore,
                rightScore = rightScore,
                switchStreak = 0,
            )
        }
        return state
    }

    private fun sideScore(side: TrackedSide, lookup: Map<String, JointPoint>): Float {
        val prefix = side.name.lowercase()
        return listOf("shoulder", "elbow", "wrist", "hip", "knee", "ankle")
            .sumOf { (lookup["${prefix}_$it"]?.visibility ?: 0f).toDouble() }
            .toFloat()
    }
}

fun getRenderableLandmarks(fullLandmarks: List<JointPoint>, trackedSide: TrackedSide): List<JointPoint> {
    val names = buildList {
        add("nose")
        SideJoint.entries.forEach { joint ->
            add("${trackedSide.name.lowercase()}_${joint.baseName}")
        }
    }
    val byName = fullLandmarks.associateBy { it.name }
    return names.mapNotNull { byName[it] }.filter { it.visibility >= MIN_RENDER_VISIBILITY }
}

fun getRenderableConnections(trackedSide: TrackedSide): List<Pair<String, String>> {
    val prefix = trackedSide.name.lowercase()
    return listOf(
        "${prefix}_shoulder" to "${prefix}_elbow",
        "${prefix}_elbow" to "${prefix}_wrist",
        "${prefix}_shoulder" to "${prefix}_hip",
        "${prefix}_hip" to "${prefix}_knee",
        "${prefix}_knee" to "${prefix}_ankle",
        "${prefix}_ankle" to "${prefix}_heel",
        "${prefix}_ankle" to "${prefix}_foot_index",
    )
}

fun TrackedSide.opposite(): TrackedSide = if (this == TrackedSide.LEFT) TrackedSide.RIGHT else TrackedSide.LEFT
