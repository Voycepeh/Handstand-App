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
    private val chestToWall = DrillModeConfig(
        type = DrillType.CHEST_TO_WALL_HANDSTAND,
        label = "Chest-to-wall handstand",
        metrics = listOf(
            MetricWeight("line_quality", 30),
            MetricWeight("shoulder_openness", 25),
            MetricWeight("scapular_elevation", 20),
            MetricWeight("rib_pelvis_control", 15),
            MetricWeight("leg_tension", 10),
        ),
        faults = mapOf(
            "line_quality" to "Banana shape",
            "shoulder_openness" to "Shoulders closed",
            "scapular_elevation" to "Passive shoulders",
            "rib_pelvis_control" to "Rib flare",
            "leg_tension" to "Soft knees / loose legs",
        ),
        cuePriority = listOf("scapular_elevation", "rib_pelvis_control", "line_quality"),
    )

    private val backToWall = DrillModeConfig(
        type = DrillType.BACK_TO_WALL_HANDSTAND,
        label = "Back-to-wall handstand",
        metrics = listOf(
            MetricWeight("shoulder_push", 25),
            MetricWeight("reduced_arch", 25),
            MetricWeight("hip_stack", 20),
            MetricWeight("wall_reliance", 20),
            MetricWeight("leg_tension", 10),
        ),
        faults = mapOf(
            "shoulder_push" to "Passive shoulders",
            "reduced_arch" to "Excessive arch",
            "hip_stack" to "Hips off stack",
            "wall_reliance" to "Too much wall pressure",
            "leg_tension" to "Leg line inconsistency",
        ),
        cuePriority = listOf("shoulder_push", "reduced_arch", "wall_reliance"),
    )

    private val pike = DrillModeConfig(
        type = DrillType.PIKE_PUSH_UP,
        label = "Pike push-up",
        metrics = listOf(
            MetricWeight("hip_height", 25),
            MetricWeight("shoulder_loading", 25),
            MetricWeight("head_path", 20),
            MetricWeight("elbow_path", 15),
            MetricWeight("tempo_control", 15),
        ),
        faults = mapOf(
            "hip_height" to "Hips too low",
            "shoulder_loading" to "Not loading shoulders vertically",
            "head_path" to "Head drifting too far forward",
            "elbow_path" to "Elbows flaring",
            "tempo_control" to "Descent too fast",
        ),
        cuePriority = listOf("hip_height", "shoulder_loading", "head_path"),
    )

    private val elevatedPike = DrillModeConfig(
        type = DrillType.ELEVATED_PIKE_PUSH_UP,
        label = "Elevated pike push-up",
        metrics = listOf(
            MetricWeight("loading_angle", 25),
            MetricWeight("depth", 20),
            MetricWeight("pressing_path", 20),
            MetricWeight("lockout", 15),
            MetricWeight("tempo_control", 20),
        ),
        faults = mapOf(
            "loading_angle" to "Shoulder loading angle off",
            "depth" to "Insufficient depth",
            "pressing_path" to "Path inconsistency",
            "lockout" to "Incomplete lockout",
            "tempo_control" to "Tempo unstable",
        ),
        cuePriority = listOf("loading_angle", "pressing_path", "tempo_control"),
    )

    private val negativeHspu = DrillModeConfig(
        type = DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP,
        label = "Negative wall handstand push-up",
        metrics = listOf(
            MetricWeight("top_position", 20),
            MetricWeight("descent_control", 30),
            MetricWeight("path_consistency", 20),
            MetricWeight("line_retention", 20),
            MetricWeight("bottom_position", 10),
        ),
        faults = mapOf(
            "top_position" to "Weak lockout",
            "descent_control" to "Dropping too fast",
            "path_consistency" to "Path inconsistent",
            "line_retention" to "Line breaks mid rep",
            "bottom_position" to "Collapsed bottom",
        ),
        cuePriority = listOf("descent_control", "line_retention", "path_consistency"),
    )

    val all = listOf(chestToWall, backToWall, pike, elevatedPike, negativeHspu)

    fun byType(type: DrillType): DrillModeConfig = all.firstOrNull { it.type == type } ?: chestToWall
}
