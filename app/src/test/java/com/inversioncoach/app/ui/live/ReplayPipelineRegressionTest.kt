package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.ui.results.replayAvailabilityBadge
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReplayPipelineRegressionTest {

    @Test
    fun rawSavedButAnnotatedUnreadableFallsBackToRawAndShowsRawOnly() {
        val rawFile = File.createTempFile("raw", ".mp4")
        try {
            rawFile.writeText("raw")
            val unreadableAnnotated = File(rawFile.parentFile, "annotated_missing_${System.nanoTime()}.mp4")
            val session = SessionRecord(
                id = 42,
                title = "regression",
                drillType = DrillType.WALL_HANDSTAND,
                startedAtMs = 1L,
                completedAtMs = 2L,
                overallScore = 0,
                strongestArea = "-",
                limitingFactor = "-",
                issues = "",
                wins = "",
                metricsJson = "",
                annotatedVideoUri = unreadableAnnotated.toURI().toString(),
                rawVideoUri = rawFile.toURI().toString(),
                rawPersistStatus = RawPersistStatus.SUCCEEDED,
                rawPersistFailureReason = null,
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                annotatedExportFailureReason = "ANNOTATED_EXPORT_FAILED:METADATA_UNREADABLE",
                overlayFrameCount = 120,
                notesUri = null,
                bestFrameTimestampMs = null,
                worstFrameTimestampMs = null,
                topImprovementFocus = "",
            )

            val preferred = resolvePreferredReplayUri(session)
            val selection = selectReplayAsset(session)

            assertEquals("raw", preferred.source)
            assertEquals(rawFile.toURI().toString(), preferred.uri)
            assertEquals("Raw replay", selection.label)
            assertEquals("Raw Only", replayAvailabilityBadge(selection.label))
            assertEquals("ANNOTATED_EXPORT_FAILED:METADATA_UNREADABLE", session.annotatedExportFailureReason)
        } finally {
            rawFile.delete()
        }
    }

    @Test
    fun postSaveExportCompletionSwitchesVisibleReplayFromRawToAnnotated() {
        val rawFile = File.createTempFile("raw_post_save", ".mp4")
        val annotatedFile = File.createTempFile("annotated_post_save", ".mp4")
        try {
            rawFile.writeText("raw")
            annotatedFile.writeText("annotated")

            val processingSession = SessionRecord(
                id = 88,
                title = "processing",
                drillType = DrillType.WALL_HANDSTAND,
                startedAtMs = 1L,
                completedAtMs = 2L,
                overallScore = 0,
                strongestArea = "-",
                limitingFactor = "-",
                issues = "",
                wins = "",
                metricsJson = "",
                rawVideoUri = rawFile.toURI().toString(),
                bestPlayableUri = rawFile.toURI().toString(),
                rawPersistStatus = RawPersistStatus.SUCCEEDED,
                annotatedExportStatus = AnnotatedExportStatus.PROCESSING,
                overlayFrameCount = 93,
                notesUri = null,
                bestFrameTimestampMs = null,
                worstFrameTimestampMs = null,
                topImprovementFocus = "",
            )

            val processingSelection = selectReplayAsset(processingSession)
            assertEquals("Raw replay", processingSelection.label)
            assertEquals(rawFile.toURI().toString(), processingSelection.uri)

            val readySession = processingSession.copy(
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_READY,
                annotatedVideoUri = annotatedFile.toURI().toString(),
                annotatedFinalUri = annotatedFile.toURI().toString(),
                bestPlayableUri = annotatedFile.toURI().toString(),
            )

            val readySelection = selectReplayAsset(readySession)
            assertEquals("Annotated replay", readySelection.label)
            assertEquals(annotatedFile.toURI().toString(), readySelection.uri)
        } finally {
            rawFile.delete()
            annotatedFile.delete()
        }
    }

}
