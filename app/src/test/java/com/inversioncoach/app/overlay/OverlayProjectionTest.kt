package com.inversioncoach.app.overlay

import androidx.compose.ui.geometry.Rect
import com.inversioncoach.app.pose.PoseScaleMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayProjectionTest {
    @Test
    fun uprightCoordinateSpaceIgnoresSourceRotationMetadata() {
        val projection = OverlayProjection.inputForFrame(
            frame = OverlayDrawingFrame(
                drawSkeleton = true,
                drawIdealLine = false,
                sourceWidth = 720,
                sourceHeight = 1280,
                sourceRotationDegrees = 270,
                coordinateSpace = OverlayCoordinateSpace.UPRIGHT_NORMALIZED,
            ),
            renderWidth = 1080,
            renderHeight = 1920,
        )

        assertEquals(0, projection.rotationDegrees)
    }

    @Test
    fun sourceOrientedCoordinateSpaceUsesNormalizedSourceRotation() {
        val projection = OverlayProjection.inputForFrame(
            frame = OverlayDrawingFrame(
                drawSkeleton = true,
                drawIdealLine = false,
                sourceWidth = 1280,
                sourceHeight = 720,
                sourceRotationDegrees = -90,
                coordinateSpace = OverlayCoordinateSpace.SOURCE_ORIENTED_NORMALIZED,
            ),
            renderWidth = 1920,
            renderHeight = 1080,
        )

        assertEquals(270, projection.rotationDegrees)
    }

    @Test
    fun projectionCarriesRectMirroringAndScaleMode() {
        val rect = Rect(20f, 40f, 1000f, 1800f)
        val projection = OverlayProjection.inputForFrame(
            frame = OverlayDrawingFrame(
                drawSkeleton = true,
                drawIdealLine = false,
                sourceWidth = 720,
                sourceHeight = 1280,
                mirrored = true,
                previewContentRect = rect,
                scaleMode = PoseScaleMode.FILL,
                coordinateSpace = OverlayCoordinateSpace.UPRIGHT_NORMALIZED,
            ),
            renderWidth = 1080,
            renderHeight = 1920,
        )

        assertEquals(true, projection.mirrored)
        assertEquals(rect, projection.previewContentRect)
        assertEquals(PoseScaleMode.FILL, projection.scaleMode)
    }
}
