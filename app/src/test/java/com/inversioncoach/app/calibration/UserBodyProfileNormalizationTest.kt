package com.inversioncoach.app.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UserBodyProfileNormalizationTest {
    @Test
    fun normalizeRejectsInvalidSegmentRatios() {
        val invalid = UserBodyProfile(
            segmentRatios = SegmentRatios(
                shoulderToTorso = -1f,
                hipToShoulder = 1f,
                upperArmToTorso = 1f,
                forearmToUpperArm = 1f,
                thighToTorso = 1f,
                shinToThigh = 1f,
                armToTorso = 1f,
                legToTorso = 1f,
            ),
            symmetryMetrics = SymmetryMetrics(armSymmetry = 1f, legSymmetry = 1f, shoulderLevelBaseline = 0f, hipLevelBaseline = 0f),
        )

        assertNull(UserBodyProfile.normalize(invalid))
    }

    @Test
    fun decodeClampsConfidencesAndSymmetry() {
        val encoded = UserBodyProfile(
            overallQuality = 3f,
            frontConfidence = -1f,
            sideConfidence = 4f,
            overheadConfidence = 0.25f,
            segmentRatios = SegmentRatios(1f, 1f, 1f, 1f, 1f, 1f, 2f, 2f),
            symmetryMetrics = SymmetryMetrics(armSymmetry = 2f, legSymmetry = -1f, shoulderLevelBaseline = 0.2f, hipLevelBaseline = 0.1f),
        ).encode()

        val decoded = UserBodyProfile.decode(encoded)

        assertNotNull(decoded)
        assertEquals(1f, decoded?.overallQuality)
        assertEquals(0f, decoded?.frontConfidence)
        assertEquals(1f, decoded?.sideConfidence)
        assertEquals(0.5f, decoded?.leftRightConsistency)
    }
}

