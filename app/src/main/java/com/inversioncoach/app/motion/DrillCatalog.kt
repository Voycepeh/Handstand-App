package com.inversioncoach.app.motion

import com.inversioncoach.app.model.DrillType

enum class DrillLevel { BEGINNER, INTERMEDIATE, ADVANCED }

enum class MovementPattern {
    HORIZONTAL_PUSH,
    VERTICAL_PUSH,
    SQUAT_PATTERN,
    VERTICAL_PULL,
    HIP_EXTENSION,
    CORE_FLEXION_COMPRESSION,
    ANTI_EXTENSION_LINE_CONTROL,
}

enum class RepMode { REP_BASED, HOLD_BASED }

enum class EasingType { LINEAR, EASE_IN_OUT }

enum class BodyJoint {
    HEAD,
    NECK,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    RIBCAGE,
    PELVIS,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
}

data class NormalizedPoint(val x: Float, val y: Float)

data class SkeletonKeyframe(
    val name: String,
    val progress: Float,
    val joints: Map<BodyJoint, NormalizedPoint>,
    val easingToNext: EasingType = EasingType.LINEAR,
)

data class SkeletonAnimationSpec(
    val id: String,
    val fpsHint: Int = 15,
    val loop: Boolean = true,
    val mirroredSupported: Boolean = false,
    val keyframes: List<SkeletonKeyframe>,
)

data class DrillPhase(val id: String, val label: String)

data class PostureRulePlaceholder(
    val id: String,
    val description: String,
    val mode: RepMode,
)

data class DrillDefinition(
    val id: DrillType,
    val displayName: String,
    val category: String,
    val level: DrillLevel,
    val equipment: List<String>,
    val movementPattern: MovementPattern,
    val requiredLandmarks: List<BodyJoint>,
    val mainPhases: List<DrillPhase>,
    val commonFaults: List<String>,
    val cues: List<String>,
    val repMode: RepMode,
    val previewAnimationId: String,
    val animationSpec: SkeletonAnimationSpec,
    val postureRulePlaceholders: List<PostureRulePlaceholder>,
)

object SkeletonRig {
    val bones: List<Pair<BodyJoint, BodyJoint>> = listOf(
        BodyJoint.HEAD to BodyJoint.NECK,
        BodyJoint.NECK to BodyJoint.LEFT_SHOULDER,
        BodyJoint.NECK to BodyJoint.RIGHT_SHOULDER,
        BodyJoint.LEFT_SHOULDER to BodyJoint.LEFT_ELBOW,
        BodyJoint.LEFT_ELBOW to BodyJoint.LEFT_WRIST,
        BodyJoint.RIGHT_SHOULDER to BodyJoint.RIGHT_ELBOW,
        BodyJoint.RIGHT_ELBOW to BodyJoint.RIGHT_WRIST,
        BodyJoint.NECK to BodyJoint.RIBCAGE,
        BodyJoint.RIBCAGE to BodyJoint.PELVIS,
        BodyJoint.PELVIS to BodyJoint.LEFT_HIP,
        BodyJoint.LEFT_HIP to BodyJoint.LEFT_KNEE,
        BodyJoint.LEFT_KNEE to BodyJoint.LEFT_ANKLE,
        BodyJoint.PELVIS to BodyJoint.RIGHT_HIP,
        BodyJoint.RIGHT_HIP to BodyJoint.RIGHT_KNEE,
        BodyJoint.RIGHT_KNEE to BodyJoint.RIGHT_ANKLE,
    )
}

