package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

data class DrillMovementProfile(
    val drillType: DrillType,
    val userBodyProfile: UserBodyProfile?,
    val holdTemplate: HoldTemplate?,
    val repTemplate: RepTemplate?,
)

interface DrillMovementProfileRepository {
    suspend fun get(drillType: DrillType): DrillMovementProfile?
    suspend fun save(profile: DrillMovementProfile)
}
