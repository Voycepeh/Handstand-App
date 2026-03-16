package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ReplayAssetSelectionTest {

    @Test
    fun prefersAnnotatedReplayWhenBothAssetsExist() {
        val rawFile = File.createTempFile("raw", ".mp4")
        val annotatedFile = File.createTempFile("annotated", ".mp4")
        try {
            val session = sessionRecord(
                rawUri = rawFile.toURI().toString(),
                annotatedUri = annotatedFile.toURI().toString(),
            )

            val selection = selectReplayAsset(session)

            assertEquals("Annotated replay", selection.label)
            assertEquals(annotatedFile.toURI().toString(), selection.uri)
        } finally {
            rawFile.delete()
            annotatedFile.delete()
        }
    }

    @Test
    fun fallsBackToRawReplayWhenAnnotatedIsMissing() {
        val rawFile = File.createTempFile("raw", ".mp4")
        val missingAnnotated = File(rawFile.parentFile, "missing_annotated_${System.nanoTime()}.mp4")
        try {
            val session = sessionRecord(
                rawUri = rawFile.toURI().toString(),
                annotatedUri = missingAnnotated.toURI().toString(),
            )

            val selection = selectReplayAsset(session)

            assertEquals("Raw replay", selection.label)
            assertEquals(rawFile.toURI().toString(), selection.uri)
        } finally {
            rawFile.delete()
        }
    }

    @Test
    fun returnsUnavailableWhenNoReplayAssetExists() {
        val session = sessionRecord(rawUri = null, annotatedUri = null)

        val selection = selectReplayAsset(session)

        assertEquals("Replay unavailable", selection.label)
        assertNull(selection.uri)
    }

    @Test
    fun resolverPrefersAnnotatedThenRaw() {
        val result = resolvePreferredReplayUri(
            sessionRecord(rawUri = "raw", annotatedUri = "annotated", annotatedStatus = AnnotatedExportStatus.ANNOTATED_READY),
            isReadable = { it == "annotated" || it == "raw" },
        )
        assertEquals("annotated", result.source)
        assertEquals("annotated", result.uri)
    }

    @Test
    fun resolverIgnoresAnnotatedWhenStatusNotReady() {
        val result = resolvePreferredReplayUri(
            sessionRecord(rawUri = "raw", annotatedUri = "annotated", annotatedStatus = AnnotatedExportStatus.PROCESSING),
            isReadable = { it == "annotated" || it == "raw" },
        )
        assertEquals("raw", result.source)
        assertEquals("raw", result.uri)
    }

    private fun sessionRecord(
        rawUri: String?,
        annotatedUri: String?,
        annotatedStatus: AnnotatedExportStatus = if (annotatedUri == null) AnnotatedExportStatus.NOT_STARTED else AnnotatedExportStatus.ANNOTATED_READY,
    ) = SessionRecord(
        id = 99,
        title = "Test session",
        drillType = DrillType.WALL_HANDSTAND,
        startedAtMs = 1L,
        completedAtMs = 2L,
        overallScore = 80,
        strongestArea = "line",
        limitingFactor = "shoulders",
        issues = "",
        wins = "",
        metricsJson = "",
        annotatedVideoUri = annotatedUri,
        rawVideoUri = rawUri,
        annotatedExportStatus = annotatedStatus,
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "focus",
    )
}