object SkeletonAnimationEngine {
    private val mirrorPairs = mapOf(
        BodyJoint.LEFT_SHOULDER to BodyJoint.RIGHT_SHOULDER,
        BodyJoint.RIGHT_SHOULDER to BodyJoint.LEFT_SHOULDER,
        BodyJoint.LEFT_ELBOW to BodyJoint.RIGHT_ELBOW,
        BodyJoint.RIGHT_ELBOW to BodyJoint.LEFT_ELBOW,
        BodyJoint.LEFT_WRIST to BodyJoint.RIGHT_WRIST,
        BodyJoint.RIGHT_WRIST to BodyJoint.LEFT_WRIST,
        BodyJoint.LEFT_HIP to BodyJoint.RIGHT_HIP,
        BodyJoint.RIGHT_HIP to BodyJoint.LEFT_HIP,
        BodyJoint.LEFT_KNEE to BodyJoint.RIGHT_KNEE,
        BodyJoint.RIGHT_KNEE to BodyJoint.LEFT_KNEE,
        BodyJoint.LEFT_ANKLE to BodyJoint.RIGHT_ANKLE,
        BodyJoint.RIGHT_ANKLE to BodyJoint.LEFT_ANKLE,
    )

    fun interpolate(
        spec: SkeletonAnimationSpec,
        progress: Float,
        mirrored: Boolean = false,
    ): Map<BodyJoint, NormalizedPoint> {
        if (spec.keyframes.isEmpty()) return emptyMap()
        if (spec.keyframes.size == 1) return maybeMirror(spec.keyframes.first().joints, mirrored && spec.mirroredSupported)

        val normalized = if (spec.loop) {
            val wrapped = progress % 1f
            if (wrapped < 0f) wrapped + 1f else wrapped
        } else {
            progress.coerceIn(0f, 1f)
        }

        val frames = spec.keyframes.sortedBy { it.progress }
        val (left, right, segmentProgress) = segmentFor(frames, normalized, spec.loop)
        val eased = applyEasing(segmentProgress, left.easingToNext)
        val joints = (left.joints.keys + right.joints.keys).associateWith { joint ->
            val start = left.joints[joint] ?: right.joints[joint] ?: NormalizedPoint(0.5f, 0.5f)
            val end = right.joints[joint] ?: start
            NormalizedPoint(
                x = lerp(start.x, end.x, eased),
                y = lerp(start.y, end.y, eased),
            )
        }
        return maybeMirror(joints, mirrored && spec.mirroredSupported)
    }

    private fun segmentFor(
        frames: List<SkeletonKeyframe>,
        progress: Float,
        loop: Boolean,
    ): Triple<SkeletonKeyframe, SkeletonKeyframe, Float> {
        for (i in 0 until frames.lastIndex) {
            val left = frames[i]
            val right = frames[i + 1]
            if (progress in left.progress..right.progress) {
                val local = ((progress - left.progress) / (right.progress - left.progress)).coerceIn(0f, 1f)
                return Triple(left, right, local)
            }
        }

        if (loop) {
            val left = frames.last()
            val right = frames.first()
            val span = 1f - left.progress + right.progress
            val localProgress = if (progress >= left.progress) {
                (progress - left.progress) / span
            } else {
                (progress + 1f - left.progress) / span
            }
            return Triple(left, right, localProgress.coerceIn(0f, 1f))
        }

        return Triple(frames.last(), frames.last(), 1f)
    }

    private fun maybeMirror(
        joints: Map<BodyJoint, NormalizedPoint>,
        mirrored: Boolean,
    ): Map<BodyJoint, NormalizedPoint> {
        if (!mirrored) return joints
        return joints.mapKeys { (joint, _) -> mirrorPairs[joint] ?: joint }
            .mapValues { (_, point) -> NormalizedPoint(1f - point.x, point.y) }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun applyEasing(progress: Float, easing: EasingType): Float = when (easing) {
        EasingType.LINEAR -> progress
        EasingType.EASE_IN_OUT -> (0.5f - kotlin.math.cos(progress * Math.PI).toFloat() / 2f)
    }
}

object DrillCatalog {
    private val baseLandmarks = BodyJoint.entries.toList()

