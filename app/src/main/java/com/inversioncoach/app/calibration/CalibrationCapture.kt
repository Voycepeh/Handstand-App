package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.PoseFrame

data class CalibrationCapture(
    val step: CalibrationStep,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val acceptedFrames: List<PoseFrame>,
    val rejectedFrameCount: Int,
)
