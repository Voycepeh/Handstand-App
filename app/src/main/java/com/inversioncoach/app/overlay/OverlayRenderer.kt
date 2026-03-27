package com.inversioncoach.app.overlay

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.inversioncoach.app.model.AlignmentMetric
import com.inversioncoach.app.model.AngleDebugMetric
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.pose.PoseScaleMode

@Composable
fun OverlayRenderer(
    frame: SmoothedPoseFrame?,
    drillType: DrillType,
    sessionMode: SessionMode,
    modifier: Modifier = Modifier,
    previewContentRect: Rect? = null,
    scaleMode: PoseScaleMode = PoseScaleMode.FIT,
    showIdealLine: Boolean,
    showDebugOverlay: Boolean = false,
    debugMetrics: List<AlignmentMetric> = emptyList(),
    debugAngles: List<AngleDebugMetric> = emptyList(),
    currentPhase: String = "setup",
    activeFault: String = "",
    cueText: String = "",
    drillCameraSide: DrillCameraSide = DrillCameraSide.LEFT,
    freestyleViewMode: FreestyleViewMode = FreestyleViewMode.UNKNOWN,
    unreliableJointNames: Set<String> = emptySet(),
) {
    Canvas(modifier = modifier) {
        val joints = frame?.joints.orEmpty()
        val model = OverlayGeometry.build(drillType, sessionMode, joints, drillCameraSide, freestyleViewMode)
        OverlayFrameRenderer.drawAndroid(
            canvas = drawContext.canvas.nativeCanvas,
            width = size.width.toInt().coerceAtLeast(1),
            height = size.height.toInt().coerceAtLeast(1),
            model = model,
            frame = OverlayDrawingFrame(
                drawSkeleton = joints.isNotEmpty(),
                drawIdealLine = showIdealLine,
                sourceWidth = frame?.analysisWidth ?: 0,
                sourceHeight = frame?.analysisHeight ?: 0,
                // Live analyzer coordinates are already normalized to preview-upright space.
                // Keep live projection in preview space and avoid an extra rotation.
                sourceRotationDegrees = 0,
                mirrored = frame?.mirrored ?: false,
                previewContentRect = previewContentRect,
                scaleMode = scaleMode,
                debugProjection = showDebugOverlay,
                renderTarget = OverlayRenderTarget.LIVE_PREVIEW,
                unreliableJointNames = unreliableJointNames,
            ),
        )

        if (showDebugOverlay) {
            renderMetricDebug(debugMetrics)
            renderAngleDebug(debugAngles)
            renderFaultAndPhaseDebug(currentPhase, activeFault, cueText)
        }
    }
}

private fun DrawScope.renderAngleDebug(angles: List<AngleDebugMetric>) {
    var y = size.height * 0.12f
    angles.take(5).forEach {
        drawText("${it.key}: ${it.degrees.toInt()}°", Offset(size.width * 0.04f, y), Color(0xFFB3F5FC))
        y += 34f
    }
}

private fun DrawScope.renderMetricDebug(metrics: List<AlignmentMetric>) {
    var y = size.height * 0.35f
    metrics.take(4).forEach {
        drawText("${it.key}: ${it.score}", Offset(size.width * 0.04f, y), Color(0xFFE2E8F0), 22f)
        y += 28f
    }
}

private fun DrawScope.renderFaultAndPhaseDebug(phase: String, fault: String, cueText: String) {
    drawText("Phase: ${phase.uppercase()}", Offset(size.width * 0.04f, size.height * 0.72f), Color.White)
    drawText(
        "Active Fault: ${fault.ifBlank { "none" }}",
        Offset(size.width * 0.04f, size.height * 0.76f),
        if (fault.isBlank()) Color.LightGray else Color(0xFFFFC857),
    )
    if (cueText.isNotBlank()) {
        drawText("Cue: ${cueText.take(56)}", Offset(size.width * 0.04f, size.height * 0.8f), Color(0xFFC4F1BE), 24f)
    }
}

private fun DrawScope.drawText(text: String, at: Offset, color: Color, textSize: Float = 26f) {
    drawContext.canvas.nativeCanvas.drawText(
        text,
        at.x,
        at.y,
        Paint().apply {
            this.color = color.toArgbCompat()
            this.textSize = textSize
            isAntiAlias = true
        },
    )
}