    private fun defaultRulePlaceholders(mode: RepMode): List<PostureRulePlaceholder> =
        if (mode == RepMode.HOLD_BASED) {
            listOf(
                PostureRulePlaceholder("line_deviation", "Track shoulder-ribcage-pelvis line drift over hold time", mode),
                PostureRulePlaceholder("fault_timer", "Start fault timer when stack quality drops below tolerance", mode),
                PostureRulePlaceholder("quality_score", "Score hold quality from stability + alignment", mode),
            )
        } else {
            listOf(
                PostureRulePlaceholder("phase_thresholds", "Rep phases use angle thresholds and minimum persistence", mode),
                PostureRulePlaceholder("symmetry_guard", "Compare left/right timing + depth each rep", mode),
                PostureRulePlaceholder("tempo_guard", "Flag reps that bypass eccentric or lockout", mode),
            )
        }

    private fun symmetricSpec(
        id: String,
        pelvisY: Float,
        shoulderY: Float,
        armReach: Float,
        legSpread: Float,
        wristY: Float,
    ): SkeletonAnimationSpec {
        return SkeletonAnimationSpec(
            id = id,
            fpsHint = 15,
            loop = true,
            mirroredSupported = false,
            keyframes = listOf(
                frame("neutral", 0f, pelvisY = pelvisY, shoulderY = shoulderY, armDelta = 0.0f, legDelta = 0.0f, armReach = armReach, wristY = wristY),
                frame("mid_eccentric", 0.25f, pelvisY = pelvisY + 0.05f, shoulderY = shoulderY + 0.03f, armDelta = 0.04f, legDelta = 0.02f + legSpread, armReach = armReach, wristY = wristY + 0.02f),
                frame("bottom", 0.5f, pelvisY = pelvisY + 0.1f, shoulderY = shoulderY + 0.06f, armDelta = 0.08f, legDelta = 0.04f + legSpread, armReach = armReach + 0.01f, wristY = wristY + 0.04f),
                frame("mid_concentric", 0.75f, pelvisY = pelvisY + 0.05f, shoulderY = shoulderY + 0.03f, armDelta = 0.03f, legDelta = 0.02f + legSpread, armReach = armReach, wristY = wristY + 0.02f),
                frame("top", 1f, pelvisY = pelvisY, shoulderY = shoulderY, armDelta = 0.0f, legDelta = 0.0f, armReach = armReach, wristY = wristY),
            ),
        )
    }

    private fun horizontalPushSpec(id: String): SkeletonAnimationSpec = symmetricSpec(
        id = id,
        pelvisY = 0.62f,
        shoulderY = 0.31f,
        armReach = 0.07f,
        legSpread = 0.0f,
        wristY = 0.52f,
    )

    private fun squatSpec(id: String): SkeletonAnimationSpec = symmetricSpec(
        id = id,
        pelvisY = 0.56f,
        shoulderY = 0.28f,
        armReach = 0.05f,
        legSpread = 0.03f,
        wristY = 0.48f,
    )

    private fun verticalPullSpec(id: String): SkeletonAnimationSpec = symmetricSpec(
        id = id,
        pelvisY = 0.54f,
        shoulderY = 0.27f,
        armReach = 0.03f,
        legSpread = 0.01f,
        wristY = 0.40f,
    )

    private fun holdSpec(id: String, inverted: Boolean): SkeletonAnimationSpec = symmetricSpec(
        id = id,
        pelvisY = if (inverted) 0.42f else 0.62f,
        shoulderY = if (inverted) 0.22f else 0.30f,
        armReach = if (inverted) 0.01f else 0.06f,
        legSpread = if (inverted) 0.0f else 0.01f,
        wristY = if (inverted) 0.32f else 0.50f,
    )

