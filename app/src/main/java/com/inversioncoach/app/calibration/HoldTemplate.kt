package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

data class HoldMetricTemplate(
    val metricKey: String,
    val targetValue: Float,
    val goodTolerance: Float,
    val warnTolerance: Float,
    val weight: Float,
)

enum class HoldTemplateSource {
    DEFAULT_BASELINE,
    STRUCTURAL_CALIBRATION,
    STABLE_HOLD_CAPTURE,
    BLENDED,
}

data class HoldTemplate(
    val drillType: DrillType,
    val profileVersion: Int,
    val metrics: List<HoldMetricTemplate>,
    val minStableDurationMs: Long,
    val source: HoldTemplateSource,
)
