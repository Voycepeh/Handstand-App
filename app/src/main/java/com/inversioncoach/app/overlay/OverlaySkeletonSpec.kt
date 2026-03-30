package com.inversioncoach.app.overlay

object OverlaySkeletonSpec {
    const val Nose = "nose"

    val baseJoints = listOf("shoulder", "elbow", "wrist", "hip", "knee", "ankle")

    val bilateralConnectors: List<Pair<String, String>> = listOf(
        "left_shoulder" to "right_shoulder",
        "left_hip" to "right_hip",
    )

    val sideConnectionsTemplate: List<Pair<String, String>> = listOf(
        Nose to "{side}_shoulder",
        "{side}_shoulder" to "{side}_elbow",
        "{side}_elbow" to "{side}_wrist",
        "{side}_shoulder" to "{side}_hip",
        "{side}_hip" to "{side}_knee",
        "{side}_knee" to "{side}_ankle",
    )

    val canonicalJointOrder: List<String> = buildList {
        add(Nose)
        addAll(baseJoints.map { "left_$it" })
        addAll(baseJoints.map { "right_$it" })
    }

    fun sideConnections(side: String): List<Pair<String, String>> =
        sideConnectionsTemplate.map { (from, to) ->
            from.replace("{side}", side) to to.replace("{side}", side)
        }
}
