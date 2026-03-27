package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

data class TemporalMetricProfile(
    val metricKey: String,
    val meanSeries: List<Float>,
    val toleranceSeries: List<Float>,
)

enum class RepTemplateSource {
    DEFAULT_BASELINE,
    CLEAN_REP_CAPTURE,
    BLENDED,
}

data class RepTemplate(
    val drillType: DrillType,
    val profileVersion: Int,
    val temporalMetrics: List<TemporalMetricProfile>,
    val expectedRepFrames: Int,
    val minRomThreshold: Float?,
    val source: RepTemplateSource,
)
