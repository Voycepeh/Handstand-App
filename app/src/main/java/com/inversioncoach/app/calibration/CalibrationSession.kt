package com.inversioncoach.app.calibration

enum class CalibrationStep {
    FRONT_NEUTRAL,
    SIDE_NEUTRAL,
    ARMS_OVERHEAD,
    CONTROLLED_HOLD,
}

data class CalibrationCapture(
    val shoulderWidth: Float,
    val hipWidth: Float,
    val torsoLength: Float,
    val upperArmLength: Float,
    val forearmLength: Float,
    val femurLength: Float,
    val shinLength: Float,
)

class CalibrationSession {
    private val captures = linkedMapOf<CalibrationStep, CalibrationCapture>()

    fun record(step: CalibrationStep, capture: CalibrationCapture) {
        captures[step] = capture
    }

    fun canComplete(): Boolean = CalibrationStep.entries.all { captures.containsKey(it) }

    fun captures(): Map<CalibrationStep, CalibrationCapture> = captures.toMap()
}
