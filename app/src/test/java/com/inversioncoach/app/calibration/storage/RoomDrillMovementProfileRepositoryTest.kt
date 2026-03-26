package com.inversioncoach.app.calibration.storage

import com.inversioncoach.app.calibration.DefaultDrillMovementProfiles
import com.inversioncoach.app.model.DrillType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RoomDrillMovementProfileRepositoryTest {
    @Test
    fun saveAndGetProfileRoundTrips() = runTest {
        val dao = FakeCalibrationDao()
        val repository = RoomDrillMovementProfileRepository(dao, DrillMovementProfileJson())
        val profile = DefaultDrillMovementProfiles.forDrill(DrillType.FREE_HANDSTAND, nowMs = 1234L)

        repository.save(profile)
        val restored = repository.get(DrillType.FREE_HANDSTAND)

        assertNotNull(restored)
        assertEquals(profile, restored)
    }

    @Test
    fun unknownDrillReturnsNull() = runTest {
        val repository = RoomDrillMovementProfileRepository(FakeCalibrationDao(), DrillMovementProfileJson())

        val restored = repository.get(DrillType.WALL_HANDSTAND)

        assertNull(restored)
    }

    private class FakeCalibrationDao : CalibrationDao {
        private val store = mutableMapOf<DrillType, CalibrationEntity>()

        override suspend fun get(drillType: DrillType): CalibrationEntity? = store[drillType]

        override suspend fun upsert(entity: CalibrationEntity) {
            store[entity.drillType] = entity
        }
    }
}
