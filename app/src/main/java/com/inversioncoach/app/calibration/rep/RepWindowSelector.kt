package com.inversioncoach.app.calibration.rep

import com.inversioncoach.app.model.PoseFrame

class RepWindowSelector {
    fun selectCleanReps(
        reps: List<List<PoseFrame>>,
        minimumFrames: Int = 10,
    ): List<List<PoseFrame>> {
        return reps.filter { it.size >= minimumFrames }
    }
}
