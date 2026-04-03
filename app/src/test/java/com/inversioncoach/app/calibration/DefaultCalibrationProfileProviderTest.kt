package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultCalibrationProfileProviderTest {
    @Test
    fun returnsStoredProfileWhenPresent() = runTest {
        val repo = FakeDrillMovementProfileRepository()
        val expected = DefaultDrillMovementProfiles.forDrill(DrillType.FREE_HANDSTAND, nowMs = 111L)
        repo.save(expected)

        val resolved = DefaultCalibrationProfileProvider(
            repository = repo,
            resolveRuntimeBodyProfile = { RuntimeBodyProfileResolution(null, null, null, null, true) },
        ).resolve(DrillType.FREE_HANDSTAND)

        assertEquals(expected, resolved)
    }

    @Test
    fun returnsDefaultProfileWhenMissing() = runTest {
        val repo = FakeDrillMovementProfileRepository()

        val resolved = DefaultCalibrationProfileProvider(
            repository = repo,
            resolveRuntimeBodyProfile = { RuntimeBodyProfileResolution(null, null, null, null, true) },
        ).resolve(DrillType.WALL_HANDSTAND_PUSH_UP)

        assertEquals(DrillType.WALL_HANDSTAND_PUSH_UP, resolved.drillType)
        assertEquals(1, resolved.profileVersion)
        assertNull(repo.get(DrillType.WALL_HANDSTAND_PUSH_UP))
    }

    private class FakeDrillMovementProfileRepository : DrillMovementProfileRepository {
        private val profiles = mutableMapOf<DrillType, DrillMovementProfile>()

        override suspend fun get(drillType: DrillType): DrillMovementProfile? = profiles[drillType]

        override suspend fun save(profile: DrillMovementProfile) {
            profiles[profile.drillType] = profile
        }

        override suspend fun clear(drillType: DrillType) {
            profiles.remove(drillType)
        }
    }
}
