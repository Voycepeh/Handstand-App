package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

data class RepTemplate(
    val drillType: DrillType,
    val profileVersion: Int,
    val targetRepCount: Int?,
    val depthTarget: Float?,
    val tempoSeconds: Float?,
)
