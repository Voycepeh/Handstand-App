package com.inversioncoach.app.motion

import com.inversioncoach.app.model.DrillType

data class DrillPreviewKeyframe(
    val t: Float,
    val joints: Map<String, Pair<Float, Float>>,
)

/**
 * Engine-facing drill model:
 * keep the posture model practical with phases + checkpoints + faults.
 */
data class DrillEngineModel(
    val id: DrillType,
    val movementPattern: String,
    val trackedAngles: List<String>,
    val phases: List<String>,
    val checkpoints: List<String>,
    val faults: List<String>,
)

data class DrillMetadata(
    val id: DrillType,
    val displayName: String,
    val level: String,
    val equipment: String,
    val engine: DrillEngineModel,
    val keyframes: List<DrillPreviewKeyframe>,
) {
    val movementPattern: String get() = engine.movementPattern
    val checkpoints: List<String> get() = engine.checkpoints
}

object DrillCatalog {
    private fun genericKeyframes(upright: Boolean = false): List<DrillPreviewKeyframe> {
        val hipY = if (upright) 0.48f else 0.58f
        return listOf(
            DrillPreviewKeyframe(0f, mapOf("head" to (0.5f to 0.15f), "shoulder" to (0.5f to 0.30f), "hip" to (0.5f to hipY), "hand" to (0.45f to 0.75f), "foot" to (0.5f to 0.9f))),
            DrillPreviewKeyframe(0.5f, mapOf("head" to (0.52f to 0.2f), "shoulder" to (0.52f to 0.36f), "hip" to (0.48f to (hipY + 0.08f)), "hand" to (0.42f to 0.78f), "foot" to (0.5f to 0.9f))),
            DrillPreviewKeyframe(1f, mapOf("head" to (0.5f to 0.15f), "shoulder" to (0.5f to 0.30f), "hip" to (0.5f to hipY), "hand" to (0.45f to 0.75f), "foot" to (0.5f to 0.9f))),
        )
    }

    private fun drill(
        id: DrillType,
        name: String,
        level: String,
        equipment: String,
        movementPattern: String,
        phases: List<String>,
        checkpoints: List<String>,
        faults: List<String>,
        trackedAngles: List<String> = listOf("leftElbowFlexion", "rightElbowFlexion", "trunkToAnkleLine", "shoulderToWristLine"),
        hold: Boolean = false,
    ) = DrillMetadata(
        id = id,
        displayName = name,
        level = level,
        equipment = equipment,
        engine = DrillEngineModel(
            id = id,
            movementPattern = movementPattern,
            trackedAngles = trackedAngles,
            phases = phases,
            checkpoints = checkpoints,
            faults = faults,
        ),
        keyframes = genericKeyframes(upright = hold),
    )