    private fun lungeSpec(id: String): SkeletonAnimationSpec = SkeletonAnimationSpec(
        id = id,
        fpsHint = 14,
        loop = true,
        mirroredSupported = true,
        keyframes = listOf(
            frame("neutral", 0f, pelvisY = 0.58f, shoulderY = 0.30f, armDelta = 0f, legDelta = 0f),
            frame("start", 0.2f, pelvisY = 0.6f, shoulderY = 0.31f, armDelta = 0.02f, legDelta = 0.03f),
            frame("bottom", 0.5f, pelvisY = 0.68f, shoulderY = 0.33f, armDelta = 0.02f, legDelta = 0.08f, asymmetry = true),
            frame("rise", 0.8f, pelvisY = 0.62f, shoulderY = 0.32f, armDelta = 0.01f, legDelta = 0.04f, asymmetry = true),
            frame("top", 1f, pelvisY = 0.58f, shoulderY = 0.30f, armDelta = 0f, legDelta = 0f),
        ),
    )

    private fun frame(
        name: String,
        progress: Float,
        pelvisY: Float,
        shoulderY: Float,
        armDelta: Float,
        legDelta: Float,
        armReach: Float = 0.06f,
        wristY: Float = 0.50f,
        asymmetry: Boolean = false,
    ): SkeletonKeyframe {
        val leftLegX = if (asymmetry) 0.43f else 0.47f
        val rightLegX = if (asymmetry) 0.57f else 0.53f
        return SkeletonKeyframe(
            name = name,
            progress = progress,
            easingToNext = EasingType.EASE_IN_OUT,
            joints = mapOf(
                BodyJoint.HEAD to NormalizedPoint(0.5f, 0.14f + armDelta / 4f),
                BodyJoint.NECK to NormalizedPoint(0.5f, 0.23f + armDelta / 4f),
                BodyJoint.LEFT_SHOULDER to NormalizedPoint(0.44f, shoulderY + armDelta),
                BodyJoint.RIGHT_SHOULDER to NormalizedPoint(0.56f, shoulderY + armDelta),
                BodyJoint.LEFT_ELBOW to NormalizedPoint(0.44f - armReach, shoulderY + 0.09f + armDelta),
                BodyJoint.RIGHT_ELBOW to NormalizedPoint(0.56f + armReach, shoulderY + 0.09f + armDelta),
                BodyJoint.LEFT_WRIST to NormalizedPoint(0.44f - armReach - 0.02f, wristY + armDelta),
                BodyJoint.RIGHT_WRIST to NormalizedPoint(0.56f + armReach + 0.02f, wristY + armDelta),
                BodyJoint.RIBCAGE to NormalizedPoint(0.5f, shoulderY + 0.08f + armDelta / 2f),
                BodyJoint.PELVIS to NormalizedPoint(0.5f, pelvisY),
                BodyJoint.LEFT_HIP to NormalizedPoint(leftLegX, pelvisY + legDelta / 2f),
                BodyJoint.RIGHT_HIP to NormalizedPoint(rightLegX, pelvisY + legDelta / 2f),
                BodyJoint.LEFT_KNEE to NormalizedPoint(leftLegX - legDelta, 0.74f + legDelta),
                BodyJoint.RIGHT_KNEE to NormalizedPoint(rightLegX + legDelta, 0.74f + if (asymmetry) legDelta / 2f else legDelta),
                BodyJoint.LEFT_ANKLE to NormalizedPoint(leftLegX - legDelta, 0.90f),
                BodyJoint.RIGHT_ANKLE to NormalizedPoint(rightLegX + legDelta, 0.90f),
            ),
        )
    }

    private fun def(
        id: DrillType,
        displayName: String,
        category: String,
        level: DrillLevel,
        equipment: List<String>,
        movementPattern: MovementPattern,
        phases: List<String>,
        faults: List<String>,
        cues: List<String>,
        repMode: RepMode,
        animationSpec: SkeletonAnimationSpec,
    ): DrillDefinition = DrillDefinition(
        id = id,
        displayName = displayName,
        category = category,
        level = level,
        equipment = equipment,
        movementPattern = movementPattern,
        requiredLandmarks = baseLandmarks,
        mainPhases = phases.map { DrillPhase(it, it.replace('_', ' ')) },
        commonFaults = faults,
        cues = cues,
        repMode = repMode,
        previewAnimationId = animationSpec.id,
        animationSpec = animationSpec,
        postureRulePlaceholders = defaultRulePlaceholders(repMode),
    )

