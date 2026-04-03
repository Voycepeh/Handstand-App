package com.inversioncoach.app.drills.catalog

import com.inversioncoach.app.motion.SkeletonAnimationSpec
import com.inversioncoach.app.motion.BodyJoint
import com.inversioncoach.app.motion.NormalizedPoint
import com.inversioncoach.app.motion.SkeletonKeyframe

enum class CatalogMovementType {
    HOLD,
    REP,
}

enum class CatalogCameraView {
    SIDE,
    FRONT,
    LEFT_PROFILE,
    RIGHT_PROFILE,
}

enum class CatalogAnalysisPlane {
    SAGITTAL,
    FRONTAL,
}

enum class CatalogComparisonMode {
    OVERLAY,
    POSE_TIMELINE,
    PHASE_CHECKPOINTS,
}

enum class CatalogNormalizationBasis {
    HIPS,
    SHOULDERS,
    TORSO,
    ANKLES,
}

typealias CameraView = CatalogCameraView
typealias AnalysisPlane = CatalogAnalysisPlane
typealias ComparisonMode = CatalogComparisonMode
typealias NormalizationBasis = CatalogNormalizationBasis

data class DrillCatalog(
    val schemaVersion: Int,
    val catalogId: String,
    val drills: List<DrillTemplate>,
)

data class DrillTemplate(
    val id: String,
    val title: String,
    val description: String = "",
    val family: String,
    val movementType: CatalogMovementType,
    val tags: List<String>,
    val cameraView: CatalogCameraView,
    val supportedViews: List<CatalogCameraView>,
    val analysisPlane: CatalogAnalysisPlane,
    val comparisonMode: CatalogComparisonMode,
    val keyJoints: List<String> = emptyList(),
    val normalizationBasis: CatalogNormalizationBasis = CatalogNormalizationBasis.HIPS,
    val phases: List<DrillPhaseTemplate>,
    val skeletonTemplate: SkeletonTemplate,
    val calibration: CalibrationTemplate,
) {
    val metricThresholds: Map<String, Float> get() = calibration.metricThresholds
    val animationSpec: SkeletonAnimationSpec
        get() = SkeletonAnimationSpec(
            id = skeletonTemplate.id,
            fpsHint = skeletonTemplate.framesPerSecond,
            loop = skeletonTemplate.loop,
            keyframes = skeletonTemplate.keyframes.mapIndexed { index, keyframe ->
                SkeletonKeyframe(
                    name = "kf_$index",
                    progress = keyframe.progress,
                    joints = keyframe.joints.mapNotNull { (name, pt) ->
                    val joint = name.toBodyJointOrNull()
                    joint?.let { it to NormalizedPoint(pt.x, pt.y) }
                }.toMap(),
                )
            },
        )
}

private fun String.toBodyJointOrNull(): BodyJoint? {
    val normalized = trim()
    return when (normalized.lowercase()) {
        "head", "nose" -> BodyJoint.HEAD
        "neck" -> BodyJoint.NECK
        "left_shoulder", "shoulder_left" -> BodyJoint.LEFT_SHOULDER
        "right_shoulder", "shoulder_right" -> BodyJoint.RIGHT_SHOULDER
        "left_elbow", "elbow_left" -> BodyJoint.LEFT_ELBOW
        "right_elbow", "elbow_right" -> BodyJoint.RIGHT_ELBOW
        "left_wrist", "wrist_left" -> BodyJoint.LEFT_WRIST
        "right_wrist", "wrist_right" -> BodyJoint.RIGHT_WRIST
        "ribcage" -> BodyJoint.RIBCAGE
        "pelvis" -> BodyJoint.PELVIS
        "left_hip", "hip_left" -> BodyJoint.LEFT_HIP
        "right_hip", "hip_right" -> BodyJoint.RIGHT_HIP
        "left_knee", "knee_left" -> BodyJoint.LEFT_KNEE
        "right_knee", "knee_right" -> BodyJoint.RIGHT_KNEE
        "left_ankle", "ankle_left" -> BodyJoint.LEFT_ANKLE
        "right_ankle", "ankle_right" -> BodyJoint.RIGHT_ANKLE
        else -> runCatching { BodyJoint.valueOf(normalized.uppercase()) }.getOrNull()
    }
}

data class DrillPhaseTemplate(
    val id: String,
    val label: String,
    val order: Int,
    val progressWindow: PhaseWindow = PhaseWindow(0f, 1f),
)

data class SkeletonTemplate(
    val id: String,
    val loop: Boolean,
    val mirroredSupported: Boolean = false,
    val framesPerSecond: Int,
    val phasePoses: List<PhasePoseTemplate> = emptyList(),
    val keyframes: List<SkeletonKeyframeTemplate>,
)

data class PhasePoseTemplate(
    val phaseId: String,
    val name: String,
    val joints: Map<String, JointPoint>,
    val holdDurationMs: Int? = null,
    val transitionDurationMs: Int = 700,
    val authoring: PhasePoseAuthoring? = null,
)

data class PhasePoseAuthoring(
    val sourceType: PhasePoseSourceType = PhasePoseSourceType.MANUAL,
    val sourceImageUri: String? = null,
    val detectedJoints: Map<String, JointPoint> = emptyMap(),
    val jointConfidence: Map<String, Float> = emptyMap(),
    val manualOffsets: Map<String, JointPoint> = emptyMap(),
    val qualityScore: Float? = null,
    val guides: PhaseBoundaryGuides = PhaseBoundaryGuides(),
)

enum class PhasePoseSourceType {
    MANUAL,
    IMAGE,
}

data class PhaseBoundaryGuides(
    val showFrameGuides: Boolean = true,
    val showFloorLine: Boolean = false,
    val floorLineY: Float = 0.9f,
    val showWallLine: Boolean = false,
    val wallLineX: Float = 0.1f,
    val showBarLine: Boolean = false,
    val barLineY: Float = 0.18f,
)

data class SkeletonKeyframeTemplate(
    val progress: Float,
    val joints: Map<String, JointPoint>,
)

data class JointPoint(
    val x: Float,
    val y: Float,
)

data class CalibrationTemplate(
    val metricThresholds: Map<String, Float>,
    val phaseWindows: Map<String, PhaseWindow>,
)

data class PhaseWindow(
    val start: Float,
    val end: Float,
)
