package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

class DefaultCalibrationProfileProvider(
    private val repository: DrillMovementProfileRepository,
) : CalibrationProfileProvider {

    override suspend fun resolve(drillType: DrillType): DrillMovementProfile {
        return repository.get(drillType) ?: DefaultDrillMovementProfiles.forDrill(drillType)
    }
}
