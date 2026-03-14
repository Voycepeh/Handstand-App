package com.inversioncoach.app.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
) {
    Canvas(modifier = modifier) {
        val joints = frame?.joints.orEmpty()
        joints.forEach { joint ->
            val color = if (joint.name == problematicJointName) Color.Red else Color.Green
            drawCircle(
                color = color,
                radius = 7f,
                center = Offset(joint.x * size.width, joint.y * size.height),
            )
        }
        if (showIdealLine) {
            val referenceXNorm = idealLineXForDrill(drillType, joints)
            drawLine(
                color = Color.Cyan,
                start = Offset(size.width * referenceXNorm, 0f),
                end = Offset(size.width * referenceXNorm, size.height),
                strokeWidth = 4f,
            )
        }
    }
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
        if (xs.isNotEmpty()) {
            return xs.average().toFloat().coerceIn(0.1f, 0.9f)
        }
    }

    return 0.5f
}
