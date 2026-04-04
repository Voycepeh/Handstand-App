package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadJobReconcilePolicyTest {

    @Test
    fun reconcileDoesNotStallWhenActiveBackgroundWorkerExists() {
        assertFalse(
            shouldMarkUploadJobStalled(
                hasActiveWorker = true,
                isHeartbeatStale = true,
            ),
        )
    }

    @Test
    fun reconcileStallsWhenNoWorkerAndHeartbeatIsStale() {
        assertTrue(
            shouldMarkUploadJobStalled(
                hasActiveWorker = false,
                isHeartbeatStale = true,
            ),
        )
    }

    @Test
    fun uploadProgressRefreshesHeartbeatTimestamp() {
        val now = 123_456L
        val updated = applyUploadPipelineProgress(
            session = baseSession,
            stageLabel = "Analyzing",
            processedFrames = 10,
            totalFrames = 20,
            timestampMs = 1_000L,
            detail = "running",
            now = now,
        )

        assertEquals(now, updated.uploadJobUpdatedAtMs)
        assertEquals(now, updated.uploadJobHeartbeatAtMs)
        assertEquals("Analyzing", updated.uploadPipelineStageLabel)
        assertEquals(10, updated.uploadAnalysisProcessedFrames)
    }

    private val baseSession = SessionRecord(
        id = 99L,
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
        rawPersistStatus = RawPersistStatus.SUCCEEDED,
        annotatedExportStatus = AnnotatedExportStatus.PROCESSING,
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "",
    )
}
