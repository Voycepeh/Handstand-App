package com.inversioncoach.app.ui.common

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDisplayUploadTest {

    @Test
    fun uploadedSessionWithoutMetricsShowsProcessingMessage() {
        val text = formatPrimaryPerformance(
            baseUploadSession.copy(
                annotatedExportStatus = AnnotatedExportStatus.PROCESSING,
                metricsJson = "{}",
            ),
        )

        assertEquals("Upload analysis is still processing.", text)
    }

    @Test
    fun uploadedSessionFailureWithoutMetricsShowsTruthfulFailureMessage() {
        val text = formatPrimaryPerformance(
            baseUploadSession.copy(
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                metricsJson = "{}",
            ),
        )

        assertEquals("Upload analysis did not produce scored attempts.", text)
    }

    @Test
    fun uploadedSessionWithHoldMetricsUsesPersistedDuration() {
        val text = formatPrimaryPerformance(
            baseUploadSession.copy(
                metricsJson = "trackingMode:HOLD_BASED|alignedDurationMs:12000|bestAlignedStreakMs:7000|sessionTrackedMs:14812|alignmentRate:0.81|avgStability:74",
            ),
        )

        assertEquals("Hold: 00:12 aligned • Best streak: 00:07 • Session: 00:14 • Align 81% • Stability 74", text)
    }

    @Test
    fun uploadedSessionWithPartialHoldMetricsDoesNotRenderFakeHoldDefaults() {
        val text = formatPrimaryPerformance(
            baseUploadSession.copy(
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                metricsJson = "trackingMode:HOLD_BASED|sessionTrackedMs:14812",
            ),
        )

        assertEquals("Upload analysis did not produce scored attempts.", text)
    }

    @Test
    fun uploadedSessionWithRepMetricsUsesRepSummaryBranch() {
        val text = formatPrimaryPerformance(
            baseUploadSession.copy(
                metricsJson = "trackingMode:REP_BASED|acceptedReps:4|rawRepAttempts:6|rejectedReps:2|avgRepScore:81|bestRepScore:93",
            ),
        )

        assertEquals("Reps: 4 accepted / 6 attempts • Rejected: 2 • Avg rep 81", text)
    }

    @Test
    fun uploadedRepBasedSessionMissingRepMetricsShowsTruthfulFailureMessage() {
        val text = formatPrimaryPerformance(
            baseUploadSession.copy(
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                metricsJson = "trackingMode:REP_BASED|acceptedReps:1",
            ),
        )

        assertEquals("Upload analysis did not produce scored attempts.", text)
    }

    private val baseUploadSession = SessionRecord(
        id = 77L,
        title = "Uploaded Video Analysis",
        drillType = DrillType.FREESTYLE,
        sessionSource = SessionSource.UPLOADED_VIDEO,
        startedAtMs = 1_000L,
        completedAtMs = 15_812L,
        overallScore = 0,
        strongestArea = "",
        limitingFactor = "",
        issues = "",
        wins = "",
        metricsJson = "{}",
        annotatedVideoUri = null,
        rawVideoUri = "file:///raw.mp4",
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "",
    )
}
