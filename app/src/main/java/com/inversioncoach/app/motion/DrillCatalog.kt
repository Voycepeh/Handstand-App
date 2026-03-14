package com.inversioncoach.app.motion

import com.inversioncoach.app.model.DrillType

enum class DrillLevel { BEGINNER, INTERMEDIATE, ADVANCED }

enum class MovementPattern {
    VERTICAL_PUSH,
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

    fun interpolate(spec: SkeletonAnimationSpec, progress: Float, mirrored: Boolean = false): Map<BodyJoint, NormalizedPoint> {
        if (spec.keyframes.isEmpty()) return emptyMap()
        if (spec.keyframes.size == 1) return maybeMirror(spec.keyframes.first().joints, mirrored && spec.mirroredSupported)

        val normalized = if (spec.loop) {
            val wrapped = progress % 1f
            if (wrapped < 0f) wrapped + 1f else wrapped
        } else progress.coerceIn(0f, 1f)

        val frames = spec.keyframes.sortedBy { it.progress }
        val (left, right, segmentProgress) = segmentFor(frames, normalized, spec.loop)
        val eased = applyEasing(segmentProgress, left.easingToNext)
        val joints = (left.joints.keys + right.joints.keys).associateWith { joint ->
            val start = left.joints[joint] ?: right.joints[joint] ?: NormalizedPoint(0.5f, 0.5f)
            val end = right.joints[joint] ?: start
            NormalizedPoint(x = lerp(start.x, end.x, eased), y = lerp(start.y, end.y, eased))
        }
        return maybeMirror(joints, mirrored && spec.mirroredSupported)
    }

