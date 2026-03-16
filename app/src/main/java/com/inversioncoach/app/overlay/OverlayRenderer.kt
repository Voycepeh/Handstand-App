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
    val freestyleClassifier = remember { FreestyleOrientationClassifier() }
    val freestyleStrategy = remember { FreestyleOverlayStrategy() }
    val drillStrategy = remember { FixedDrillSideOverlayStrategy() }
    val mapper = remember { OverlayCoordinateMapper() }

    Canvas(modifier = modifier) {
        val joints = frame?.joints.orEmpty()
        val viewMode = freestyleClassifier.classify(joints)
        val model = if (sessionMode == SessionMode.FREESTYLE) {
            freestyleStrategy.build(joints, viewMode)
        } else {
            drillStrategy.build(joints, drillCameraSide)
        }

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
            val referenceXNorm = idealLineXForDrill(drillType, joints)
            val start = mapper.map(referenceXNorm, 0f, size.width, size.height)
            val end = mapper.map(referenceXNorm, 1f, size.width, size.height)
            drawLine(color = Color.Cyan.copy(alpha = 0.45f), start = start, end = end, strokeWidth = 2f)
        }

        if (showDebugOverlay) {
            renderMetricDebug(debugMetrics)
            renderAngleDebug(debugAngles)
            renderFaultAndPhaseDebug(currentPhase, activeFault, cueText)
            if (sessionMode == SessionMode.FREESTYLE) {
                drawText("Freestyle view: ${viewMode.name}", Offset(size.width * 0.04f, size.height * 0.08f), Color.White)
            }
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

private fun idealLineXForDrill(drillType: DrillType, joints: List<JointPoint>): Float {
    val jointPriority = when (drillType) {
        DrillType.FREESTYLE,
        DrillType.WALL_FACING_HANDSTAND_HOLD,
        DrillType.CHEST_TO_WALL_HANDSTAND,
        DrillType.BACK_TO_WALL_HANDSTAND,
        DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP,
        DrillType.FREESTANDING_HANDSTAND_FUTURE,
        DrillType.PIKE_PUSH_UP,
        DrillType.ELEVATED_PIKE_PUSH_UP,
        DrillType.PUSH_UP,
        DrillType.WALL_PUSH_UP,
        DrillType.STANDARD_PUSH_UP,
        DrillType.INCLINE_OR_KNEE_PUSH_UP,
        DrillType.FOREARM_PLANK,
        DrillType.PARALLEL_BAR_DIP,
        -> listOf(listOf("left_wrist", "right_wrist"), listOf("left_shoulder", "right_shoulder"))
        DrillType.BODYWEIGHT_SQUAT,
        DrillType.REVERSE_LUNGE,
        DrillType.BURPEE,
        DrillType.STANDING_POSTURE_HOLD,
        -> listOf(listOf("left_ankle", "right_ankle"), listOf("left_hip", "right_hip"))
        DrillType.HANGING_KNEE_RAISE,
        DrillType.PULL_UP_OR_ASSISTED_PULL_UP,
        DrillType.L_SIT_HOLD,
        DrillType.HOLLOW_BODY_HOLD,
        DrillType.GLUTE_BRIDGE,
        DrillType.SIT_UP,
        -> listOf(listOf("left_hip", "right_hip"), listOf("left_shoulder", "right_shoulder"))
    }

    for (jointNames in jointPriority) {
        val xs = jointNames.mapNotNull { name -> joints.firstOrNull { it.name == name }?.x }
        if (xs.isNotEmpty()) return xs.average().toFloat().coerceIn(0.1f, 0.9f)
    }
    return 0.5f
}
