package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.AnnotatedExportFailureReason
import com.inversioncoach.app.recording.MediaVerificationResult
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
}
