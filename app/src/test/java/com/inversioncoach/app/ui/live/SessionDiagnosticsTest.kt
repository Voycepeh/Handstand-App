package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDiagnosticsTest {

    @Test
    fun eventsAreAppendedInOrder() {
        val sessionId = 99001L
        SessionDiagnostics.clearSession(sessionId)
        SessionDiagnostics.record(sessionId, SessionDiagnostics.Stage.SESSION_START, SessionDiagnostics.Status.STARTED, "start")
        SessionDiagnostics.record(sessionId, SessionDiagnostics.Stage.RAW_PERSIST, SessionDiagnostics.Status.SUCCEEDED, "raw ok")

        val events = SessionDiagnostics.eventsForSession(sessionId)

        assertEquals(2, events.size)
        assertTrue(events[0].timestampMs <= events[1].timestampMs)
        assertEquals(SessionDiagnostics.Stage.SESSION_START, events[0].stage)
        assertEquals(SessionDiagnostics.Stage.RAW_PERSIST, events[1].stage)
    }

    @Test
    fun reportContainsCriticalFieldsAndFailureCode() {
        val sessionId = 99002L
        SessionDiagnostics.clearSession(sessionId)
        SessionDiagnostics.record(
            sessionId,
            SessionDiagnostics.Stage.ANNOTATED_EXPORT_VERIFY,
            SessionDiagnostics.Status.FAILED,
            "verify failed",
            errorCode = "OUTPUT_URI_NULL",
        )
        val session = sampleSession(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED, null, "file:///raw.mp4", "OUTPUT_URI_NULL")

        val report = SessionDiagnostics.buildReport(session, sessionId)

        assertTrue(report.contains("sessionId: $sessionId"))
        assertTrue(report.contains("annotatedExportFailureReason: OUTPUT_URI_NULL"))
        assertTrue(report.contains("OUTPUT_URI_NULL"))
        assertTrue(report.contains("# Event timeline"))
    }

    @Test
    fun rootCauseSummarySurfacesReplayFallbackReason() {
        val sessionId = 99003L
        SessionDiagnostics.clearSession(sessionId)
        SessionDiagnostics.record(
            sessionId,
            SessionDiagnostics.Stage.FALLBACK_DECISION,
            SessionDiagnostics.Status.FALLBACK,
            "fallback",
            errorCode = "REPLAY_SELECTION_FELL_BACK_TO_RAW",
        )

        val summary = SessionDiagnostics.rootCauseSummary(
            sampleSession(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED, null, "file:///raw.mp4", "REPLAY_SELECTION_FELL_BACK_TO_RAW"),
            SessionDiagnostics.eventsForSession(sessionId),
        )

        assertTrue(summary.contains("REPLAY_SELECTION_FELL_BACK_TO_RAW") || summary.contains("fell back to raw"))
    }


    @Test
    fun structuredProgressEventWithReasonDoesNotBecomeFinalizeFailure() {
        val sessionId = 99005L
        SessionDiagnostics.clearSession(sessionId)

        SessionDiagnostics.logStructured(
            event = "replay_source_select_temp_raw_while_processing",
            sessionId = sessionId,
            drillType = DrillType.WALL_HANDSTAND,
            rawUri = "file:///raw.mp4",
            annotatedUri = null,
            overlayFrameCount = 93,
            failureReason = "annotatedExportStatus=PROCESSING",
        )

        val event = SessionDiagnostics.eventsForSession(sessionId).single()
        assertEquals(SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT, event.stage)
        assertEquals(SessionDiagnostics.Status.PROGRESS, event.status)
    }

    @Test
    fun rootCauseSummaryExposesNullAnnotatedUriWhenReady() {
        val sessionId = 99004L
        SessionDiagnostics.clearSession(sessionId)
        val summary = SessionDiagnostics.rootCauseSummary(
            sampleSession(sessionId, AnnotatedExportStatus.ANNOTATED_READY, null, "file:///raw.mp4", null),
            emptyList(),
        )

        assertTrue(summary.contains("annotatedVideoUri was null"))
    }


    @Test
    fun uploadRootCauseFlagsAnalysisNeverStarted() {
        val sessionId = 99006L
        SessionDiagnostics.clearSession(sessionId)
        SessionDiagnostics.record(
            sessionId,
            SessionDiagnostics.Stage.SESSION_START,
            SessionDiagnostics.Status.STARTED,
            "upload session started",
        )

        val summary = SessionDiagnostics.rootCauseSummary(
            sampleSession(sessionId, AnnotatedExportStatus.NOT_STARTED, null, "file:///raw.mp4", null).copy(sessionSource = SessionSource.UPLOADED_VIDEO),
            SessionDiagnostics.eventsForSession(sessionId),
        )

        assertTrue(summary.contains("never started"))
    }

    @Test
    fun uploadRootCauseFlagsExportNeverLaunched() {
        val sessionId = 99007L
        SessionDiagnostics.clearSession(sessionId)
        SessionDiagnostics.record(
            sessionId,
            SessionDiagnostics.Stage.TIMESTAMP_ALIGNMENT,
            SessionDiagnostics.Status.SUCCEEDED,
            "overlay timeline extracted",
        )

        val summary = SessionDiagnostics.rootCauseSummary(
            sampleSession(sessionId, AnnotatedExportStatus.NOT_STARTED, null, "file:///raw.mp4", null).copy(sessionSource = SessionSource.UPLOADED_VIDEO),
            SessionDiagnostics.eventsForSession(sessionId),
        )

        assertTrue(summary.contains("never launched"))
    }

    @Test
    fun uploadRootCauseMarksAnnotatedReadySuccess() {
        val sessionId = 99008L
        SessionDiagnostics.clearSession(sessionId)
        val summary = SessionDiagnostics.rootCauseSummary(
            sampleSession(sessionId, AnnotatedExportStatus.ANNOTATED_READY, "file:///annotated.mp4", "file:///raw.mp4", null)
                .copy(sessionSource = SessionSource.UPLOADED_VIDEO),
            emptyList(),
        )

        assertTrue(summary.contains("ready"))
    }

    @Test
    fun uploadRootCauseShowsInProgressWithEvidence() {
        val sessionId = 99009L
        SessionDiagnostics.clearSession(sessionId)
        SessionDiagnostics.record(
            sessionId,
            SessionDiagnostics.Stage.RAW_PERSIST,
            SessionDiagnostics.Status.SUCCEEDED,
            "raw persisted",
        )

        val summary = SessionDiagnostics.rootCauseSummary(
            sampleSession(sessionId, AnnotatedExportStatus.PROCESSING, null, "file:///raw.mp4", null)
                .copy(sessionSource = SessionSource.UPLOADED_VIDEO),
            SessionDiagnostics.eventsForSession(sessionId),
        )

        assertTrue(summary.contains("in progress"))
    }

    private fun sampleSession(
        sessionId: Long,
        status: AnnotatedExportStatus,
        annotatedUri: String?,
        rawUri: String?,
        failureReason: String?,
    ): SessionRecord = SessionRecord(
        id = sessionId,
        title = "Session",
        drillType = DrillType.WALL_HANDSTAND,
        startedAtMs = 1000L,
        completedAtMs = 2000L,
        overallScore = 70,
        strongestArea = "line",
        limitingFactor = "hips",
        issues = "",
        wins = "",
        metricsJson = "",
        annotatedVideoUri = annotatedUri,
        rawVideoUri = rawUri,
        annotatedExportStatus = status,
        annotatedExportFailureReason = failureReason,
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "focus",
    )
}
