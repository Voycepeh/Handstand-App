package com.inversioncoach.app.ui.common

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsNavigationGateTest {
    @Test
    fun uploadInProgressCannotOpenResultsRoute() {
        assertFalse(baseUpload.copy(annotatedExportStatus = AnnotatedExportStatus.PROCESSING).canOpenResultsRoute())
    }

    @Test
    fun uploadFailedWithoutPlayableRawCannotOpenResultsRoute() {
        assertFalse(
            baseUpload.copy(
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                rawVideoUri = null,
            ).canOpenResultsRoute(),
        )
    }

    @Test
    fun uploadAnnotatedReadyCanOpenResultsRoute() {
        assertTrue(
            baseUpload.copy(
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_READY,
                annotatedVideoUri = "file:///annotated.mp4",
            ).canOpenResultsRoute(),
        )
    }

    @Test
    fun uploadRawOnlyTerminalCanOpenResultsRoute() {
        assertTrue(
            baseUpload.copy(
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                rawVideoUri = "file:///raw.mp4",
            ).canOpenResultsRoute(),
        )
    }

    @Test
    fun liveSessionCanAlwaysOpenResultsRoute() {
        assertTrue(baseUpload.copy(sessionSource = SessionSource.LIVE).canOpenResultsRoute())
    }

    private val baseUpload = SessionRecord(
        id = 44L,
        title = "Upload",
        drillType = DrillType.FREESTYLE,
        sessionSource = SessionSource.UPLOADED_VIDEO,
        startedAtMs = 1000L,
        completedAtMs = 2000L,
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
