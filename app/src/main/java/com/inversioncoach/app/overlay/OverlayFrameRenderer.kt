package com.inversioncoach.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
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
    val scaleMode: PoseScaleMode = PoseScaleMode.FIT,
    val debugProjection: Boolean = false,
    val renderTarget: OverlayRenderTarget = OverlayRenderTarget.LIVE_PREVIEW,
    val styleScaleMultiplier: Float = 1f,
)

enum class OverlayRenderTarget {
    LIVE_PREVIEW,
    ANNOTATED_EXPORT,
}

object OverlayFrameRenderer {
    private const val TAG = "OverlayFrameRenderer"
    private val mapper = PoseCoordinateMapper()
    private val skeletonPaint = Paint().apply {
        color = 0xFF7CF0A9.toInt()
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val idealLinePaint = Paint().apply {
        color = 0x7300FFFF
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
        val style = styleForFrame(width = width, height = height, frame = frame)
        skeletonPaint.strokeWidth = style.skeletonStrokeWidth
        idealLinePaint.strokeWidth = style.idealLineStrokeWidth

        val projection = PoseProjectionInput(
            sourceWidth = frame.sourceWidth.coerceAtLeast(1),
            sourceHeight = frame.sourceHeight.coerceAtLeast(1),
            previewWidth = width.toFloat(),
            previewHeight = height.toFloat(),
            previewContentRect = frame.previewContentRect,
            rotationDegrees = frame.sourceRotationDegrees,
            mirrored = frame.mirrored,
            scaleMode = frame.scaleMode,
        )
        val projectionDiagnostics = mapper.diagnostics(projection)
        val projectedJointBounds = projectedJointBounds(model = model, projection = projection)
        if (frame.debugProjection) {
            Log.d(
                TAG,
                "Projection diagnostics canvas=0,0,${width}x${height} " +
                    "baseContentRect=${frame.previewContentRect ?: Rect(0f, 0f, width.toFloat(), height.toFloat())} " +
                    "projectedContentRect=${projectionDiagnostics.contentRect} " +
                    "scaleMode=${frame.scaleMode} skeletonBounds=${projectedJointBounds ?: "none"}",
            )
        }
        if (frame.drawSkeleton) {
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
                val jointStyle = jointStyle(joint.name, androidx.compose.ui.graphics.Color(0xFF7CF0A9), style.jointRadius)
                val alphaColor = jointStyle.color.copy(alpha = jointStyle.color.alpha * joint.visibility.coerceIn(0.2f, 1f))
                jointFillPaint.color = alphaColor.toArgbCompat()
                val mapped = mapper.map(joint.x, joint.y, projection)
                canvas.drawCircle(
                    mapped.x,
                    mapped.y,
                    jointStyle.radius,
                    jointFillPaint,
                )
            }
        }

        if (frame.drawIdealLine) {
            val (lineStart, lineEnd) = model.idealLine
            val mappedStart = mapper.map(lineStart.x, lineStart.y, projection)
            val mappedEnd = mapper.map(lineEnd.x, lineEnd.y, projection)
            canvas.drawLine(mappedStart.x, mappedStart.y, mappedEnd.x, mappedEnd.y, idealLinePaint)
        }
    }

    internal data class OverlayStrokeStyle(
        val skeletonStrokeWidth: Float,
        val idealLineStrokeWidth: Float,
        val jointRadius: Float,
    )

    internal fun styleForFrame(
        width: Int,
        height: Int,
        frame: OverlayDrawingFrame,
    ): OverlayStrokeStyle {
        val minDimension = minOf(width, height).toFloat().coerceAtLeast(1f)
        val normalizedScale = (minDimension / 720f).coerceIn(0.75f, 1.6f)
        val targetScale = when (frame.renderTarget) {
            OverlayRenderTarget.LIVE_PREVIEW -> normalizedScale
            OverlayRenderTarget.ANNOTATED_EXPORT -> (normalizedScale * 0.72f).coerceAtMost(1f)
        } * frame.styleScaleMultiplier.coerceIn(0.5f, 2f)
        return OverlayStrokeStyle(
            skeletonStrokeWidth = (4.5f * targetScale).coerceAtLeast(2f),
            idealLineStrokeWidth = (1.6f * targetScale).coerceAtLeast(1f),
            jointRadius = (4.8f * targetScale).coerceAtLeast(2.4f),
        )
    }

    private fun projectedJointBounds(
        model: OverlayRenderModel,
        projection: PoseProjectionInput,
    ): Rect? {
        if (model.joints.isEmpty()) return null
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        model.joints.forEach { joint ->
            val mapped = mapper.map(joint.x, joint.y, projection)
            minX = minOf(minX, mapped.x)
            minY = minOf(minY, mapped.y)
            maxX = maxOf(maxX, mapped.x)
            maxY = maxOf(maxY, mapped.y)
        }
        if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) return null
        return Rect(minX, minY, maxX, maxY)
    }
}

fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
