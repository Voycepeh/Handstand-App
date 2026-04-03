package com.inversioncoach.app.ui.upload

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadVideoInputNormalizerTest {
    @Test
    fun portraitRotationMetadataRequiresNormalization() = runTest {
        val sourceUri = Uri.parse("content://video/rotation")
        val normalizer = normalizerFor(
            UploadVideoFormatDetails(
                containerMime = "video/mp4",
                videoMime = "video/avc",
                width = 1920,
                height = 1080,
                rotationDegrees = 90,
                frameRate = 30,
                bitDepth = 8,
                colorTransfer = null,
                hdrStaticInfoPresent = false,
                videoTrackCount = 1,
                audioTrackCount = 1,
                metadataTrackCount = 0,
            ),
        )

        val result = normalizer.normalize(sourceUri)

        assertTrue(result.normalizationRequired)
        assertTrue(result.reasons.contains("rotation_metadata"))
        assertEquals(1080, result.canonical.width)
        assertEquals(1920, result.canonical.height)
    }

    @Test
    fun hevcHdrAndMetadataTracksAreMarkedForCanonicalization() = runTest {
        val sourceUri = Uri.parse("content://video/hevc")
        val normalizer = normalizerFor(
            UploadVideoFormatDetails(
                containerMime = "video/mp4",
                videoMime = "video/hevc",
                width = 1080,
                height = 1920,
                rotationDegrees = 0,
                frameRate = 60,
                bitDepth = 10,
                colorTransfer = 6,
                hdrStaticInfoPresent = true,
                videoTrackCount = 1,
                audioTrackCount = 1,
                metadataTrackCount = 1,
            ),
        )

        val result = normalizer.normalize(sourceUri)

        assertTrue(result.normalizationRequired)
        assertTrue(result.reasons.contains("hevc_source"))
        assertTrue(result.reasons.contains("ten_bit_source"))
        assertTrue(result.reasons.contains("hdr_metadata"))
        assertTrue(result.reasons.contains("noncanonical_tracks"))
        assertEquals(30, result.canonical.frameRate)
        assertEquals("video/avc", result.canonical.videoMime)
        assertEquals(8, result.canonical.bitDepth)
        assertEquals("SDR", result.canonical.dynamicRange)
    }

    @Test
    fun canonicalSourceSkipsNormalizationAttempt() = runTest {
        val sourceUri = Uri.parse("content://video/canonical")
        val normalizer = normalizerFor(
            UploadVideoFormatDetails(
                containerMime = "video/mp4",
                videoMime = "video/avc",
                width = 1080,
                height = 1920,
                rotationDegrees = 0,
                frameRate = 30,
                bitDepth = 8,
                colorTransfer = null,
                hdrStaticInfoPresent = false,
                videoTrackCount = 1,
                audioTrackCount = 1,
                metadataTrackCount = 0,
            ),
        )

        val result = normalizer.normalize(sourceUri)

        assertFalse(result.normalizationRequired)
        assertFalse(result.normalizationAttempted)
        assertTrue(result.normalizationSucceeded)
        assertEquals(sourceUri, result.workingUri)
    }

    @Test
    fun missingAudioTrackIsTreatedAsOptional() = runTest {
        val sourceUri = Uri.parse("content://video/no-audio")
        val normalizer = normalizerFor(
            UploadVideoFormatDetails(
                containerMime = "video/mp4",
                videoMime = "video/avc",
                width = 1080,
                height = 1920,
                rotationDegrees = 0,
                frameRate = 30,
                bitDepth = 8,
                colorTransfer = null,
                hdrStaticInfoPresent = false,
                videoTrackCount = 1,
                audioTrackCount = 0,
                metadataTrackCount = 0,
            ),
        )

        val result = normalizer.normalize(sourceUri)

        assertFalse(result.normalizationRequired)
        assertTrue(result.normalizationSucceeded)
    }

    private fun normalizerFor(source: UploadVideoFormatDetails): DefaultUploadVideoInputNormalizer {
        val inspector = object : UploadVideoFormatInspector {
            override fun inspect(sourceUri: Uri): UploadVideoFormatDetails = source
        }
        val transcoder = object : UploadVideoTranscoder {
            override suspend fun transcodeToCanonical(sourceUri: Uri, source: UploadVideoFormatDetails, target: CanonicalVideoSpec): Uri? = null
        }
        return DefaultUploadVideoInputNormalizer(
            inspector = inspector,
            transcoder = transcoder,
        )
    }
}
