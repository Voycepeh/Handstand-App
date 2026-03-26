package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

data class HoldTemplate(
    val drillType: DrillType,
    val profileVersion: Int,
    val targetDurationMs: Long,
    val alignmentTarget: Int,
    val stabilityTarget: Int,
)
