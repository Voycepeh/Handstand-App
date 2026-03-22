package com.inversioncoach.app.pose

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseCoordinateMapperTest {
    private val mapper = PoseCoordinateMapper()

    @Test
    fun mapsPortraitFitWithInsets() {
        val input = PoseProjectionInput(
            sourceWidth = 720,
            sourceHeight = 1280,
            previewWidth = 1080f,
            previewHeight = 1920f,
            previewContentRect = Rect(0f, 120f, 1080f, 1800f),
            scaleMode = PoseScaleMode.FIT,
        )

        val center = mapper.map(0.5f, 0.5f, input)
        assertEquals(540f, center.x, 0.1f)
        assertEquals(960f, center.y, 0.1f)
    }

    @Test
    fun mapsMirroredLandscape() {
        val input = PoseProjectionInput(
            sourceWidth = 1280,
            sourceHeight = 720,
            previewWidth = 1920f,
            previewHeight = 1080f,
            mirrored = true,
        )

        val mapped = mapper.map(0.2f, 0.4f, input)
        assertEquals(1536f, mapped.x, 0.5f)
        assertEquals(432f, mapped.y, 0.5f)
    }

    @Test
    fun mapsWithRotationNinetyDegrees() {
        val input = PoseProjectionInput(
            sourceWidth = 720,
            sourceHeight = 1280,
            previewWidth = 1080f,
            previewHeight = 1920f,
            rotationDegrees = 90,
        )

        val mapped = mapper.map(0.2f, 0.1f, input)
        assertEquals(108f, mapped.x, 0.5f)
        assertEquals(1536f, mapped.y, 0.5f)
    }

    @Test
    fun fillModeExpandsContentRectBeyondPreview() {
        val input = PoseProjectionInput(
            sourceWidth = 720,
            sourceHeight = 1280,
            previewWidth = 1920f,
            previewHeight = 1080f,
            scaleMode = PoseScaleMode.FILL,
        )
        val diagnostics = mapper.diagnostics(input)
        assertTrue(diagnostics.contentRect.width >= 1920f)
        assertTrue(diagnostics.contentRect.height >= 1080f)
    }
}