    private fun segmentFor(frames: List<SkeletonKeyframe>, progress: Float, loop: Boolean): Triple<SkeletonKeyframe, SkeletonKeyframe, Float> {
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
            val local = if (progress >= left.progress) (progress - left.progress) / span else (progress + 1f - left.progress) / span
            return Triple(left, right, local.coerceIn(0f, 1f))
        }
        return Triple(frames.last(), frames.last(), 1f)
    }

    private fun maybeMirror(joints: Map<BodyJoint, NormalizedPoint>, mirrored: Boolean): Map<BodyJoint, NormalizedPoint> {
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

    private fun frame(
        name: String,
        progress: Float,
        pelvisY: Float,
        shoulderY: Float,
        armDelta: Float,
        legDelta: Float,
        armReach: Float,
        wristY: Float,
    ): SkeletonKeyframe = SkeletonKeyframe(
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
            BodyJoint.LEFT_HIP to NormalizedPoint(0.47f, pelvisY + legDelta / 2f),
            BodyJoint.RIGHT_HIP to NormalizedPoint(0.53f, pelvisY + legDelta / 2f),
            BodyJoint.LEFT_KNEE to NormalizedPoint(0.47f - legDelta, 0.74f + legDelta),
            BodyJoint.RIGHT_KNEE to NormalizedPoint(0.53f + legDelta, 0.74f + legDelta),
            BodyJoint.LEFT_ANKLE to NormalizedPoint(0.47f - legDelta, 0.90f),
            BodyJoint.RIGHT_ANKLE to NormalizedPoint(0.53f + legDelta, 0.90f),
        ),
    )

    private fun symmetricSpec(id: String, pelvisY: Float, shoulderY: Float, armReach: Float, legSpread: Float, wristY: Float): SkeletonAnimationSpec =
        SkeletonAnimationSpec(
            id = id,
            fpsHint = 15,
            loop = true,
            mirroredSupported = false,
            keyframes = listOf(
                frame("neutral", 0f, pelvisY, shoulderY, armDelta = 0f, legDelta = 0f, armReach = armReach, wristY = wristY),
                frame("mid_eccentric", 0.25f, pelvisY + 0.05f, shoulderY + 0.03f, armDelta = 0.04f, legDelta = 0.02f + legSpread, armReach = armReach, wristY = wristY + 0.02f),
                frame("bottom", 0.5f, pelvisY + 0.1f, shoulderY + 0.06f, armDelta = 0.08f, legDelta = 0.04f + legSpread, armReach = armReach + 0.01f, wristY = wristY + 0.04f),
                frame("mid_concentric", 0.75f, pelvisY + 0.05f, shoulderY + 0.03f, armDelta = 0.03f, legDelta = 0.02f + legSpread, armReach = armReach, wristY = wristY + 0.02f),
                frame("top", 1f, pelvisY, shoulderY, armDelta = 0f, legDelta = 0f, armReach = armReach, wristY = wristY),
            ),
        )

    private fun holdSpec(id: String, inverted: Boolean): SkeletonAnimationSpec = symmetricSpec(
        id = id,
        pelvisY = if (inverted) 0.42f else 0.62f,
        shoulderY = if (inverted) 0.22f else 0.30f,
        armReach = if (inverted) 0.01f else 0.06f,
        legSpread = 0f,
        wristY = if (inverted) 0.32f else 0.50f,
    )

    private fun pikeSpec(id: String): SkeletonAnimationSpec = symmetricSpec(
        id = id,
        pelvisY = 0.46f,
        shoulderY = 0.24f,
        armReach = 0.03f,
        legSpread = 0.01f,
        wristY = 0.38f,
    )

    private fun def(
        id: DrillType,
        displayName: String,
        category: String,
        level: DrillLevel,
        equipment: List<String>,
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
        movementPattern = MovementPattern.VERTICAL_PUSH,
        requiredLandmarks = baseLandmarks,
        mainPhases = phases.map { DrillPhase(it, it.replace('_', ' ')) },
        commonFaults = faults,
        cues = cues,
        repMode = repMode,
        previewAnimationId = animationSpec.id,
        animationSpec = animationSpec,
        postureRulePlaceholders = defaultRulePlaceholders(repMode),
    )

    private val drills = listOf(
        def(DrillType.FREESTANDING_HANDSTAND_FUTURE, "Free Standing Handstand", "handstand", DrillLevel.INTERMEDIATE, listOf("none"), listOf("neutral", "start", "hold", "top"), listOf("banana_line", "passive_shoulder", "head_poke"), listOf("Push tall", "Stack ribs over pelvis", "Squeeze legs together"), RepMode.HOLD_BASED, holdSpec("free_standing_handstand", inverted = true)),
        def(DrillType.CHEST_TO_WALL_HANDSTAND, "Wall Assisted Handstand", "handstand", DrillLevel.BEGINNER, listOf("wall"), listOf("neutral", "start", "hold", "top"), listOf("banana_line", "passive_shoulder", "wall_reliance"), listOf("Push through shoulders", "Keep ribs tucked", "Use light heel pressure"), RepMode.HOLD_BASED, holdSpec("wall_assisted_handstand", inverted = true)),
        def(DrillType.PIKE_PUSH_UP, "Pike Push-Up", "handstand push", DrillLevel.BEGINNER, listOf("floor"), listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("hips_low", "elbow_flare", "head_path_forward"), listOf("Hips high", "Tripod head path", "Push away hard"), RepMode.REP_BASED, pikeSpec("pike_push_up")),
        def(DrillType.ELEVATED_PIKE_PUSH_UP, "Elevated Pike Push-Up", "handstand push", DrillLevel.INTERMEDIATE, listOf("box"), listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("hips_drop", "short_depth", "rushed_tempo"), listOf("Keep hips over shoulders", "Descend under control", "Drive straight up"), RepMode.REP_BASED, pikeSpec("elevated_pike_push_up")),
        def(DrillType.PUSH_UP, "Free Standing Handstand Push-Up", "handstand push", DrillLevel.INTERMEDIATE, listOf("none"), listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("line_break", "depth_short", "lockout_missing"), listOf("Stay stacked", "Touch depth target", "Finish fully"), RepMode.REP_BASED, holdSpec("free_standing_hspu", inverted = true)),
        def(DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP, "Wall Assisted Handstand Push-Up", "handstand push", DrillLevel.INTERMEDIATE, listOf("wall"), listOf("neutral", "start", "mid_eccentric", "bottom", "mid_concentric", "top"), listOf("wall_dump", "descent_fast", "elbow_flare"), listOf("Lower with control", "Keep line against wall", "Press to stacked top"), RepMode.REP_BASED, holdSpec("wall_assisted_hspu", inverted = true)),
    )

    val all: List<DrillDefinition> = drills
    private val byType = all.associateBy { it.id }
    private val byAnimationId = all.associateBy { it.previewAnimationId }

    fun byType(type: DrillType): DrillDefinition = byType[type] ?: drills.first()
    fun byAnimationId(animationId: String): SkeletonAnimationSpec? = byAnimationId[animationId]?.animationSpec
}
