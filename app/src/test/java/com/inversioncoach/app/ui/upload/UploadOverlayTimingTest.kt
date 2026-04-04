package com.inversioncoach.app.ui.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun overlayCoverageMarksDegradedForSparseTimelines() {
        val diagnostics = assessOverlayCoverage(
            sourceDurationMs = 10_000L,
            acceptedOverlayCount = 4,
            firstOverlayTimestampMs = 0L,
            lastOverlayTimestampMs = 2_000L,
        )

        assertTrue(diagnostics.isDegraded)
    }

    @Test
    fun longUploadsUseLowerAnalysisSamplingFps() {
        val fps = resolveUploadAnalysisSampleFps(
            sourceDurationMs = 200_000L,
            sourceFrameRate = 30,
        )

        assertEquals(3, fps)
    }
}
