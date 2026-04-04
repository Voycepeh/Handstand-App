package com.inversioncoach.app.ui.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun overlayCoverageDoesNotDegradeModerateAcceptedFrames() {
        val diagnostics = assessOverlayCoverage(
            sourceDurationMs = 10_000L,
            acceptedOverlayCount = 11,
            firstOverlayTimestampMs = 500L,
            lastOverlayTimestampMs = 8_500L,
        )

        assertFalse(diagnostics.isDegraded)
    }

    @Test
    fun elevenAcceptedFramesAtPointSixtySevenDensityIsNotDegraded() {
        val diagnostics = assessOverlayCoverage(
            sourceDurationMs = 16_418L,
            acceptedOverlayCount = 11,
            firstOverlayTimestampMs = 1_000L,
            lastOverlayTimestampMs = 9_000L,
        )

        assertFalse(diagnostics.isDegraded)
    }

    @Test
    fun longUploadsUseLowerAnalysisSamplingFps() {
        val fps = resolveUploadAnalysisSampleFps(
            sourceDurationMs = 200_000L,
            sourceFrameRate = 30,
        )

        assertEquals(3, fps)
    }

    @Test
    fun samplingPolicyCapsCandidateDecodeRate() {
        val policy = resolveUploadAnalysisSamplingPolicy(
            sourceDurationMs = 20_000L,
            sourceFrameRate = 60,
        )

        assertEquals(6, policy.analysisFps)
        assertEquals(8, policy.candidateDecodeFps)
    }

    @Test
    fun degradedOverlaySelectsRawReplay() {
        val selected = selectReplayUriForUpload(
            shouldTreatAsRawOnly = true,
            annotatedUri = "file:///annotated.mp4",
            rawUri = "file:///raw.mp4",
        )

        assertEquals("file:///raw.mp4", selected)
    }

    @Test
    fun replaySelectionHandlesMissingUris() {
        assertEquals(
            "file:///raw.mp4",
            selectReplayUriForUpload(
                shouldTreatAsRawOnly = false,
                annotatedUri = null,
                rawUri = "file:///raw.mp4",
            ),
        )
        assertNull(
            selectReplayUriForUpload(
                shouldTreatAsRawOnly = true,
                annotatedUri = "file:///annotated.mp4",
                rawUri = null,
            ),
        )
    }
}
