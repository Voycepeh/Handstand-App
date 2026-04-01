package com.inversioncoach.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.compose.ui.geometry.Rect
import com.inversioncoach.app.pose.PoseCoordinateMapper
import com.inversioncoach.app.pose.PoseProjectionInput
import com.inversioncoach.app.pose.PoseScaleMode
import kotlin.math.hypot

data class OverlayDrawingFrame(
    val drawSkeleton: Boolean,
    val drawIdealLine: Boolean,
    val drawCenterOfGravity: Boolean = true,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val sourceRotationDegrees: Int = 0,
    val mirrored: Boolean = false,
    val previewContentRect: Rect? = null,
    val scaleMode: PoseScaleMode = PoseScaleMode.FIT,
    val debugProjection: Boolean = false,
    val renderTarget: OverlayRenderTarget = OverlayRenderTarget.LIVE_PREVIEW,
    val styleScaleMultiplier: Float = 1f,
    val unreliableJointNames: Set<String> = emptySet(),
)

enum class OverlayRenderTarget {
    LIVE_PREVIEW,
    ANNOTATED_EXPORT,
}

object OverlayFrameRenderer {
    private const val TAG = "OverlayFrameRenderer"
    private const val MIN_RENDERABLE_VISIBILITY = 0.35f
    private const val MAX_CONNECTION_NORMALIZED_LENGTH = 1.1f
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
    private val cogPaint = Paint().apply {
        color = 0xFFFFD166.toInt()
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val cogOutlinePaint = Paint().apply {
        color = 0xCC1F2937.toInt()
        isAntiAlias = true
        style = Paint.Style.STROKE
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
        val mappedJoints = mapJoints(
            model = model,
            projection = projection,
            unreliableJointNames = frame.unreliableJointNames,
            canvasWidth = width.toFloat(),
            canvasHeight = height.toFloat(),
        )
        if (frame.drawSkeleton) {
            model.connections.forEach { (from, to) ->
                val start = model.joints.firstOrNull { it.name == from } ?: return@forEach
                val end = model.joints.firstOrNull { it.name == to } ?: return@forEach
                val startMapped = mappedJoints[from] ?: return@forEach
                val endMapped = mappedJoints[to] ?: return@forEach
                if (!shouldRenderConnection(start, end, startMapped, endMapped, frame, width.toFloat(), height.toFloat())) return@forEach
                canvas.drawLine(
                    startMapped.x,
                    startMapped.y,
                    endMapped.x,
                    endMapped.y,
                    skeletonPaint,
                )
            }

            model.joints.forEach { joint ->
                if (!isRenderableJoint(joint, frame.unreliableJointNames)) return@forEach
                val jointStyle = jointStyle(joint.name, androidx.compose.ui.graphics.Color(0xFF7CF0A9), style.jointRadius)
                val alphaColor = jointStyle.color.copy(alpha = jointStyle.color.alpha * joint.visibility.coerceIn(0.2f, 1f))
                jointFillPaint.color = alphaColor.toArgbCompat()
                val mapped = mappedJoints[joint.name] ?: return@forEach
                canvas.drawCircle(
                    mapped.x,
                    mapped.y,
                    jointStyle.radius,
                    jointFillPaint,
                )
            }
        }

        if (frame.drawIdealLine && model.showBalanceLane) {
            val (lineStart, lineEnd) = model.idealLine
            val mappedStart = mapper.map(lineStart.x, lineStart.y, projection)
            val mappedEnd = mapper.map(lineEnd.x, lineEnd.y, projection)
            canvas.drawLine(mappedStart.x, mappedStart.y, mappedEnd.x, mappedEnd.y, idealLinePaint)
        }

        if (frame.drawCenterOfGravity) {
            model.centerOfGravity
                ?.takeIf { isRenderableJoint(it, frame.unreliableJointNames) }
                ?.let { center ->
                    val mapped = mapper.map(center.x, center.y, projection)
                    drawStar(canvas, mapped.x, mapped.y, style.jointRadius * 2.1f)
                }
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

    private fun mapJoints(
        model: OverlayRenderModel,
        projection: PoseProjectionInput,
        unreliableJointNames: Set<String>,
        canvasWidth: Float,
        canvasHeight: Float,
    ): Map<String, androidx.compose.ui.geometry.Offset> = buildMap {
        model.joints.forEach { joint ->
            if (!isRenderableJoint(joint, frameUnreliableJointNames = unreliableJointNames)) return@forEach
            val mapped = mapper.map(joint.x, joint.y, projection)
            if (isSafeJointPoint(mapped.x, mapped.y, canvasWidth, canvasHeight)) {
                put(joint.name, mapped)
            }
        }
    }

    private fun shouldRenderConnection(
        startJoint: com.inversioncoach.app.model.JointPoint,
        endJoint: com.inversioncoach.app.model.JointPoint,
        start: androidx.compose.ui.geometry.Offset,
        end: androidx.compose.ui.geometry.Offset,
        frame: OverlayDrawingFrame,
        canvasWidth: Float,
        canvasHeight: Float,
    ): Boolean {
        if (!areConnectionEndpointsRenderable(startJoint, endJoint, frame.unreliableJointNames)) return false
        if (!isSafeJointPoint(start.x, start.y, canvasWidth, canvasHeight)) return false
        if (!isSafeJointPoint(end.x, end.y, canvasWidth, canvasHeight)) return false
        return isConnectionLengthSafe(startJoint, endJoint)
    }

    internal fun areConnectionEndpointsRenderable(
        startJoint: com.inversioncoach.app.model.JointPoint,
        endJoint: com.inversioncoach.app.model.JointPoint,
        unreliableJointNames: Set<String> = emptySet(),
    ): Boolean = isRenderableJoint(startJoint, unreliableJointNames) && isRenderableJoint(endJoint, unreliableJointNames)

    internal fun isRenderableJoint(
        joint: com.inversioncoach.app.model.JointPoint,
        frameUnreliableJointNames: Set<String> = emptySet(),
    ): Boolean {
        if (!joint.x.isFinite() || !joint.y.isFinite()) return false
        if (joint.visibility < MIN_RENDERABLE_VISIBILITY) return false
        if (joint.name in frameUnreliableJointNames) return false
        return true
    }

    internal fun isConnectionLengthSafe(
        startJoint: com.inversioncoach.app.model.JointPoint,
        endJoint: com.inversioncoach.app.model.JointPoint,
    ): Boolean {
        val normalizedLength = hypot(startJoint.x - endJoint.x, startJoint.y - endJoint.y)
        if (!normalizedLength.isFinite()) return false
        return normalizedLength <= MAX_CONNECTION_NORMALIZED_LENGTH
    }

    internal fun isSafeJointPoint(x: Float, y: Float, canvasWidth: Float, canvasHeight: Float): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        val marginX = canvasWidth * 0.5f
        val marginY = canvasHeight * 0.5f
        return x in (-marginX)..(canvasWidth + marginX) && y in (-marginY)..(canvasHeight + marginY)
    }

    private fun drawStar(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val innerRadius = radius * 0.45f
        val path = Path()
        for (i in 0 until 10) {
            val angle = Math.toRadians((i * 36.0) - 90.0)
            val pointRadius = if (i % 2 == 0) radius else innerRadius
            val x = centerX + (Math.cos(angle) * pointRadius).toFloat()
            val y = centerY + (Math.sin(angle) * pointRadius).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        cogOutlinePaint.strokeWidth = (radius * 0.22f).coerceAtLeast(1.2f)
        canvas.drawPath(path, cogPaint)
        canvas.drawPath(path, cogOutlinePaint)
    }
}

fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
