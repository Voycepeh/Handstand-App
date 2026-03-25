package com.inversioncoach.app.calibration

data class HoldTemplate(
    val id: String,
    val drillName: String,
    val targetDurationMs: Long,
    val alignmentTarget: Int,
    val stabilityTarget: Int,
)
