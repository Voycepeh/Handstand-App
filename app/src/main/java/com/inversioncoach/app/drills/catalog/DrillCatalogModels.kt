package com.inversioncoach.app.drills.catalog

import com.inversioncoach.app.motion.SkeletonAnimationSpec

enum class CatalogMovementType { HOLD, REP }
enum class CatalogCameraView { SIDE, FRONT }
enum class CatalogAnalysisPlane { SAGITTAL, FRONTAL }
enum class CatalogComparisonMode { OFF, OVERLAY }

data class PhaseWindow(val start: Float, val end: Float)

data class DrillPhaseTemplate(
    val id: String,
    val label: String,
    val order: Int,
    val progressWindow: PhaseWindow,
)

data class DrillTemplate(
    val id: String,
    val title: String,
    val family: String,
    val movementType: CatalogMovementType,
    val cameraView: CatalogCameraView,
    val supportedViews: List<CatalogCameraView>,
    val analysisPlane: CatalogAnalysisPlane,
    val comparisonMode: CatalogComparisonMode,
    val phases: List<DrillPhaseTemplate>,
    val metricThresholds: Map<String, Float>,
    val animationSpec: SkeletonAnimationSpec,
)
