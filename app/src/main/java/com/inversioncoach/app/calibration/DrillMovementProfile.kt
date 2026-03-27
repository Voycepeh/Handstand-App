package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

data class DrillMovementProfile(
    val drillType: DrillType,
    val profileVersion: Int,
    val userBodyProfile: UserBodyProfile?,
    val holdTemplate: HoldTemplate?,
    val repTemplate: RepTemplate?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

interface DrillMovementProfileRepository {
    suspend fun get(drillType: DrillType): DrillMovementProfile?
    suspend fun save(profile: DrillMovementProfile)
    suspend fun clear(drillType: DrillType)
}
