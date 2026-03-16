package com.inversioncoach.app.ui.results

import com.inversioncoach.app.model.AnnotatedExportStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
    fun staleProcessingStateGetsReconciled() {
        assertTrue(
            shouldReconcileStaleProcessing(
                status = AnnotatedExportStatus.PROCESSING,
                annotatedVideoUri = null,
                hasActiveExportJob = false,
            ),
        )
    }

    @Test
    fun activeProcessingJobIsNotReconciled() {
        assertFalse(
            shouldReconcileStaleProcessing(
                status = AnnotatedExportStatus.PROCESSING,
                annotatedVideoUri = null,
                hasActiveExportJob = true,
            ),
        )
    }
}
