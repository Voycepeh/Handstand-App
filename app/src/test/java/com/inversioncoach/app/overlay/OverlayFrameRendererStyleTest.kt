package com.inversioncoach.app.overlay

import com.inversioncoach.app.pose.PoseScaleMode
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFrameRendererStyleTest {

    @Test
    fun annotatedExportUsesSmallerStrokeAndDotSizingThanLivePreviewAtSameCanvasSize() {
        val liveStyle = OverlayFrameRenderer.styleForFrame(
            width = 720,
            height = 1280,
            frame = OverlayDrawingFrame(
                drawSkeleton = true,
                drawIdealLine = true,
                scaleMode = PoseScaleMode.FILL,
                renderTarget = OverlayRenderTarget.LIVE_PREVIEW,
                coordinateSpace = OverlayCoordinateSpace.UPRIGHT_NORMALIZED,
            ),
        )
        val exportStyle = OverlayFrameRenderer.styleForFrame(
            width = 720,
            height = 1280,
            frame = OverlayDrawingFrame(
                drawSkeleton = true,
                drawIdealLine = true,
                scaleMode = PoseScaleMode.FIT,
                renderTarget = OverlayRenderTarget.ANNOTATED_EXPORT,
                coordinateSpace = OverlayCoordinateSpace.UPRIGHT_NORMALIZED,
            ),
        )

        assertTrue(exportStyle.skeletonStrokeWidth < liveStyle.skeletonStrokeWidth)
        assertTrue(exportStyle.idealLineStrokeWidth < liveStyle.idealLineStrokeWidth)
        assertTrue(exportStyle.jointRadius < liveStyle.jointRadius)
    }

    @Test
    fun annotatedExportSizingStaysWithinExpectedBoundsForPortraitExportCanvas() {
        val exportStyle = OverlayFrameRenderer.styleForFrame(
            width = 720,
            height = 1280,
            frame = OverlayDrawingFrame(
                drawSkeleton = true,
                drawIdealLine = true,
                scaleMode = PoseScaleMode.FIT,
                renderTarget = OverlayRenderTarget.ANNOTATED_EXPORT,
                coordinateSpace = OverlayCoordinateSpace.UPRIGHT_NORMALIZED,
            ),
        )

        assertTrue(exportStyle.skeletonStrokeWidth in 2f..4.5f)
        assertTrue(exportStyle.idealLineStrokeWidth in 1f..2f)
        assertTrue(exportStyle.jointRadius in 2.4f..4.8f)
    }
}
