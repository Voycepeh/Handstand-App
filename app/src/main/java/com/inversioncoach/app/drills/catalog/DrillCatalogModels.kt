package com.inversioncoach.app.drills.catalog

enum class CatalogMovementType {
    HOLD,
    REP,
}

enum class CameraView {
    LEFT_PROFILE,
    RIGHT_PROFILE,
    FRONT,
}

enum class AnalysisPlane {
    SAGITTAL,
    FRONTAL,
}

enum class ComparisonMode {
    POSE_TIMELINE,
    PHASE_CHECKPOINTS,
}

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
    val cameraView: CameraView,
    val supportedViews: List<CameraView>,
    val analysisPlane: AnalysisPlane,
    val comparisonMode: ComparisonMode,
    val phases: List<DrillPhaseTemplate>,
    val skeletonTemplate: SkeletonTemplate,
    val calibration: CalibrationTemplate,
)

data class DrillPhaseTemplate(
    val id: String,
    val label: String,
    val order: Int,
    val progressWindow: PhaseWindow? = null,
)

data class SkeletonTemplate(
    val id: String,
    val loop: Boolean,
    val mirroredSupported: Boolean = false,
    val framesPerSecond: Int,
    val keyframes: List<SkeletonKeyframeTemplate>,
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
