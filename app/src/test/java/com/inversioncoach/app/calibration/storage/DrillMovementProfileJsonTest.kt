package com.inversioncoach.app.calibration.storage

import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.calibration.SegmentRatios
import com.inversioncoach.app.calibration.SymmetryMetrics
import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DrillMovementProfileJsonTest {
    private val codec = DrillMovementProfileJson()

    @Test
    fun roundTripPreservesBodyProfileMetadata() {
        val profile = DrillMovementProfile(
            drillType = DrillType.FREE_HANDSTAND,
            profileVersion = 3,
            userBodyProfile = UserBodyProfile(
                id = "body_1",
                name = "Athlete",
                overallQuality = 0.92f,
                frontConfidence = 0.8f,
                sideConfidence = 0.9f,
                overheadConfidence = 0.95f,
                segmentRatios = SegmentRatios(1.1f, 0.9f, 0.8f, 0.95f, 1.0f, 1.1f, 1.56f, 2.1f),
                symmetryMetrics = SymmetryMetrics(0.7f, 0.8f, 0.02f, 0.01f),
            ),
            createdAtMs = 100L,
            updatedAtMs = 200L,
        )

        val restored = codec.decode(codec.encode(profile))

        assertNotNull(restored.userBodyProfile)
        assertEquals("body_1", restored.userBodyProfile?.id)
        assertEquals("Athlete", restored.userBodyProfile?.name)
        assertEquals(0.92f, restored.userBodyProfile?.overallQuality)
    }
}

