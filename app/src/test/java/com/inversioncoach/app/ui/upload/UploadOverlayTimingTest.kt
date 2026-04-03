package com.inversioncoach.app.ui.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadOverlayTimingTest {
    @Test
    fun usesAverageDeltaFromImportedTimestamps() {
        val interval = estimateTimelineSampleIntervalMs(
            timestampsMs = listOf(0L, 120L, 250L, 380L),
            fallbackFps = 6,
        )

        assertEquals(126L, interval)
    }

    @Test
    fun fallsBackToConfiguredSamplingFpsForShortTimeline() {
        val interval = estimateTimelineSampleIntervalMs(
            timestampsMs = listOf(100L),
            fallbackFps = 5,
        )

        assertEquals(200L, interval)
    }
}
