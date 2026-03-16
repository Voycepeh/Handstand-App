package com.inversioncoach.app.ui.results

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
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
    fun staleProcessingWithoutActiveJobIsReconciled() {
        val session = baseSession().copy(
            annotatedExportStatus = AnnotatedExportStatus.PROCESSING,
            annotatedVideoUri = null,
        )

        assertTrue(shouldReconcileStaleProcessingState(session, hasActiveExportJob = false))
    }

    @Test
    fun processingWithActiveJobIsNotReconciled() {
        val session = baseSession().copy(
            annotatedExportStatus = AnnotatedExportStatus.PROCESSING,
            annotatedVideoUri = null,
        )

        assertFalse(shouldReconcileStaleProcessingState(session, hasActiveExportJob = true))
    }


    private fun baseSession() = SessionRecord(
        id = 1L,
        title = "Session",
        drillType = DrillType.CHEST_TO_WALL_HANDSTAND,
        startedAtMs = 1L,
        completedAtMs = 2L,
        overallScore = 0,
        strongestArea = "-",
        limitingFactor = "-",
        issues = "",
        wins = "",
        metricsJson = "",
        annotatedVideoUri = null,
        rawVideoUri = null,
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "",
    )
}
