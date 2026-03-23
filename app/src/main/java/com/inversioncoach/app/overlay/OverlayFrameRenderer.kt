package com.inversioncoach.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import com.inversioncoach.app.pose.PoseCoordinateMapper
import com.inversioncoach.app.pose.PoseProjectionInput
import com.inversioncoach.app.pose.PoseScaleMode

data class OverlayDrawingFrame(
    val drawSkeleton: Boolean,
    val drawIdealLine: Boolean,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val sourceRotationDegrees: Int = 0,
    val mirrored: Boolean = false,
    val previewContentRect: Rect? = null,
)

object OverlayFrameRenderer {
    private val mapper = PoseCoordinateMapper()
    private val skeletonPaint = Paint().apply {
        color = 0xFF7CF0A9.toInt()
        strokeWidth = 6f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val idealLinePaint = Paint().apply {
        color = 0x7300FFFF
        strokeWidth = 2f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val jointFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    fun drawAndroid(
        canvas: Canvas,
        width: Int,
        height: Int,
        model: OverlayRenderModel,
        frame: OverlayDrawingFrame,
    ) {
        if (frame.drawSkeleton) {
            val projection = PoseProjectionInput(
                sourceWidth = frame.sourceWidth.coerceAtLeast(1),
                sourceHeight = frame.sourceHeight.coerceAtLeast(1),
                previewWidth = width.toFloat(),
                previewHeight = height.toFloat(),
                previewContentRect = frame.previewContentRect,
                rotationDegrees = frame.sourceRotationDegrees,
                mirrored = frame.mirrored,
                scaleMode = PoseScaleMode.FIT,
            )
            model.connections.forEach { (from, to) ->
                val start = model.joints.firstOrNull { it.name == from } ?: return@forEach
                val end = model.joints.firstOrNull { it.name == to } ?: return@forEach
                val startMapped = mapper.map(start.x, start.y, projection)
                val endMapped = mapper.map(end.x, end.y, projection)
                canvas.drawLine(
                    startMapped.x,
                    startMapped.y,
                    endMapped.x,
                    endMapped.y,
                    skeletonPaint,
                )
            }

            model.joints.forEach { joint ->
                val style = jointStyle(joint.name, androidx.compose.ui.graphics.Color(0xFF7CF0A9), 6f)
                val alphaColor = style.color.copy(alpha = style.color.alpha * joint.visibility.coerceIn(0.2f, 1f))
                jointFillPaint.color = alphaColor.toArgbCompat()
                val mapped = mapper.map(joint.x, joint.y, projection)
                canvas.drawCircle(
                    mapped.x,
                    mapped.y,
                    style.radius,
                    jointFillPaint,
                )
            }
        }

        if (frame.drawIdealLine) {
            val projection = PoseProjectionInput(
                sourceWidth = frame.sourceWidth.coerceAtLeast(1),
                sourceHeight = frame.sourceHeight.coerceAtLeast(1),
                previewWidth = width.toFloat(),
                previewHeight = height.toFloat(),
                previewContentRect = frame.previewContentRect,
                rotationDegrees = frame.sourceRotationDegrees,
                mirrored = frame.mirrored,
                scaleMode = PoseScaleMode.FIT,
            )
            val (lineStart, lineEnd) = model.idealLine
            val mappedStart = mapper.map(lineStart.x, lineStart.y, projection)
            val mappedEnd = mapper.map(lineEnd.x, lineEnd.y, projection)
            canvas.drawLine(mappedStart.x, mappedStart.y, mappedEnd.x, mappedEnd.y, idealLinePaint)
        }
    }
}

fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
