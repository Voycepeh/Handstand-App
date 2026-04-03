package com.inversioncoach.app.media

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMediaResolverTest {

    @Test
    fun resolve_rawOnly_returnsAvailableRawAndNoAnnotatedOutput() {
        val resolver = SessionMediaResolver(assetExists = { it == "raw.mp4" })
        val result = resolver.resolve(sessionForTest(rawUri = "raw.mp4", annotatedUri = null))

        assertEquals(SessionArtifact.Available("raw.mp4"), result.raw)
        assertEquals(SessionArtifact.Unavailable(SessionArtifactError.NO_ANNOTATED_OUTPUT), result.annotated)
        assertEquals(SessionMediaType.RAW, result.preferredReplay?.type)
    }

    @Test
    fun resolve_annotatedReady_returnsBothAvailable() {
        val resolver = SessionMediaResolver(assetExists = { it == "raw.mp4" || it == "annotated.mp4" })
        val result = resolver.resolve(
            sessionForTest(
                rawUri = "raw.mp4",
                annotatedUri = "annotated.mp4",
                annotatedStatus = AnnotatedExportStatus.ANNOTATED_READY,
            ),
        )

        assertEquals(SessionArtifact.Available("raw.mp4"), result.raw)
        assertEquals(SessionArtifact.Available("annotated.mp4"), result.annotated)
        assertEquals(SessionMediaType.ANNOTATED, result.preferredReplay?.type)
    }

    @Test
    fun resolve_annotatedProcessing_returnsProcessingState() {
        val resolver = SessionMediaResolver(assetExists = { it == "raw.mp4" })
        val result = resolver.resolve(
            sessionForTest(
                rawUri = "raw.mp4",
                annotatedUri = null,
                annotatedStatus = AnnotatedExportStatus.PROCESSING,
            ),
        )

        assertEquals(SessionArtifact.Available("raw.mp4"), result.raw)
        assertTrue(result.annotated is SessionArtifact.Processing)
    }

    @Test
    fun resolve_missingRawSource_returnsUnavailableRaw() {
        val resolver = SessionMediaResolver(assetExists = { false })
        val result = resolver.resolve(sessionForTest(rawUri = "raw.mp4", annotatedUri = null))

        assertEquals(SessionArtifact.Unavailable(SessionArtifactError.MISSING_FILE), result.raw)
    }

    @Test
    fun resolve_usesCanonicalRawPrecedence() {
        val session = sessionForTest(rawUri = "raw.mp4", annotatedUri = null).copy(
            rawFinalUri = "raw_final.mp4",
            rawMasterUri = "raw_master.mp4",
        )
        val resolver = SessionMediaResolver(assetExists = { it == "raw_master.mp4" || it == "raw_final.mp4" })

        val result = resolver.resolve(session)

        assertEquals(SessionArtifact.Available("raw_final.mp4"), result.raw)
    }

    @Test
    fun resolve_usesCanonicalAnnotatedPrecedence() {
        val session = sessionForTest(rawUri = "raw.mp4", annotatedUri = "annotated.mp4").copy(
            annotatedFinalUri = "annotated_final.mp4",
            annotatedMasterUri = "annotated_master.mp4",
            annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_READY,
        )
        val resolver = SessionMediaResolver(assetExists = { it == "annotated_master.mp4" || it == "annotated_final.mp4" })

        val result = resolver.resolve(session)

        assertEquals(SessionArtifact.Available("annotated_final.mp4"), result.annotated)
    }

    @Test
    fun resolve_rawMarkedInvalid_doesNotReturnRawReplay() {
        val session = sessionForTest(rawUri = "raw.mp4", annotatedUri = null).copy(
            rawPersistFailureReason = "RAW_MEDIA_CORRUPT",
        )
        val resolver = SessionMediaResolver(assetExists = { true })

        val result = resolver.resolve(session)

        assertEquals(SessionArtifact.Unavailable(SessionArtifactError.RAW_INVALID), result.raw)
        assertEquals(null, result.canonicalActionSource())
    }

    companion object {
        fun sessionForTest(
            rawUri: String?,
            annotatedUri: String?,
            annotatedStatus: AnnotatedExportStatus = if (annotatedUri == null) AnnotatedExportStatus.NOT_STARTED else AnnotatedExportStatus.ANNOTATED_READY,
        ) = SessionRecord(
            id = 1,
            title = "Session",
            drillType = DrillType.WALL_HANDSTAND,
            startedAtMs = 1,
            completedAtMs = 2,
            overallScore = 80,
            strongestArea = "line",
            limitingFactor = "hips",
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
}
