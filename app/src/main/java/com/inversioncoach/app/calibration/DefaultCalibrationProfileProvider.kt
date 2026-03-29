package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

class DefaultCalibrationProfileProvider(
    private val repository: DrillMovementProfileRepository,
    private val runtimeBodyProfileResolver: RuntimeBodyProfileResolver,
) : CalibrationProfileProvider {

    override suspend fun resolve(drillType: DrillType): DrillMovementProfile {
        val baseProfile = repository.get(drillType) ?: DefaultDrillMovementProfiles.forDrill(drillType)
        val activeBodyProfile = runtimeBodyProfileResolver.resolve().bodyProfile
        return baseProfile.copy(userBodyProfile = activeBodyProfile)
    }

    override suspend fun save(profile: DrillMovementProfile) {
        repository.save(profile)
    }
}
