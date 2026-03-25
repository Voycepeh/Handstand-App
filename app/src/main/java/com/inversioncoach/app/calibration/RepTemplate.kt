package com.inversioncoach.app.calibration

data class RepTemplate(
    val id: String,
    val drillName: String,
    val targetRepCount: Int,
    val depthTarget: Float,
    val tempoSeconds: Float,
)
