package com.inversioncoach.app.ui.upload

import com.inversioncoach.app.model.AnnotatedExportStage
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadStageDerivationTest {

    @Test
    fun processingStagesMapToTruthfulUploadStages() {
        assertEquals(UploadStage.PREPARING_ANALYSIS, deriveUploadStage(base.copy(rawPersistStatus = RawPersistStatus.SUCCEEDED, annotatedExportStatus = AnnotatedExportStatus.PROCESSING, annotatedExportStage = AnnotatedExportStage.PREPARING)))
        assertEquals(UploadStage.ANALYZING_VIDEO, deriveUploadStage(base.copy(rawPersistStatus = RawPersistStatus.SUCCEEDED, annotatedExportStatus = AnnotatedExportStatus.PROCESSING, annotatedExportStage = AnnotatedExportStage.DECODING_SOURCE)))
        assertEquals(UploadStage.RENDERING_OVERLAY, deriveUploadStage(base.copy(rawPersistStatus = RawPersistStatus.SUCCEEDED, annotatedExportStatus = AnnotatedExportStatus.PROCESSING, annotatedExportStage = AnnotatedExportStage.LOADING_OVERLAYS)))
        assertEquals(UploadStage.EXPORTING_ANNOTATED_VIDEO, deriveUploadStage(base.copy(rawPersistStatus = RawPersistStatus.SUCCEEDED, annotatedExportStatus = AnnotatedExportStatus.PROCESSING, annotatedExportStage = AnnotatedExportStage.ENCODING)))
        assertEquals(UploadStage.VERIFYING_OUTPUT, deriveUploadStage(base.copy(rawPersistStatus = RawPersistStatus.SUCCEEDED, annotatedExportStatus = AnnotatedExportStatus.PROCESSING, annotatedExportStage = AnnotatedExportStage.VERIFYING)))
    }

    @Test
    fun failedAnnotatedWithRawReadyMapsToRawOnlyTerminal() {
        val stage = deriveUploadStage(
            base.copy(
                rawPersistStatus = RawPersistStatus.SUCCEEDED,
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
            ),
        )
        assertEquals(UploadStage.COMPLETED_RAW_ONLY, stage)
    }

    private val base = SessionRecord(
        id = 15L,
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
