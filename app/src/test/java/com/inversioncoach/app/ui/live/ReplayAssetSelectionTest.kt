package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.AnnotatedExportFailureReason
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
    fun resolverSelectsRawWhenAnnotatedNeverStartedAndRawReadable() {
        val result = resolvePreferredReplayUri(
            sessionRecord(rawUri = "raw", annotatedUri = null, annotatedStatus = AnnotatedExportStatus.NOT_STARTED),
            isReadable = { it == "raw" },
        )

        assertEquals("raw", result.source)
        assertEquals("raw", result.uri)
    }

    @Test
    fun resolverIgnoresAnnotatedWhenStatusNotReady() {
        val result = resolvePreferredReplayUri(
            sessionRecord(rawUri = "raw", annotatedUri = "annotated", annotatedStatus = AnnotatedExportStatus.PROCESSING),
            isReadable = { it == "annotated" || it == "raw" },
        )
        assertEquals("none", result.source)
        assertEquals(null, result.uri)
    }

    

    @Test
    fun replayResolutionSelectsRawWhenNotStartedAndRawReadable() {
        val resolution = resolveReplaySourceState(
            session = sessionRecord(
                rawUri = "file:///raw.mp4",
                annotatedUri = null,
                annotatedStatus = AnnotatedExportStatus.NOT_STARTED,
            ),
            isReadable = { it == "file:///raw.mp4" },
        )

        assertEquals(ReplaySourceState.RAW_READY, resolution.state)
        assertEquals("file:///raw.mp4", resolution.uri)
    }

    @Test
    fun replayResolutionWaitsForDecisionPointEvenWhenRawExists() {
        val resolution = resolveReplaySourceState(
            session = sessionRecord(
                rawUri = "file:///raw.mp4",
                annotatedUri = null,
                annotatedStatus = AnnotatedExportStatus.PROCESSING,
            ),
            isReadable = { it == "file:///raw.mp4" },
        )

        assertEquals(ReplaySourceState.UNRESOLVED, resolution.state)
        assertEquals(null, resolution.uri)
    }

    @Test
    fun replayResolutionFallsBackToRawAfterAnnotatedDecision() {
        val resolution = resolveReplaySourceState(
            session = sessionRecord(
                rawUri = "file:///raw.mp4",
                annotatedUri = null,
                annotatedStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
            ),
            isReadable = { it == "file:///raw.mp4" },
        )

        assertEquals(ReplaySourceState.RAW_READY, resolution.state)
        assertEquals("file:///raw.mp4", resolution.uri)
    }

    @Test
    fun replayResolutionReportsUnavailableWhenRawMarkedInvalid() {
        val resolution = resolveReplaySourceState(
            session = sessionRecord(
                rawUri = "file:///raw.mp4",
                annotatedUri = null,
                annotatedStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                rawPersistFailureReason = AnnotatedExportFailureReason.RAW_REPLAY_INVALID.name,
            ),
            isReadable = { it == "file:///raw.mp4" },
        )

        assertEquals(ReplaySourceState.UNAVAILABLE, resolution.state)
        assertEquals(null, resolution.uri)
    }

    private fun sessionRecord(
        rawUri: String?,
        annotatedUri: String?,
        annotatedStatus: AnnotatedExportStatus = if (annotatedUri == null) AnnotatedExportStatus.NOT_STARTED else AnnotatedExportStatus.ANNOTATED_READY,
        rawPersistFailureReason: String? = null,
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
        rawPersistFailureReason = rawPersistFailureReason,
        annotatedExportStatus = annotatedStatus,
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "focus",
    )
}
