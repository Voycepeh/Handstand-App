package com.inversioncoach.app.overlay

import androidx.compose.ui.geometry.Offset

class OverlayCoordinateMapper(
    private val sourceAspectRatio: Float = 9f / 16f,
    private val mirrored: Boolean = false,
) {
    fun map(normalizedX: Float, normalizedY: Float, canvasWidth: Float, canvasHeight: Float): Offset {
        val viewAspect = if (canvasHeight == 0f) sourceAspectRatio else canvasWidth / canvasHeight
        val useWidth = viewAspect > sourceAspectRatio

        val (scaleX, scaleY, offsetX, offsetY) = if (useWidth) {
            val scaledHeight = canvasWidth / sourceAspectRatio
            val topCrop = (scaledHeight - canvasHeight) / 2f
            Quad(1f, scaledHeight / canvasHeight, 0f, -topCrop)
        } else {
            val scaledWidth = canvasHeight * sourceAspectRatio
            val sideCrop = (scaledWidth - canvasWidth) / 2f
            Quad(scaledWidth / canvasWidth, 1f, -sideCrop, 0f)
        }

        val x = ((if (mirrored) 1f - normalizedX else normalizedX) * canvasWidth * scaleX) + offsetX
        val y = (normalizedY * canvasHeight * scaleY) + offsetY
        return Offset(x, y)
    }

    private data class Quad(val scaleX: Float, val scaleY: Float, val offsetX: Float, val offsetY: Float)
}
