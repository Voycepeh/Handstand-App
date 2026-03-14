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
            DrillType.WALL_PUSH_UP,
            "Wall Push-Up",
            cuePriority = listOf("line_quality", "elbow_path", "lockout"),
            faults = mapOf(
                "line_quality" to "Body line not straight",
                "elbow_path" to "Elbows flaring",
                "lockout" to "Incomplete elbow extension",
                "depth" to "Insufficient wall depth",
                "tempo_control" to "Rushed reps",
            ),
        ),
        cfg(
            DrillType.INCLINE_OR_KNEE_PUSH_UP,
            "Incline / Knee Push-Up",
            cuePriority = listOf("line_quality", "depth", "shoulder_loading"),
            faults = mapOf(
                "line_quality" to "Head-spine-hip line broken",
                "depth" to "Insufficient elbow depth",
                "shoulder_loading" to "Shoulders not over wrists",
                "reduced_arch" to "Lumbar sag",
                "tempo_control" to "Rushed reps",
            ),
        ),
        cfg(
            DrillType.BODYWEIGHT_SQUAT,
            "Bodyweight Squat",
            cuePriority = listOf("depth", "line_quality", "symmetry"),
            faults = mapOf(
                "depth" to "Hip crease too high",
                "line_quality" to "Excessive trunk lean",
                "symmetry" to "Knee valgus / asymmetry",
                "bottom_position" to "Heels lifting",
                "tempo_control" to "Uncontrolled cadence",
            ),
        ),
        cfg(
            DrillType.REVERSE_LUNGE,
            "Reverse Lunge",
            cuePriority = listOf("line_quality", "symmetry", "depth"),
            faults = mapOf(
                "line_quality" to "Torso not upright",
                "symmetry" to "Front knee not tracking",
                "depth" to "Insufficient lunge depth",
                "rib_pelvis_control" to "Pelvis instability",
                "tempo_control" to "Loss of control",
            ),
        ),
        cfg(
            DrillType.FOREARM_PLANK,
            "Forearm Plank",
            cuePriority = listOf("line_quality", "stillness", "rib_pelvis_control"),
            faults = mapOf(
                "line_quality" to "Shoulder-hip-ankle line broken",
                "stillness" to "Excess movement",
                "rib_pelvis_control" to "Hip sag / pike",
                "head_path" to "Head not neutral",
                "tempo_control" to "Inconsistent hold",
            ),
            metrics = listOf(
                MetricWeight("line_quality", 35),
                MetricWeight("stillness", 25),
                MetricWeight("rib_pelvis_control", 20),
                MetricWeight("shoulder_stack", 10),
                MetricWeight("head_path", 10),
            ),
        ),
        cfg(
            DrillType.GLUTE_BRIDGE,
            "Glute Bridge",
            cuePriority = listOf("depth", "rib_pelvis_control", "symmetry"),
            faults = mapOf(
                "depth" to "Insufficient hip extension",
                "rib_pelvis_control" to "Overarch / ribs up",
                "symmetry" to "Pelvis/knee asymmetry",
                "line_quality" to "Poor top alignment",
                "tempo_control" to "Uncontrolled reps",
            ),
        ),
        cfg(
            DrillType.STANDARD_PUSH_UP,
            "Standard Push-Up",
            cuePriority = listOf("line_quality", "depth", "elbow_path"),
            faults = mapOf(
                "line_quality" to "Hollow line lost",
                "depth" to "Insufficient depth",
                "elbow_path" to "Elbows too wide",
                "head_path" to "Neck not neutral",
                "tempo_control" to "Snake-up / rushed",
            ),
        ),
        cfg(
            DrillType.PULL_UP_OR_ASSISTED_PULL_UP,
            "Pull-Up / Assisted Pull-Up",
            cuePriority = listOf("scapular_elevation", "depth", "symmetry"),
            faults = mapOf(
                "scapular_elevation" to "Passive shoulders in hang",
                "depth" to "Chin not clearing bar",
                "tempo_control" to "Excessive kip/swing",
                "symmetry" to "Elbow asymmetry",
                "line_quality" to "Trunk swing too high",
            ),
        ),
        cfg(
            DrillType.PARALLEL_BAR_DIP,
            "Parallel Bar Dip",
            cuePriority = listOf("scapular_elevation", "depth", "head_path"),
            faults = mapOf(
                "scapular_elevation" to "Shoulders unstable",
                "depth" to "Insufficient dip depth",
                "head_path" to "Forward head collapse",
                "line_quality" to "Shoulder dump",
                "tempo_control" to "Uncontrolled dip",
            ),
        ),
        cfg(
            DrillType.HANGING_KNEE_RAISE,
            "Hanging Knee Raise",
            cuePriority = listOf("depth", "line_quality", "scapular_elevation"),
            faults = mapOf(
                "depth" to "Knees not reaching target",
                "line_quality" to "Excessive swing",
                "rib_pelvis_control" to "Limited posterior tilt",
                "scapular_elevation" to "Scapular control loss",
                "tempo_control" to "Fast uncontrolled descent",
            ),
        ),
        cfg(
            DrillType.PIKE_PUSH_UP,
            "Pike Push-Up",
            cuePriority = listOf("hip_height", "shoulder_loading", "head_path"),
            faults = mapOf(
                "hip_height" to "Hips too low",
                "shoulder_loading" to "Insufficient shoulder load",
                "elbow_path" to "Elbows too wide",
                "head_path" to "Head path off target",
                "tempo_control" to "Descent too fast",
            ),
            metrics = listOf(
                MetricWeight("hip_height", 25),
                MetricWeight("shoulder_loading", 25),
                MetricWeight("head_path", 20),
                MetricWeight("elbow_path", 15),
                MetricWeight("tempo_control", 15),
            ),
        ),
        cfg(
            DrillType.HOLLOW_BODY_HOLD,
            "Hollow Body Hold",
            cuePriority = listOf("rib_pelvis_control", "line_quality", "shoulder_push"),
            faults = mapOf(
                "rib_pelvis_control" to "Lower back not close to floor",
                "line_quality" to "Ribs up / line break",
                "shoulder_push" to "Shoulders not elevated",
                "leg_tension" to "Leg progression too loose",
                "stillness" to "Hold instability",
            ),
            metrics = listOf(
                MetricWeight("rib_pelvis_control", 35),
                MetricWeight("line_quality", 25),
                MetricWeight("shoulder_push", 15),
                MetricWeight("leg_tension", 15),
                MetricWeight("stillness", 10),
            ),
        ),
        cfg(
            DrillType.WALL_FACING_HANDSTAND_HOLD,
            "Wall-Facing Handstand Hold",
            cuePriority = listOf("line_quality", "scapular_elevation", "rib_pelvis_control"),
            faults = mapOf(
                "line_quality" to "Stack deviation",
                "rib_pelvis_control" to "Banana/rib flare",
                "scapular_elevation" to "Passive shoulders",
                "head_path" to "Head not neutral",
                "wall_reliance" to "Excessive wall pressure",
            ),
        ),
        cfg(
            DrillType.L_SIT_HOLD,
            "L-Sit Hold",
            cuePriority = listOf("lockout", "scapular_elevation", "depth"),
            faults = mapOf(
                "lockout" to "Elbows not locked",
                "scapular_elevation" to "Shoulders not depressed/stable",
                "depth" to "Hip flexion not high enough",
                "leg_tension" to "Knees bent in full variation",
                "line_quality" to "Torso not upright",
            ),
        ),
        cfg(
            DrillType.BURPEE,
            "Burpee",
            cuePriority = listOf("line_quality", "depth", "tempo_control"),
            faults = mapOf(
                "line_quality" to "Plank alignment lost",
                "depth" to "Transition range too shallow",
                "symmetry" to "Knee/foot alignment drift",
                "tempo_control" to "Landing instability",
                "lockout" to "Incomplete stand/jump",
            ),
        ),
        cfg(DrillType.STANDING_POSTURE_HOLD, "Standing Posture Hold", listOf("line_quality", "rib_pelvis_control", "shoulder_stack"), mapOf("line_quality" to "Forward lean / sway", "shoulder_stack" to "Shoulders not stacked", "rib_pelvis_control" to "Rib flare / pelvis tilt", "knee_lockout" to "Soft knees", "stillness" to "Too much movement"), metrics = listOf(MetricWeight("line_quality", 35), MetricWeight("shoulder_stack", 20), MetricWeight("rib_pelvis_control", 20), MetricWeight("knee_lockout", 10), MetricWeight("stillness", 15))),
        cfg(DrillType.PUSH_UP, "Push-Up (legacy)", listOf("torso_line", "depth", "elbow_path"), mapOf("torso_line" to "Sagging or piking torso", "depth" to "Insufficient depth", "lockout" to "Incomplete lockout", "elbow_path" to "Elbows flaring", "tempo_control" to "Rushed reps"), metrics = listOf(MetricWeight("torso_line", 25), MetricWeight("depth", 25), MetricWeight("lockout", 20), MetricWeight("elbow_path", 15), MetricWeight("tempo_control", 15))),
        cfg(DrillType.SIT_UP, "Sit-Up (legacy)", listOf("controlled_descent", "trunk_range", "knee_stability"), mapOf("trunk_range" to "Limited range", "controlled_descent" to "Drops to floor", "tempo_control" to "Rushed cadence", "knee_stability" to "Knees drifting", "symmetry" to "Left-right imbalance"), metrics = listOf(MetricWeight("trunk_range", 30), MetricWeight("controlled_descent", 25), MetricWeight("tempo_control", 20), MetricWeight("knee_stability", 15), MetricWeight("symmetry", 10))),
        cfg(DrillType.CHEST_TO_WALL_HANDSTAND, "Chest-to-wall Handstand", listOf("scapular_elevation", "rib_pelvis_control", "line_quality"), mapOf("line_quality" to "Banana shape", "shoulder_openness" to "Shoulders closed", "scapular_elevation" to "Passive shoulders", "rib_pelvis_control" to "Rib flare", "leg_tension" to "Soft knees / loose legs"), metrics = listOf(MetricWeight("line_quality", 30), MetricWeight("shoulder_openness", 25), MetricWeight("scapular_elevation", 20), MetricWeight("rib_pelvis_control", 15), MetricWeight("leg_tension", 10))),
        cfg(DrillType.BACK_TO_WALL_HANDSTAND, "Back-to-wall Handstand", listOf("shoulder_push", "reduced_arch", "wall_reliance"), mapOf("shoulder_push" to "Passive shoulders", "reduced_arch" to "Excessive arch", "hip_stack" to "Hips off stack", "wall_reliance" to "Too much wall pressure", "leg_tension" to "Leg line inconsistency"), metrics = listOf(MetricWeight("shoulder_push", 25), MetricWeight("reduced_arch", 25), MetricWeight("hip_stack", 20), MetricWeight("wall_reliance", 20), MetricWeight("leg_tension", 10))),
        cfg(DrillType.ELEVATED_PIKE_PUSH_UP, "Elevated Pike Push-Up", listOf("loading_angle", "pressing_path", "tempo_control"), mapOf("loading_angle" to "Shoulder loading angle off", "depth" to "Insufficient depth", "pressing_path" to "Path inconsistency", "lockout" to "Incomplete lockout", "tempo_control" to "Tempo unstable"), metrics = listOf(MetricWeight("loading_angle", 25), MetricWeight("depth", 20), MetricWeight("pressing_path", 20), MetricWeight("lockout", 15), MetricWeight("tempo_control", 20))),
        cfg(DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP, "Negative Wall Handstand Push-Up", listOf("descent_control", "line_retention", "path_consistency"), mapOf("top_position" to "Weak lockout", "descent_control" to "Dropping too fast", "path_consistency" to "Path inconsistent", "line_retention" to "Line breaks mid rep", "bottom_position" to "Collapsed bottom"), metrics = listOf(MetricWeight("top_position", 20), MetricWeight("descent_control", 30), MetricWeight("path_consistency", 20), MetricWeight("line_retention", 20), MetricWeight("bottom_position", 10))),
    )

    fun byType(type: DrillType): DrillModeConfig = all.firstOrNull { it.type == type } ?: all.first()
}
