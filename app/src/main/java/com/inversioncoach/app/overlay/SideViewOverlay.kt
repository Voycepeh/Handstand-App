package com.inversioncoach.app.overlay

import androidx.compose.ui.graphics.Color

data class OverlayJointStyle(
    val color: Color,
    val radius: Float,
)

fun jointStyle(jointName: String, baseColor: Color, baseRadius: Float): OverlayJointStyle {
    val largeRadius = baseRadius * 2f
    return when {
        jointName == "nose" -> OverlayJointStyle(Color.Green, largeRadius)
        jointName.endsWith("_hip") -> OverlayJointStyle(Color.Red, largeRadius)
        else -> OverlayJointStyle(baseColor, baseRadius)
    }
}