    val all: List<DrillMetadata> = listOf(
        drill(
            id = DrillType.WALL_PUSH_UP,
            name = "Wall Push-Up",
            level = "beginner",
            equipment = "wall",
            movementPattern = "horizontal_push",
            phases = listOf("setup", "lower", "press", "lockout"),
            checkpoints = listOf("body_line_straight", "elbows_not_flaring", "full_extension_top", "chest_to_wall_no_hip_sag"),
            faults = listOf("hip_sag", "elbow_flare", "incomplete_lockout", "forward_head"),
        ),
        drill(
            id = DrillType.INCLINE_OR_KNEE_PUSH_UP,
            name = "Incline / Knee Push-Up",
            level = "beginner",
            equipment = "bench_or_mat",
            movementPattern = "horizontal_push",
            phases = listOf("setup", "lower", "bottom", "press", "top"),
            checkpoints = listOf("head_spine_hip_line", "elbow_depth", "shoulder_over_wrist", "no_lumbar_sag"),
            faults = listOf("hip_sag", "hip_pike", "elbow_flare", "incomplete_depth"),
        ),
        drill(
            id = DrillType.BODYWEIGHT_SQUAT,
            name = "Bodyweight Squat",
            level = "beginner",
            equipment = "none",
            movementPattern = "squat",
            phases = listOf("setup", "descend", "bottom", "ascend", "stand"),
            checkpoints = listOf("knee_tracks_over_foot", "trunk_lean_threshold", "hip_crease_depth", "heels_planted", "no_valgus"),
            faults = listOf("knee_valgus", "heel_lift", "excess_trunk_lean", "insufficient_depth"),
            trackedAngles = listOf("leftKneeFlexion", "rightKneeFlexion", "trunkToVertical", "hipCreaseDepth"),
        ),
        drill(
            id = DrillType.REVERSE_LUNGE,
            name = "Reverse Lunge",
            level = "beginner",
            equipment = "none",
            movementPattern = "lunge",
            phases = listOf("setup", "step_back", "lower", "rise", "reset"),
            checkpoints = listOf("torso_upright", "front_knee_track", "pelvis_level", "back_knee_control"),
            faults = listOf("front_knee_collapse", "pelvis_drop", "forward_torso_collapse"),
            trackedAngles = listOf("frontKneeFlexion", "rearKneeFlexion", "trunkToVertical", "pelvisTilt"),
        ),
        drill(
            id = DrillType.FOREARM_PLANK,
            name = "Forearm Plank",
            level = "beginner",
            equipment = "none",
            movementPattern = "anti_extension_hold",
            phases = listOf("setup", "hold", "reset"),
            checkpoints = listOf("shoulder_hip_ankle_line", "no_hip_sag", "no_pike", "neutral_head"),
            faults = listOf("hip_sag", "hip_pike", "forward_head"),
            trackedAngles = listOf("trunkToAnkleLine", "pelvicTilt", "neckAngle"),
            hold = true,
        ),
        drill(
            id = DrillType.GLUTE_BRIDGE,
            name = "Glute Bridge",
            level = "beginner",
            equipment = "none",
            movementPattern = "hip_extension",
            phases = listOf("setup", "lift", "hold", "lower"),
            checkpoints = listOf("hip_extension_height", "ribs_down", "knees_stable", "pelvis_symmetric"),
            faults = listOf("insufficient_hip_extension", "overarch", "knee_drift", "pelvis_rotation"),
            trackedAngles = listOf("hipExtension", "pelvicTilt", "leftKneeFlexion", "rightKneeFlexion"),
        ),
        drill(
            id = DrillType.STANDARD_PUSH_UP,
            name = "Standard Push-Up",
            level = "intermediate",
            equipment = "none",
            movementPattern = "horizontal_push",
            phases = listOf("setup", "eccentric", "bottom", "concentric", "top"),
            checkpoints = listOf("hollow_body_line", "elbow_depth", "wrist_elbow_shoulder_alignment", "neutral_neck", "no_snake_up"),
            faults = listOf("hip_sag", "hip_pike", "elbow_flare", "incomplete_depth", "incomplete_lockout", "forward_head"),
        ),
        drill(
            id = DrillType.PULL_UP_OR_ASSISTED_PULL_UP,
            name = "Pull-Up / Assisted Pull-Up",
            level = "intermediate",
            equipment = "bar_or_band",
            movementPattern = "vertical_pull",
            phases = listOf("dead_hang", "pull", "top", "lower"),
            checkpoints = listOf("active_shoulders_hang", "chin_clears_bar", "strict_no_kip", "elbow_symmetry", "trunk_swing_threshold"),
            faults = listOf("passive_hang", "incomplete_top", "kip_excess", "asym_pull", "excess_swing"),
            trackedAngles = listOf("leftElbowFlexion", "rightElbowFlexion", "scapularElevation", "trunkSwing"),
        ),
        drill(
            id = DrillType.PARALLEL_BAR_DIP,
            name = "Parallel Bar Dip",
            level = "intermediate",
            equipment = "parallel_bars",
            movementPattern = "vertical_push",
            phases = listOf("support", "lower", "bottom", "press", "support"),
            checkpoints = listOf("shoulders_depressed_stable", "elbow_depth", "no_forward_head", "no_shoulder_dump"),
            faults = listOf("shoulder_dump", "incomplete_depth", "forward_head", "collapse_bottom"),
            trackedAngles = listOf("leftElbowFlexion", "rightElbowFlexion", "shoulderDepression", "headForward"),
        ),
        drill(
            id = DrillType.HANGING_KNEE_RAISE,
            name = "Hanging Knee Raise",
            level = "intermediate",
            equipment = "bar",
            movementPattern = "trunk_hip_flexion",
            phases = listOf("hang", "raise", "peak", "lower"),
            checkpoints = listOf("reduced_swing", "knees_to_target", "posterior_tilt_top", "scapular_control"),
            faults = listOf("excess_swing", "insufficient_height", "poor_pelvic_tilt", "scapular_collapse"),
            trackedAngles = listOf("hipFlexion", "pelvicTilt", "trunkSwing", "scapularElevation"),
        ),
        drill(
            id = DrillType.PIKE_PUSH_UP,
            name = "Pike Push-Up",
            level = "intermediate",
            equipment = "none",
            movementPattern = "vertical_push_prep",
            phases = listOf("setup", "lower_head", "press", "reset"),
            checkpoints = listOf("hips_high", "shoulders_loaded", "elbows_back_not_wide", "head_path_target"),
            faults = listOf("hips_too_low", "elbow_flare", "head_path_forward", "incomplete_lockout"),
        ),
        drill(
            id = DrillType.HOLLOW_BODY_HOLD,
            name = "Hollow Body Hold",
            level = "intermediate",
            equipment = "none",
            movementPattern = "anti_extension",
            phases = listOf("setup", "hold", "reset"),
            checkpoints = listOf("low_back_down", "ribs_down", "shoulders_elevated", "legs_extended_tolerance"),
            faults = listOf("lumbar_extension", "rib_flare", "shoulder_drop", "knee_bend"),
            trackedAngles = listOf("trunkToAnkleLine", "pelvicTilt", "shoulderOpening", "kneeExtension"),
            hold = true,
        ),
        drill(
            id = DrillType.WALL_FACING_HANDSTAND_HOLD,
            name = "Wall-Facing Handstand Hold",
            level = "advanced",
            equipment = "wall",
            movementPattern = "inversion_hold",
            phases = listOf("kick_walk_in", "stack", "hold", "exit"),
            checkpoints = listOf("wrist_shoulder_hip_ankle_stack", "no_banana", "shoulder_elevation", "head_neutral_between_arms"),
            faults = listOf("banana_arch", "passive_shoulders", "head_out", "wall_reliance_excess"),
            trackedAngles = listOf("stackDeviation", "shoulderOpening", "pelvicTilt", "headNeutral"),
            hold = true,
        ),
        drill(
            id = DrillType.L_SIT_HOLD,
            name = "L-Sit Hold",
            level = "advanced",
            equipment = "parallettes",
            movementPattern = "compression_hold",
            phases = listOf("support", "lift", "hold", "lower"),
            checkpoints = listOf("elbows_locked", "shoulders_depressed", "hip_flexion_height", "knees_straight", "torso_upright"),
            faults = listOf("elbow_bend", "shoulder_elevation_loss", "insufficient_compression", "knee_bend"),
            trackedAngles = listOf("elbowExtension", "shoulderDepression", "hipFlexion", "kneeExtension"),
            hold = true,
        ),
        drill(
            id = DrillType.BURPEE,
            name = "Burpee",
            level = "advanced",
            equipment = "none",
            movementPattern = "full_body_transition",
            phases = listOf("stand", "squat", "plank", "return", "jump"),
            checkpoints = listOf("stable_landing", "plank_alignment", "complete_stand_jump", "knee_foot_alignment"),
            faults = listOf("unstable_landing", "plank_line_loss", "incomplete_extension", "knee_collapse"),
            trackedAngles = listOf("trunkToAnkleLine", "kneeFlexion", "hipFlexion", "jumpExtension"),
        ),
    )

    fun byType(type: DrillType): DrillMetadata = all.firstOrNull { it.id == type } ?: all.first()
}
