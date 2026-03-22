package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.AnnotatedExportFailureReason
import com.inversioncoach.app.recording.MediaVerificationResult
import com.inversioncoach.app.recording.ReplayInspectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RawPersistVerificationTest {

    @Test
    fun acceptsPersistedFileWhenMetadataProbeFails() {
        val rawFile = File.createTempFile("raw_verification", ".mp4")
        try {
            rawFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            val result = verifyPersistedRawVideoUris(
                candidateUris = listOf(rawFile.toURI().toString()),
                metadataVerifier = {
                    MediaVerificationResult(
                        isValid = false,
                        failureReason = AnnotatedExportFailureReason.METADATA_UNREADABLE,
                    )
                },
            )

            assertTrue(result.isPersisted)
            assertEquals(rawFile.toURI().toString(), result.persistedUri)
            assertFalse(result.isReplayPlayable)
        } finally {
            rawFile.delete()
        }
    }

    @Test
    fun failsWhenNoReadableNonEmptyCandidateExists() {
        val missingUri = File("/tmp/nonexistent_${System.nanoTime()}.mp4").toURI().toString()

        val result = verifyPersistedRawVideoUris(
            candidateUris = listOf(missingUri, null, ""),
            metadataVerifier = { MediaVerificationResult(isValid = true) },
        )

        assertFalse(result.isPersisted)
        assertEquals(null, result.persistedUri)
        assertFalse(result.isReplayPlayable)
    }

    @Test
    fun requiresReadableMetadataWhenStrictPersistenceCheckEnabled() {
        val rawFile = File.createTempFile("raw_verification_strict", ".mp4")
        try {
            rawFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            val result = verifyPersistedRawVideoUris(
                candidateUris = listOf(rawFile.toURI().toString()),
                metadataVerifier = {
                    MediaVerificationResult(
                        isValid = false,
                        failureReason = AnnotatedExportFailureReason.METADATA_UNREADABLE,
                    )
                },
                replayInspector = {
                    ReplayInspectionResult(
                        uri = it,
                        fileExists = true,
                        fileSizeBytes = rawFile.length(),
                        lastModifiedEpochMs = rawFile.lastModified(),
                        durationMs = null,
                        trackCount = 1,
                        width = 1280,
                        height = 720,
                        hasVideoTrack = true,
                        firstFrameDecoded = false,
                        errorDetail = "METADATA_UNREADABLE",
                    )
                },
                requireReadableMetadataForPersistence = true,
            )

            assertFalse(result.isPersisted)
            assertEquals(null, result.persistedUri)
        } finally {
            rawFile.delete()
        }
    }

    @Test
    fun captureSpanComputesNonNegativeDuration() {
        assertEquals(11_500L, captureSpanMs(startMs = 10_000L, stopMs = 21_500L))
        assertEquals(0L, captureSpanMs(startMs = 21_500L, stopMs = 10_000L))
    }

    @Test
    fun fullLiveRecordingDurationCanBeValidatedWithinTolerance() {
        val captureSpan = 11_500L
        val persistedDuration = 11_320L
        assertTrue(
            persistedDurationWithinTolerance(
                captureSpanMs = captureSpan,
                persistedDurationMs = persistedDuration,
                toleranceMs = 250L,
            ),
        )
        assertFalse(
            persistedDurationWithinTolerance(
                captureSpanMs = captureSpan,
                persistedDurationMs = 7_267L,
                toleranceMs = 250L,
            ),
        )
    }

    @Test
    fun classifiesMissingVideoTrackAsRawMediaCorrupt() {
        val failure = classifyRawReplayFailure(
            RawPersistVerification(
                isPersisted = true,
                persistedUri = "file:///raw_master.mp4",
                isReplayPlayable = false,
                inspection = ReplayInspectionResult(
                    uri = "file:///raw_master.mp4",
                    fileExists = true,
                    fileSizeBytes = 10_000L,
                    lastModifiedEpochMs = 123L,
                    durationMs = 5000L,
                    trackCount = 1,
                    width = null,
                    height = null,
                    hasVideoTrack = false,
                    firstFrameDecoded = false,
                    errorDetail = null,
                ),
            ),
        )

        assertEquals(AnnotatedExportFailureReason.RAW_MEDIA_CORRUPT.name, failure)
    }

    @Test
    fun classifiesUnreadableMetadataAsSourceVideoUnreadable() {
        val failure = classifyRawReplayFailure(
            RawPersistVerification(
                isPersisted = true,
                persistedUri = "file:///raw_master.mp4",
                isReplayPlayable = false,
                inspection = ReplayInspectionResult(
                    uri = "file:///raw_master.mp4",
                    fileExists = true,
                    fileSizeBytes = 12_000L,
                    lastModifiedEpochMs = 123L,
                    durationMs = null,
                    trackCount = 1,
                    width = null,
                    height = null,
                    hasVideoTrack = true,
                    firstFrameDecoded = false,
                    errorDetail = "METADATA_UNREADABLE",
                ),
            ),
        )

        assertEquals(AnnotatedExportFailureReason.SOURCE_VIDEO_UNREADABLE.name, failure)
    }
}
