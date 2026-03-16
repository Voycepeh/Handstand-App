package com.inversioncoach.app.overlay

import android.graphics.Canvas
import android.graphics.Paint

data class OverlayDrawingFrame(
    val drawSkeleton: Boolean,
    val drawIdealLine: Boolean,
)

object OverlayFrameRenderer {
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
            model.connections.forEach { (from, to) ->
                val start = model.joints.firstOrNull { it.name == from } ?: return@forEach
                val end = model.joints.firstOrNull { it.name == to } ?: return@forEach
                canvas.drawLine(
                    start.x * width,
                    start.y * height,
                    end.x * width,
                    end.y * height,
                    skeletonPaint,
                )
            }

            model.joints.forEach { joint ->
                val style = jointStyle(joint.name, androidx.compose.ui.graphics.Color(0xFF7CF0A9), 6f)
                jointFillPaint.color = style.color.toArgbCompat()
                canvas.drawCircle(
                    joint.x * width,
                    joint.y * height,
                    style.radius,
                    jointFillPaint,
                )
            }
        }

        if (frame.drawIdealLine) {
            val x = model.idealLineX * width
            canvas.drawLine(x, 0f, x, height.toFloat(), idealLinePaint)
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