    // Wave 1 seeded drills (shipping now)
    private val wave1 = listOf(
        def(DrillType.WALL_PUSH_UP, "Wall Push-Up", "push-up progression", DrillLevel.BEGINNER, listOf("wall"), MovementPattern.HORIZONTAL_PUSH, listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("hip_sag", "elbow_flare", "head_forward"), listOf("Brace line", "Lower under control", "Finish with reach"), RepMode.REP_BASED, horizontalPushSpec("wall_push_up")),
        def(DrillType.STANDARD_PUSH_UP, "Push-Up", "push-up progression", DrillLevel.INTERMEDIATE, listOf("floor"), MovementPattern.HORIZONTAL_PUSH, listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("snake_rep", "incomplete_depth", "locked_ribs"), listOf("Keep hollow body", "Chest and hips rise together", "Lock out softly"), RepMode.REP_BASED, horizontalPushSpec("push_up")),
        def(DrillType.BODYWEIGHT_SQUAT, "Squat", "squat progression", DrillLevel.BEGINNER, listOf("none"), MovementPattern.SQUAT_PATTERN, listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("knee_valgus", "heel_lift", "depth_short"), listOf("Sit between heels", "Track knees over toes", "Stand tall at top"), RepMode.REP_BASED, squatSpec("squat")),
        def(DrillType.REVERSE_LUNGE, "Reverse Lunge", "squat progression", DrillLevel.BEGINNER, listOf("none"), MovementPattern.SQUAT_PATTERN, listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("front_knee_collapse", "torso_drop", "unstable_step"), listOf("Step back long", "Stay tall", "Push through front foot"), RepMode.REP_BASED, lungeSpec("reverse_lunge")),
        def(DrillType.FOREARM_PLANK, "Plank", "line control", DrillLevel.BEGINNER, listOf("mat"), MovementPattern.ANTI_EXTENSION_LINE_CONTROL, listOf("neutral", "start", "hold", "top"), listOf("hip_sag", "hip_pike", "head_drop"), listOf("Pull ribs down", "Squeeze glutes", "Reach elbows forward"), RepMode.HOLD_BASED, holdSpec("plank", inverted = false)),
        def(DrillType.GLUTE_BRIDGE, "Glute Bridge", "bridge progression", DrillLevel.BEGINNER, listOf("mat"), MovementPattern.HIP_EXTENSION, listOf("neutral", "start", "mid_concentric", "top", "mid_eccentric"), listOf("overarch", "knee_wobble", "short_extension"), listOf("Drive through heels", "Ribs stay down", "Pause at top"), RepMode.REP_BASED, squatSpec("glute_bridge")),
        def(DrillType.PULL_UP_OR_ASSISTED_PULL_UP, "Pull-Up", "pull-up progression", DrillLevel.INTERMEDIATE, listOf("bar", "band_optional"), MovementPattern.VERTICAL_PULL, listOf("neutral", "start", "mid_concentric", "top", "mid_eccentric"), listOf("kip_swing", "chin_short", "shrugged_pull"), listOf("Start active hang", "Pull elbows to ribs", "Control descent"), RepMode.REP_BASED, verticalPullSpec("pull_up")),
        def(DrillType.PARALLEL_BAR_DIP, "Dip", "dip progression", DrillLevel.INTERMEDIATE, listOf("parallel_bars"), MovementPattern.VERTICAL_PUSH, listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("shoulder_dump", "depth_short", "forward_head"), listOf("Shoulders down", "Lean slightly", "Press to support"), RepMode.REP_BASED, verticalPullSpec("dip")),
        def(DrillType.HANGING_KNEE_RAISE, "Hanging Knee Raise", "leg raise progression", DrillLevel.INTERMEDIATE, listOf("bar"), MovementPattern.CORE_FLEXION_COMPRESSION, listOf("neutral", "start", "mid_concentric", "top", "mid_eccentric"), listOf("swing", "low_knee_height", "passive_hang"), listOf("Own the hang", "Posterior tilt at top", "Lower slowly"), RepMode.REP_BASED, verticalPullSpec("hanging_knee_raise")),
        def(DrillType.PIKE_PUSH_UP, "Pike Push-Up", "handstand prep", DrillLevel.INTERMEDIATE, listOf("floor", "box_optional"), MovementPattern.VERTICAL_PUSH, listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("hips_low", "elbow_flare", "head_path_forward"), listOf("Hips high", "Tripod head path", "Push away hard"), RepMode.REP_BASED, holdSpec("pike_push_up", inverted = true)),
        def(DrillType.HOLLOW_BODY_HOLD, "Hollow Hold", "hollow/plank", DrillLevel.INTERMEDIATE, listOf("mat"), MovementPattern.ANTI_EXTENSION_LINE_CONTROL, listOf("neutral", "start", "hold", "top"), listOf("rib_flare", "lumbar_gap", "knee_bend"), listOf("Crush low back down", "Reach long", "Breathe quietly"), RepMode.HOLD_BASED, holdSpec("hollow_hold", inverted = false)),
        def(DrillType.WALL_FACING_HANDSTAND_HOLD, "Wall-Facing Handstand", "handstand prep", DrillLevel.ADVANCED, listOf("wall"), MovementPattern.ANTI_EXTENSION_LINE_CONTROL, listOf("neutral", "start", "hold", "top"), listOf("banana_line", "passive_shoulder", "head_poke"), listOf("Push tall", "Stack ribs over pelvis", "Eyes between hands"), RepMode.HOLD_BASED, holdSpec("wall_facing_handstand", inverted = true)),
        def(DrillType.L_SIT_HOLD, "L-Sit", "L-sit progression", DrillLevel.ADVANCED, listOf("parallettes", "dip_bars"), MovementPattern.CORE_FLEXION_COMPRESSION, listOf("neutral", "start", "lift", "hold", "lower"), listOf("elbow_bend", "knee_soft", "collapsed_support"), listOf("Lock elbows", "Lift from hips", "Press shoulders down"), RepMode.HOLD_BASED, verticalPullSpec("l_sit")),
    )

