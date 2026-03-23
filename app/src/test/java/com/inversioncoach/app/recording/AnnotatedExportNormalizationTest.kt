package com.inversioncoach.app.recording

import com.inversioncoach.app.model.JointPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotatedExportNormalizationTest {

    @Test
    fun sourceRotationZeroRendersUprightWithZeroOutputMetadataRotation() {
        val transform = buildExportTransform(
            source = SourceVideoMetadata(
                durationUs = 11_000_000L,
                width = 1080,
                height = 1920,
                rotationDegrees = 0,
            ),
            preset = ExportPreset.BALANCED,
        )

        assertFalse(transform.requiresAxisSwap)
        assertEquals(0, transform.sourceMetadataRotationDegrees)
        assertEquals(0, transform.renderRotationDegrees)
        assertEquals(0, transform.finalRotationMetadataDegrees)

        val verification = verifyExportedVideo(
            sourceDurationMs = 11_000L,
            output = OutputVideoMetadata(
                durationMs = 11_000L,
                width = transform.outputWidth,
                height = transform.outputHeight,
                rotationDegrees = 0,
            ),
            expectedWidth = transform.outputWidth,
            expectedHeight = transform.outputHeight,
            expectedRotationDegrees = transform.finalRotationMetadataDegrees,
        )
        assertTrue(verification.passed)
    }

    @Test
    fun sourceRotationNinetyRendersUprightWithSwappedAxesAndZeroOutputMetadataRotation() {
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
        assertEquals(90, transform.sourceMetadataRotationDegrees)
        assertEquals(90, transform.renderRotationDegrees)
        assertEquals(0, transform.finalRotationMetadataDegrees)
        assertTrue(transform.outputHeight > transform.outputWidth)

        val verification = verifyExportedVideo(
            sourceDurationMs = 11_000L,
            output = OutputVideoMetadata(
                durationMs = 11_000L,
                width = transform.outputWidth,
                height = transform.outputHeight,
                rotationDegrees = 0,
            ),
            expectedWidth = transform.outputWidth,
            expectedHeight = transform.outputHeight,
            expectedRotationDegrees = transform.finalRotationMetadataDegrees,
        )
        assertTrue(verification.passed)
    }

    @Test
    fun sourceRotationTwoSeventyRendersUprightWithSwappedAxesAndZeroOutputMetadataRotation() {
        val transform = buildExportTransform(
            source = SourceVideoMetadata(
                durationUs = 11_000_000L,
                width = 1920,
                height = 1080,
                rotationDegrees = 270,
            ),
            preset = ExportPreset.BALANCED,
        )

        assertTrue(transform.requiresAxisSwap)
        assertEquals(270, transform.sourceMetadataRotationDegrees)
        assertEquals(270, transform.renderRotationDegrees)
        assertEquals(0, transform.finalRotationMetadataDegrees)
        assertTrue(transform.outputHeight > transform.outputWidth)

        val verification = verifyExportedVideo(
            sourceDurationMs = 11_000L,
            output = OutputVideoMetadata(
                durationMs = 11_000L,
                width = transform.outputWidth,
                height = transform.outputHeight,
                rotationDegrees = 0,
            ),
            expectedWidth = transform.outputWidth,
            expectedHeight = transform.outputHeight,
            expectedRotationDegrees = transform.finalRotationMetadataDegrees,
        )
        assertTrue(verification.passed)
    }

    @Test
    fun doesNotApplySecondRotationWhenContentAlreadyUpright() {
        val transform = buildExportTransform(
            source = SourceVideoMetadata(
                durationUs = 9_000_000L,
                width = 1080,
                height = 1920,
                rotationDegrees = 0,
            ),
            preset = ExportPreset.BALANCED,
        )

        val overlayPoint = JointPoint(
            name = "left_hip",
            x = 0.3f,
            y = 0.6f,
            z = 0f,
            visibility = 1f,
        )
        val mappedOverlay = mapOverlayPointToExportSpace(overlayPoint, transform)

        assertEquals(overlayPoint.x, mappedOverlay.x, 0.0001f)
        assertEquals(overlayPoint.y, mappedOverlay.y, 0.0001f)
        assertEquals(0, transform.renderRotationDegrees)
        assertEquals(0, transform.finalRotationMetadataDegrees)
    }

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
            transform = ExportTransform(
                sourceMetadataRotationDegrees = 90,
                renderRotationDegrees = 90,
                outputWidth = 720,
                outputHeight = 1280,
            ),
        )
        assertEquals(0.75f, rotated.x, 0.0001f)
        assertEquals(0.75f, rotated.y, 0.0001f)
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

    @Test
    fun verificationFailsWhenOutputCarriesRotationMetadata() {
        val verification = verifyExportedVideo(
            sourceDurationMs = 11_000L,
            output = OutputVideoMetadata(
                durationMs = 11_000L,
                width = 720,
                height = 1280,
                rotationDegrees = 90,
            ),
            expectedWidth = 720,
            expectedHeight = 1280,
            expectedRotationDegrees = 0,
        )
        assertFalse(verification.passed)
        assertTrue(verification.failureDetail?.contains("output_rotation_mismatch") == true)
    }

    @Test
    fun texturePointMappingMatchesOverlayPointMappingForUprightCompositionSpace() {
        val overlayPoint = JointPoint(
            name = "left_hip",
            x = 0.3f,
            y = 0.6f,
            z = 0f,
            visibility = 1f,
        )
        val transform = ExportTransform(
            sourceMetadataRotationDegrees = 90,
            renderRotationDegrees = 90,
            outputWidth = 720,
            outputHeight = 1280,
        )

        val mappedOverlay = mapOverlayPointToExportSpace(overlayPoint, transform)
        val mappedTexture = mapTextureCoordinateToExportSpace(
            x = overlayPoint.x,
            y = overlayPoint.y,
            rotationDegrees = transform.renderRotationDegrees,
        )

        assertEquals(mappedTexture.first, mappedOverlay.x, 0.0001f)
        assertEquals(mappedTexture.second, mappedOverlay.y, 0.0001f)
    }

    @Test
    fun sourceRotationMappingPreservesExpectedNormalizationAcrossAllCanonicalRotations() {
        assertEquals(0, sourceToUprightRotationDegrees(0))
        assertEquals(90, sourceToUprightRotationDegrees(90))
        assertEquals(180, sourceToUprightRotationDegrees(180))
        assertEquals(270, sourceToUprightRotationDegrees(270))
    }

    @Test
    fun normalizedPointMappingCoversAllCanonicalRotations() {
        assertEquals(0.2f to 0.8f, mapNormalizedPointToExportSpace(0.2f, 0.8f, 0))
        assertEquals(0.2f to 0.2f, mapNormalizedPointToExportSpace(0.8f, 0.2f, 90))
        assertEquals(0.8f to 0.2f, mapNormalizedPointToExportSpace(0.2f, 0.8f, 180))
        assertEquals(0.8f to 0.8f, mapNormalizedPointToExportSpace(0.2f, 0.8f, 270))
    }

    @Test
    fun textureCoordinateMappingAccountsForOesYAxisAcrossCanonicalRotations() {
        assertEquals(0.2f to 0.8f, mapTextureCoordinateToExportSpace(0.2f, 0.8f, 0))
        assertEquals(0.8f to 0.8f, mapTextureCoordinateToExportSpace(0.2f, 0.2f, 90))
        assertEquals(0.8f to 0.2f, mapTextureCoordinateToExportSpace(0.2f, 0.8f, 180))
        assertEquals(0.2f to 0.2f, mapTextureCoordinateToExportSpace(0.2f, 0.8f, 270))
    }
}
