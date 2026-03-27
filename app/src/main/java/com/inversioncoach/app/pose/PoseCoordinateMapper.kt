package com.inversioncoach.app.pose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

enum class PoseScaleMode { FIT, FILL }

data class PoseProjectionInput(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val previewWidth: Float,
    val previewHeight: Float,
    val previewContentRect: Rect? = null,
    val rotationDegrees: Int = 0,
    val mirrored: Boolean = false,
    val scaleMode: PoseScaleMode = PoseScaleMode.FIT,
    val overlayOffsetX: Float = 0f,
    val overlayOffsetY: Float = 0f,
)

data class PoseProjectionDiagnostics(
    val contentRect: Rect,
    val scale: Float,
    val normalizedSourceWidth: Int,
    val normalizedSourceHeight: Int,
    val mirrored: Boolean,
    val rotationDegrees: Int,
)

class PoseCoordinateMapper {
    fun map(normalizedX: Float, normalizedY: Float, input: PoseProjectionInput): Offset {
        val diagnostics = diagnostics(input)
        val (rotatedX, rotatedY) = rotate(normalizedX, normalizedY, diagnostics.rotationDegrees)
        val mappedX = if (diagnostics.mirrored) 1f - rotatedX else rotatedX
        return Offset(
            x = diagnostics.contentRect.left + mappedX * diagnostics.contentRect.width + input.overlayOffsetX,
            y = diagnostics.contentRect.top + rotatedY * diagnostics.contentRect.height + input.overlayOffsetY,
        )
    }

    fun diagnostics(input: PoseProjectionInput): PoseProjectionDiagnostics {
        val rotation = ((input.rotationDegrees % 360) + 360) % 360
        val rotated = rotation == 90 || rotation == 270
        val sourceW = if (rotated) input.sourceHeight else input.sourceWidth
        val sourceH = if (rotated) input.sourceWidth else input.sourceHeight
        val baseRect = input.previewContentRect ?: Rect(0f, 0f, input.previewWidth, input.previewHeight)
        val sourceAspect = if (sourceH == 0) 1f else sourceW.toFloat() / sourceH.toFloat()
        val previewAspect = if (baseRect.height == 0f) sourceAspect else baseRect.width / baseRect.height

        val scale = when (input.scaleMode) {
            PoseScaleMode.FIT -> if (sourceAspect > previewAspect) baseRect.width / sourceW else baseRect.height / sourceH
            PoseScaleMode.FILL -> if (sourceAspect > previewAspect) baseRect.height / sourceH else baseRect.width / sourceW
        }
        val scaledWidth = sourceW * scale
        val scaledHeight = sourceH * scale
        val left = baseRect.left + (baseRect.width - scaledWidth) / 2f
        val top = baseRect.top + (baseRect.height - scaledHeight) / 2f
        return PoseProjectionDiagnostics(
            contentRect = Rect(left, top, left + scaledWidth, top + scaledHeight),
            scale = scale,
            normalizedSourceWidth = sourceW,
            normalizedSourceHeight = sourceH,
            mirrored = input.mirrored,
            rotationDegrees = rotation,
        )
    }

    private fun rotate(x: Float, y: Float, rotationDegrees: Int): Pair<Float, Float> = when (rotationDegrees) {
        90 -> y to (1f - x)
        180 -> (1f - x) to (1f - y)
        270 -> (1f - y) to x
        else -> x to y
    }
}
