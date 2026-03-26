package com.inversioncoach.app.calibration.storage

import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.calibration.DrillMovementProfileRepository
import com.inversioncoach.app.model.DrillType

class RoomDrillMovementProfileRepository(
    private val dao: CalibrationDao,
    private val json: DrillMovementProfileJson,
) : DrillMovementProfileRepository {

    override suspend fun get(drillType: DrillType): DrillMovementProfile? {
        return dao.get(drillType)?.let { json.decode(it.payloadJson) }
    }

    override suspend fun save(profile: DrillMovementProfile) {
        dao.upsert(
            CalibrationEntity(
                drillType = profile.drillType,
                profileVersion = profile.profileVersion,
                payloadJson = json.encode(profile),
                updatedAtMs = profile.updatedAtMs,
            ),
        )
    }
}
