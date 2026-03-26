package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

interface CalibrationProfileProvider {
    suspend fun resolve(drillType: DrillType): DrillMovementProfile
}
