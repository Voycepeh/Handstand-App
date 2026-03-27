package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

data class CalibrationSession(
    val drillType: DrillType,
    val captures: MutableMap<CalibrationStep, CalibrationCapture> = mutableMapOf(),
) {
    fun record(capture: CalibrationCapture) {
        captures[capture.step] = capture
    }

    fun get(step: CalibrationStep): CalibrationCapture? = captures[step]
}