    // Wave 2 expansion TODO: add richer asymmetrical/skill-specific keyframes.
    private val wave2 = listOf(
        def(DrillType.INCLINE_OR_KNEE_PUSH_UP, "Archer Push-Up (stub)", "push-up progression", DrillLevel.ADVANCED, listOf("floor"), MovementPattern.HORIZONTAL_PUSH, listOf("neutral", "start", "bottom", "top"), listOf("arm_shift_loss"), listOf("Shift over working arm"), RepMode.REP_BASED, horizontalPushSpec("archer_push_up")),
        def(DrillType.BURPEE, "Archer Pull-Up / Skill Prep (stub)", "pull-up progression", DrillLevel.ADVANCED, listOf("bar"), MovementPattern.VERTICAL_PULL, listOf("neutral", "start", "top", "lower"), listOf("swing"), listOf("Pause at top"), RepMode.REP_BASED, verticalPullSpec("archer_pull_up")),
    )

    val all: List<DrillDefinition> = wave1 + wave2

    private val byType = all.associateBy { it.id }
    private val byAnimationId = all.associateBy { it.previewAnimationId }

    fun byType(type: DrillType): DrillDefinition = byType[type] ?: wave1.first()

    fun byAnimationId(animationId: String): SkeletonAnimationSpec? = byAnimationId[animationId]?.animationSpec
}
