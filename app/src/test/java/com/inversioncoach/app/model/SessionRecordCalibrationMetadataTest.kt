package com.inversioncoach.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRecordCalibrationMetadataTest {
    @Test
    fun sessionRecordStoresCalibrationMetadata() {
        val baseline = SessionRecord(
            title = "Session",
            drillType = DrillType.FREE_HANDSTAND,
            startedAtMs = 1L,
            completedAtMs = 2L,
            overallScore = 90,
            strongestArea = "Alignment",
            limitingFactor = "Stability",
            issues = "",
            wins = "",
            metricsJson = "",
            annotatedVideoUri = null,
            rawVideoUri = null,
            notesUri = null,
            bestFrameTimestampMs = null,
            worstFrameTimestampMs = null,
            topImprovementFocus = "Maintain line",
        )

        assertNull(baseline.calibrationProfileVersion)
        assertNull(baseline.calibrationUpdatedAtMs)

        val withCalibration = baseline.copy(
            calibrationProfileVersion = 3,
            calibrationUpdatedAtMs = 1700L,
        )

        assertEquals(3, withCalibration.calibrationProfileVersion)
        assertEquals(1700L, withCalibration.calibrationUpdatedAtMs)
    }
}
