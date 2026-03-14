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
import com.inversioncoach.app.model.SmoothedPoseFrame

@Composable
fun OverlayRenderer(
    frame: SmoothedPoseFrame?,
    drillType: DrillType,
    modifier: Modifier = Modifier,
    showIdealLine: Boolean,
    problematicJointName: String? = null,
    showDebugOverlay: Boolean = false,
    debugMetrics: List<AlignmentMetric> = emptyList(),
    debugAngles: List<AngleDebugMetric> = emptyList(),
    currentPhase: String = "setup",
    activeFault: String = "",
    cueText: String = "",
    showRawLandmarksInDebug: Boolean = false,
) {
    val sideSelector = remember { TrackedSideSelector() }
    Canvas(modifier = modifier) {
        val joints = frame?.joints.orEmpty()
        val mode = when {
            showDebugOverlay && showRawLandmarksInDebug -> OverlayMode.DEBUG_ALL_LANDMARKS
            showDebugOverlay -> OverlayMode.DEBUG_SIDE_VIEW
            else -> OverlayMode.NORMAL_SIDE_VIEW
        }
        val sideState = sideSelector.determineTrackedSide(joints)

        when (mode) {
            OverlayMode.NORMAL_SIDE_VIEW -> renderTrackedSideSkeleton(
                joints = joints,
                trackedSide = sideState.trackedSide,
                prominent = true,
                showLabels = false,
                problematicJointName = problematicJointName,
            )

            OverlayMode.DEBUG_SIDE_VIEW -> {
                renderAxisLines(joints)
                renderTrackedSideSkeleton(joints, sideState.trackedSide, prominent = true, showLabels = true, problematicJointName = problematicJointName)
                renderTrackedSideSkeleton(joints, sideState.trackedSide.opposite(), prominent = false, showLabels = false, problematicJointName = null)
                renderAngleDebug(debugAngles)
                renderMetricDebug(debugMetrics)
                renderSideSelectionDebug(sideState)
                renderFaultAndPhaseDebug(currentPhase, activeFault, cueText)
            }

            OverlayMode.DEBUG_ALL_LANDMARKS -> {
                joints.forEach { joint ->
                    drawCircle(confidenceColor(joint.visibility), radius = 5f, center = joint.toOffset(size.width, size.height))
                }
            }
        }

        if (showIdealLine) {
            val referenceXNorm = idealLineXForDrill(drillType, joints)
            drawLine(
                color = Color.Cyan.copy(alpha = if (mode == OverlayMode.NORMAL_SIDE_VIEW) 0.45f else 0.8f),
                start = Offset(size.width * referenceXNorm, 0f),
                end = Offset(size.width * referenceXNorm, size.height),
                strokeWidth = 2f,
            )
        }
    }
}

private fun DrawScope.renderTrackedSideSkeleton(
    joints: List<JointPoint>,
    trackedSide: TrackedSide,
    prominent: Boolean,
    showLabels: Boolean,
    problematicJointName: String?,
) {
    val renderable = getRenderableLandmarks(joints, trackedSide)
    if (renderable.isEmpty()) return
    val jointByName = renderable.associateBy { it.name }

    getRenderableConnections(trackedSide).forEach { (from, to) ->
        val start = jointByName[from] ?: return@forEach
        val end = jointByName[to] ?: return@forEach
        drawLine(
            color = if (prominent) Color(0xFF7CF0A9) else Color(0x80A0AEC0),
            start = start.toOffset(size.width, size.height),
            end = end.toOffset(size.width, size.height),
            strokeWidth = if (prominent) 6f else 3f,
        )
    }

    renderable.forEach { joint ->
        val center = joint.toOffset(size.width, size.height)
        val color = if (joint.name == problematicJointName) Color.Red else confidenceColor(joint.visibility)
        drawCircle(color = color, radius = if (prominent) 8f else 5f, center = center)
        if (showLabels) {
            val compactLabel = jointLabelFor(joint.name)
            if (compactLabel.isNotBlank()) {
                drawText(compactLabel, center + Offset(10f, -10f), Color.White)
                drawText("${"%.2f".format(joint.visibility)}", center + Offset(10f, 12f), Color.LightGray, 22f)
            }
        }
    }
}

private fun DrawScope.renderAxisLines(joints: List<JointPoint>) {
    val shoulderMid = midpoint(joints, "left_shoulder", "right_shoulder") ?: return
    val hipMid = midpoint(joints, "left_hip", "right_hip") ?: return
    val shoulderOffset = shoulderMid.toOffset(size.width, size.height)
    val hipOffset = hipMid.toOffset(size.width, size.height)

    drawLine(color = Color(0xFF89C2FF), start = shoulderOffset, end = hipOffset, strokeWidth = 3f)
    drawLine(color = Color(0x66FFFFFF), start = Offset(hipOffset.x, 0f), end = Offset(hipOffset.x, size.height), strokeWidth = 2f)
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

private fun DrawScope.renderSideSelectionDebug(state: SideSelectionState) {
    val y = size.height * 0.06f
    drawText("Tracked side: ${state.trackedSide.name}", Offset(size.width * 0.04f, y), Color.White)
    drawText("L:${"%.2f".format(state.leftScore)}  R:${"%.2f".format(state.rightScore)}", Offset(size.width * 0.04f, y + 30f), Color.LightGray, 22f)
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

private fun midpoint(joints: List<JointPoint>, first: String, second: String): JointPoint? {
    val firstJoint = joints.firstOrNull { it.name == first } ?: return null
    val secondJoint = joints.firstOrNull { it.name == second } ?: return null
    if (firstJoint.visibility < 0.35f || secondJoint.visibility < 0.35f) return null
    return JointPoint(
        name = "midpoint_${first}_$second",
        x = (firstJoint.x + secondJoint.x) / 2f,
        y = (firstJoint.y + secondJoint.y) / 2f,
        z = (firstJoint.z + secondJoint.z) / 2f,
        visibility = minOf(firstJoint.visibility, secondJoint.visibility),
    )
}

private fun jointLabelFor(jointName: String): String {
    val suffix = jointName.substringAfter("_", missingDelimiterValue = jointName)
    return SideJoint.entries.firstOrNull { it.baseName == suffix }?.label ?: if (jointName == "nose") "HD" else ""
}

private fun confidenceColor(visibility: Float): Color = when {
    visibility >= 0.75f -> Color(0xFF44D17A)
    visibility >= 0.5f -> Color(0xFFFFD166)
    else -> Color(0xFFFF6B6B)
}

private fun JointPoint.toOffset(width: Float, height: Float): Offset = Offset(x * width, y * height)

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
