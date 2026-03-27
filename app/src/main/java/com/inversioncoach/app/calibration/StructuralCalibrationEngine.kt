package com.inversioncoach.app.calibration

class StructuralCalibrationEngine(
    private val builder: UserBodyProfileBuilder = UserBodyProfileBuilder(),
) {
    fun buildProfile(session: CalibrationSession): UserBodyProfile? {
        val front = session.get(CalibrationStep.FRONT_NEUTRAL)
        val side = session.get(CalibrationStep.SIDE_NEUTRAL)
        val overhead = session.get(CalibrationStep.ARMS_OVERHEAD)

        if (front == null || side == null || overhead == null) return null

        return builder.build(
            frontFrames = front.acceptedFrames,
            sideFrames = side.acceptedFrames,
            overheadFrames = overhead.acceptedFrames,
            holdFrames = session.get(CalibrationStep.CONTROLLED_HOLD)?.acceptedFrames.orEmpty(),
        )
    }
}
