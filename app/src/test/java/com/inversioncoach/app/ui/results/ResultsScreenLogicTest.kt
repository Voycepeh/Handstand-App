package com.inversioncoach.app.ui.results

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsScreenLogicTest {
    @Test
    fun hidesRawButtonWhenPrimaryReplayAlreadyUsesRawVideo() {
        assertFalse(
            shouldShowRawVideoButton(
                replayUri = "file:///sessions/1/raw.mp4",
                rawUri = "file:///sessions/1/raw.mp4",
            ),
        )
    }

    @Test
    fun showsRawButtonWhenAnnotatedReplayAndRawVideoBothExist() {
        assertTrue(
            shouldShowRawVideoButton(
                replayUri = "file:///sessions/1/annotated.mp4",
                rawUri = "file:///sessions/1/raw.mp4",
            ),
        )
    }

    @Test
    fun hidesRawButtonWhenRawVideoMissing() {
        assertFalse(
            shouldShowRawVideoButton(
                replayUri = "file:///sessions/1/annotated.mp4",
                rawUri = null,
            ),
        )
    }

    @Test
    fun replayBadgeShowsRawOnlyWhenPrimaryReplayIsRaw() {
        assertEquals("Raw Only", replayAvailabilityBadge("Raw replay"))
    }

    @Test
    fun elapsedFormattingHandlesMissingValue() {
        assertEquals("0s", formatElapsedDuration(null))
    }

    @Test
    fun displayDurationPrefersAnnotatedWhenAvailable() {
        assertEquals(
            5_000L,
            pickDisplayDurationMs(
                annotatedDurationMs = 5_000L,
                rawDurationMs = 4_000L,
                sessionDurationMs = 10_000L,
            ),
        )
    }

    @Test
    fun displayDurationFallsBackToRawWhenAnnotatedMissing() {
        assertEquals(
            4_000L,
            pickDisplayDurationMs(
                annotatedDurationMs = null,
                rawDurationMs = 4_000L,
                sessionDurationMs = 10_000L,
            ),
        )
    }

    @Test
    fun displayDurationFallsBackToSessionWhenVideoDurationsMissing() {
        assertEquals(
            10_000L,
            pickDisplayDurationMs(
                annotatedDurationMs = null,
                rawDurationMs = null,
                sessionDurationMs = 10_000L,
            ),
        )
    }

}
