package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.DrillType

data class MetricWeight(val key: String, val weight: Int)

data class DrillModeConfig(
    val type: DrillType,
    val label: String,
    val sideViewPrimary: Boolean = true,
    val metrics: List<MetricWeight>,
    val faults: Map<String, String>,
    val cuePriority: List<String>,
)

object DrillConfigs {
    private val defaultMetrics = listOf(
        MetricWeight("line_quality", 30),
        MetricWeight("depth", 20),
        MetricWeight("lockout", 20),
        MetricWeight("tempo_control", 15),
        MetricWeight("symmetry", 15),
    )

    private fun cfg(
        type: DrillType,
        label: String,
        cuePriority: List<String>,
        faults: Map<String, String>,
        metrics: List<MetricWeight> = defaultMetrics,
    ) = DrillModeConfig(type = type, label = label, metrics = metrics, faults = faults, cuePriority = cuePriority)

    val all = listOf(
        cfg(
            DrillType.FREESTANDING_HANDSTAND_FUTURE,
            "Free Standing Handstand",
            cuePriority = listOf("line_quality", "scapular_elevation", "rib_pelvis_control"),
            faults = mapOf(
                "line_quality" to "Stack deviation",
                "scapular_elevation" to "Passive shoulders",
                "rib_pelvis_control" to "Rib flare / arch",
                "head_path" to "Head not neutral",
                "stillness" to "Hold instability",
            ),
            metrics = listOf(
                MetricWeight("line_quality", 35),
                MetricWeight("scapular_elevation", 25),
                MetricWeight("rib_pelvis_control", 20),
                MetricWeight("head_path", 10),
                MetricWeight("stillness", 10),
            ),
        ),
        cfg(
            DrillType.CHEST_TO_WALL_HANDSTAND,
            "Wall Assisted Handstand",
            cuePriority = listOf("line_quality", "scapular_elevation", "wall_reliance"),
            faults = mapOf(
                "line_quality" to "Banana line",
                "scapular_elevation" to "Shoulders not active",
                "wall_reliance" to "Excessive wall pressure",
                "rib_pelvis_control" to "Rib flare",
                "head_path" to "Head poking forward",
            ),
        ),
        cfg(
            DrillType.PIKE_PUSH_UP,
            "Pike Push-Up",
            cuePriority = listOf("loading_angle", "depth", "pressing_path"),
            faults = mapOf(
                "loading_angle" to "Hips too low",
                "depth" to "Insufficient depth",
                "pressing_path" to "Press path drifts",
                "lockout" to "Incomplete lockout",
                "tempo_control" to "Rushed reps",
            ),
        ),
        cfg(
            DrillType.ELEVATED_PIKE_PUSH_UP,
            "Elevated Pike Push-Up",
            cuePriority = listOf("loading_angle", "depth", "pressing_path"),
            faults = mapOf(
                "loading_angle" to "Shoulder loading angle off",
                "depth" to "Insufficient depth",
                "pressing_path" to "Path inconsistency",
                "lockout" to "Incomplete lockout",
                "tempo_control" to "Tempo unstable",
            ),
        ),
        cfg(
            DrillType.PUSH_UP,
            "Free Standing Handstand Push-Up",
            cuePriority = listOf("line_quality", "depth", "lockout"),
            faults = mapOf(
                "line_quality" to "Line breaks during rep",
                "depth" to "Insufficient depth",
                "lockout" to "Top lockout missing",
                "path_consistency" to "Bar path drift",
                "tempo_control" to "Rushed reps",
            ),
        ),
        cfg(
            DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP,
            "Wall Assisted Handstand Push-Up",
            cuePriority = listOf("descent_control", "line_retention", "path_consistency"),
            faults = mapOf(
                "descent_control" to "Dropping too fast",
                "line_retention" to "Line breaks mid rep",
                "path_consistency" to "Path inconsistent",
                "top_position" to "Weak lockout",
                "bottom_position" to "Collapsed bottom",
            ),
        ),
    )

    fun byType(type: DrillType): DrillModeConfig = all.firstOrNull { it.type == type } ?: all.first()
}
