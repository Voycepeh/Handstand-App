package com.inversioncoach.app.overlay

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.inversioncoach.app.model.AlignmentMetric
import com.inversioncoach.app.model.AngleDebugMetric
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SmoothedPoseFrame

@Composable
fun OverlayRenderer(
    frame: SmoothedPoseFrame?,
    drillType: DrillType,
    sessionMode: SessionMode,
    modifier: Modifier = Modifier,
    showIdealLine: Boolean,
    showDebugOverlay: Boolean = false,
    debugMetrics: List<AlignmentMetric> = emptyList(),
    debugAngles: List<AngleDebugMetric> = emptyList(),
    currentPhase: String = "setup",
    activeFault: String = "",
    cueText: String = "",
    drillCameraSide: DrillCameraSide = DrillCameraSide.LEFT,
) {
    val mapper = remember { OverlayCoordinateMapper() }

    Canvas(modifier = modifier) {
        val joints = frame?.joints.orEmpty()
        val model = OverlayGeometry.build(drillType, sessionMode, joints, drillCameraSide)

        model.connections.forEach { (from, to) ->
            val start = model.joints.firstOrNull { it.name == from } ?: return@forEach
            val end = model.joints.firstOrNull { it.name == to } ?: return@forEach
            drawLine(
                color = Color(0xFF7CF0A9),
                start = mapper.toOffset(start, size.width, size.height),
                end = mapper.toOffset(end, size.width, size.height),
                strokeWidth = 6f,
            )
        }

        model.joints.forEach { joint ->
            val style = jointStyle(joint.name, Color(0xFF7CF0A9), 6f)
            drawCircle(style.color, radius = style.radius, center = mapper.toOffset(joint, size.width, size.height))
        }

        if (showIdealLine) {
            val referenceXNorm = model.idealLineX
            val start = mapper.map(referenceXNorm, 0f, size.width, size.height)
            val end = mapper.map(referenceXNorm, 1f, size.width, size.height)
            drawLine(color = Color.Cyan.copy(alpha = 0.45f), start = start, end = end, strokeWidth = 2f)
        }

        if (showDebugOverlay) {
            renderMetricDebug(debugMetrics)
            renderAngleDebug(debugAngles)
            renderFaultAndPhaseDebug(currentPhase, activeFault, cueText)
        }
    }
}

private fun OverlayCoordinateMapper.toOffset(joint: JointPoint, width: Float, height: Float): Offset = map(joint.x, joint.y, width, height)

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

private fun Color.toArgbCompat(): Int {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
