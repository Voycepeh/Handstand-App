package com.inversioncoach.app.ui.history

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryScreenStatusTest {

    @Test
    fun rawReadyAnnotatedProcessingShowsAccurateStatus() {
        val session = baseSession.copy(
            rawPersistStatus = RawPersistStatus.SUCCEEDED,
            annotatedExportStatus = AnnotatedExportStatus.PROCESSING,
            rawVideoUri = "file:///raw.mp4",
        )

        assertEquals("Raw replay ready • Annotated replay processing", videoStatus(session))
        assertEquals(0.7f, uploadProgress(session))
    }

    @Test
    fun rawReadyAnnotatedFailedShowsFailureStatus() {
        val session = baseSession.copy(
            rawPersistStatus = RawPersistStatus.SUCCEEDED,
            annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
            rawVideoUri = "file:///raw.mp4",
        )

        assertEquals("Raw replay ready • Annotated replay failed", videoStatus(session))
        assertEquals(1f, uploadProgress(session))
    }

    private val baseSession = SessionRecord(
        id = 1L,
        title = "Uploaded Video Analysis",
        drillType = DrillType.FREESTYLE,
        sessionSource = SessionSource.UPLOADED_VIDEO,
        startedAtMs = 1L,
        completedAtMs = 1L,
        overallScore = 0,
        strongestArea = "",
        limitingFactor = "",
        issues = "",
        wins = "",
        metricsJson = "{}",
        annotatedVideoUri = null,
        rawVideoUri = null,
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "",
    )
}
