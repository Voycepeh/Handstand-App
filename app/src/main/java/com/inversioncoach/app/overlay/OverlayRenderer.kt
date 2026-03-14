package com.inversioncoach.app.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.inversioncoach.app.model.SmoothedPoseFrame

@Composable
fun OverlayRenderer(
    frame: SmoothedPoseFrame?,
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
            drawLine(
                color = Color.Cyan,
                start = Offset(size.width * 0.5f, 0f),
                end = Offset(size.width * 0.5f, size.height),
                strokeWidth = 4f,
            )
        }
    }
}
