package com.inversioncoach.app.recording

import com.inversioncoach.app.model.JointPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotatedExportNormalizationTest {

    @Test
    fun shorterOverlayTimelineDoesNotChangeRawDurationVerification() {
        val verification = verifyExportedVideo(
            sourceDurationMs = 11_000L,
            output = OutputVideoMetadata(
                durationMs = 10_700L,
                width = 720,
                height = 1280,
                rotationDegrees = 0,
            ),
            toleranceMs = 750L,
        )
        assertTrue(verification.passed)
    }

    @Test
    fun rotatedInputSwapsExportDimensionsForPortraitOutput() {
        val transform = buildExportTransform(
            source = SourceVideoMetadata(
                durationUs = 11_000_000L,
                width = 1920,
                height = 1080,
                rotationDegrees = 90,
            ),
            preset = ExportPreset.BALANCED,
        )

        assertTrue(transform.requiresAxisSwap)
        assertEquals(90, transform.rotationDegrees)
        assertTrue(transform.outputHeight > transform.outputWidth)
    }

    @Test
    fun overlayPointsRotateIntoSameNormalizedSpaceAsVideo() {
        val point = JointPoint(
            name = "left_shoulder",
            x = 0.25f,
            y = 0.75f,
            z = 0f,
            visibility = 1f,
        )
        val rotated = mapOverlayPointToExportSpace(
            point = point,
            transform = ExportTransform(rotationDegrees = 90, outputWidth = 720, outputHeight = 1280),
        )
        assertEquals(0.25f, rotated.x, 0.0001f)
        assertEquals(0.25f, rotated.y, 0.0001f)
    }

    @Test
    fun verificationFailsWhenOutputDurationIsUnexpectedlyShort() {
        val verification = verifyExportedVideo(
            sourceDurationMs = 11_000L,
            output = OutputVideoMetadata(
                durationMs = 8_500L,
                width = 720,
                height = 1280,
                rotationDegrees = 0,
            ),
            toleranceMs = 500L,
        )
        assertFalse(verification.passed)
        assertTrue(verification.failureDetail?.contains("output_duration_too_short") == true)
    }
}
