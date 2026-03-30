package com.inversioncoach.app.drills.catalog

import com.inversioncoach.app.motion.SkeletonAnimationSpec

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

typealias CameraView = CatalogCameraView
typealias AnalysisPlane = CatalogAnalysisPlane
typealias ComparisonMode = CatalogComparisonMode

data class DrillCatalog(
    val schemaVersion: Int,
    val catalogId: String,
    val drills: List<DrillTemplate>,
)

data class DrillTemplate(
    val id: String,
    val title: String,
    val family: String,
    val movementType: CatalogMovementType,
    val tags: List<String>,
    val cameraView: CatalogCameraView,
    val supportedViews: List<CatalogCameraView>,
    val analysisPlane: CatalogAnalysisPlane,
    val comparisonMode: CatalogComparisonMode,
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
                com.inversioncoach.app.motion.SkeletonKeyframe(
                    name = "kf_$index",
                    progress = keyframe.progress,
                    joints = keyframe.joints.mapNotNull { (name, pt) ->
                    val joint = runCatching { com.inversioncoach.app.motion.BodyJoint.valueOf(name) }.getOrNull()
                    joint?.let { it to com.inversioncoach.app.motion.NormalizedPoint(pt.x, pt.y) }
                }.toMap(),
                )
            },
        )
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
