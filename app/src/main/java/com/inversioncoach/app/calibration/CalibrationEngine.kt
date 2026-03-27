package com.inversioncoach.app.calibration

class CalibrationEngine(
    private val structuralCalibrationEngine: StructuralCalibrationEngine = StructuralCalibrationEngine(),
) {
    fun buildProfile(session: CalibrationSession): UserBodyProfile? = structuralCalibrationEngine.buildProfile(session)
}
