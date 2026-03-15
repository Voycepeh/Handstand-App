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
    val calibration: DrillCalibrationProfile,
    val experimental: Boolean = false,
)

object DrillConfigs {
    private val defaultMetrics = listOf(
        MetricWeight("line_quality", 30),
        MetricWeight("bottom_depth_quality", 20),
        MetricWeight("top_lockout_quality", 20),
        MetricWeight("descent_quality", 15),
        MetricWeight("flare_stability_quality", 15),
    )

    private fun cfg(
        type: DrillType,
        label: String,
        cuePriority: List<String>,
        faults: Map<String, String>,
        metrics: List<MetricWeight> = defaultMetrics,
        experimental: Boolean = false,
    ) = DrillModeConfig(
        type = type,
        label = label,
        metrics = metrics,
        faults = faults,
        cuePriority = cuePriority,
        calibration = DrillProfiles.forDrill(type, metrics),
        experimental = experimental,
    )

    val all = listOf(
        cfg(
            DrillType.FREESTYLE,
            "Freestyle Live Coaching",
            cuePriority = emptyList(),
            faults = emptyMap(),
            metrics = listOf(
                MetricWeight("line_quality", 100),
            ),
            experimental = false,
        ),
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
                MetricWeight("line_quality", 25),
                MetricWeight("scapular_elevation", 20),
                MetricWeight("rib_pelvis_control", 20),
                MetricWeight("shoulder_openness", 15),
                MetricWeight("leg_tension", 10),
                MetricWeight("elbow_lock", 10),
            ),
            experimental = true,
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
            metrics = listOf(
                MetricWeight("line_quality", 25),
                MetricWeight("scapular_elevation", 20),
                MetricWeight("rib_pelvis_control", 20),
                MetricWeight("shoulder_openness", 15),
                MetricWeight("leg_tension", 10),
                MetricWeight("elbow_lock", 10),
            ),
        ),
        cfg(
            DrillType.PIKE_PUSH_UP,
            "Pike Push-Up",
            cuePriority = listOf("loading_angle", "bottom_depth_quality", "top_lockout_quality"),
            faults = mapOf(
                "loading_angle" to "Hips too low",
                "bottom_depth_quality" to "Insufficient depth",
                "pressing_path" to "Press path drifts",
                "top_lockout_quality" to "Incomplete lockout",
                "descent_quality" to "Rushed reps",
            ),
            metrics = listOf(
                MetricWeight("loading_angle", 20),
                MetricWeight("bottom_depth_quality", 20),
                MetricWeight("top_lockout_quality", 20),
                MetricWeight("descent_quality", 15),
                MetricWeight("ascent_quality", 10),
                MetricWeight("flare_stability_quality", 15),
            ),
        ),
        cfg(
            DrillType.ELEVATED_PIKE_PUSH_UP,
            "Elevated Pike Push-Up",
            cuePriority = listOf("loading_angle", "bottom_depth_quality", "top_lockout_quality"),
            faults = mapOf(
                "loading_angle" to "Shoulder loading angle off",
                "bottom_depth_quality" to "Insufficient depth",
                "pressing_path" to "Path inconsistency",
                "top_lockout_quality" to "Incomplete lockout",
                "descent_quality" to "Tempo unstable",
            ),
            metrics = listOf(
                MetricWeight("loading_angle", 20),
                MetricWeight("bottom_depth_quality", 20),
                MetricWeight("top_lockout_quality", 20),
                MetricWeight("descent_quality", 15),
                MetricWeight("ascent_quality", 10),
                MetricWeight("flare_stability_quality", 15),
            ),
        ),
        cfg(
            DrillType.PUSH_UP,
            "Free Standing Handstand Push-Up",
            cuePriority = listOf("torso_line", "bottom_depth_quality", "top_lockout_quality"),
            faults = mapOf(
                "torso_line" to "Line breaks during rep",
                "bottom_depth_quality" to "Insufficient depth",
                "top_lockout_quality" to "Top lockout missing",
                "flare_stability_quality" to "Elbow flare or instability",
                "descent_quality" to "Rushed reps",
            ),
            metrics = listOf(
                MetricWeight("torso_line", 20),
                MetricWeight("bottom_depth_quality", 25),
                MetricWeight("top_lockout_quality", 20),
                MetricWeight("descent_quality", 15),
                MetricWeight("ascent_quality", 10),
                MetricWeight("flare_stability_quality", 10),
            ),
        ),
        cfg(
            DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP,
            "Wall Assisted Handstand Push-Up",
            cuePriority = listOf("descent_quality", "line_retention", "path_consistency"),
            faults = mapOf(
                "descent_quality" to "Dropping too fast",
                "line_retention" to "Line breaks mid rep",
                "path_consistency" to "Path inconsistent",
                "top_lockout_quality" to "Weak lockout",
                "bottom_depth_quality" to "Collapsed bottom",
            ),
            metrics = listOf(
                MetricWeight("line_retention", 20),
                MetricWeight("path_consistency", 20),
                MetricWeight("bottom_depth_quality", 20),
                MetricWeight("top_lockout_quality", 15),
                MetricWeight("descent_quality", 15),
                MetricWeight("flare_stability_quality", 10),
            ),
        ),
    )

    val supportedTypes: Set<DrillType> = all.map { it.type }.toSet()

    fun byTypeOrNull(type: DrillType): DrillModeConfig? = all.firstOrNull { it.type == type }

    fun requireByType(type: DrillType): DrillModeConfig =
        byTypeOrNull(type) ?: error("Unsupported drill for biomechanics engine: $type")
}
