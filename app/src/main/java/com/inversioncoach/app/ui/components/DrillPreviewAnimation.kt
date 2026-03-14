package com.inversioncoach.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.motion.DrillPreviewKeyframe

@Composable
fun DrillPreviewAnimation(keyframes: List<DrillPreviewKeyframe>, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "drill_preview")
    val t = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "preview_progress",
    ).value

    val pose = interpolatePose(keyframes, t)

    Canvas(modifier = modifier.size(72.dp)) {
        fun p(name: String): Offset {
            val joint = pose[name] ?: (0.5f to 0.5f)
            return Offset(joint.first * size.width, joint.second * size.height)
        }

        val head = p("head")
        val shoulder = p("shoulder")
        val hip = p("hip")
        val hand = p("hand")
        val foot = p("foot")

        drawLine(Color(0xFF4A6CF7), shoulder, hip, strokeWidth = 6f)
        drawLine(Color(0xFF4A6CF7), shoulder, hand, strokeWidth = 5f)
        drawLine(Color(0xFF4A6CF7), hip, foot, strokeWidth = 5f)
        drawCircle(Color(0xFF1F2A5A), radius = 6f, center = head)
    }
}

private fun interpolatePose(keyframes: List<DrillPreviewKeyframe>, t: Float): Map<String, Pair<Float, Float>> {
    if (keyframes.isEmpty()) return emptyMap()
    if (keyframes.size == 1) return keyframes.first().joints

    val sorted = keyframes.sortedBy { it.t }
    val right = sorted.firstOrNull { it.t >= t } ?: sorted.last()
    val left = sorted.lastOrNull { it.t <= t } ?: sorted.first()
    if (left.t == right.t) return left.joints

    val local = ((t - left.t) / (right.t - left.t)).coerceIn(0f, 1f)
    val keys = left.joints.keys + right.joints.keys

    return keys.associateWith { key ->
        val a = left.joints[key] ?: right.joints[key] ?: (0.5f to 0.5f)
        val b = right.joints[key] ?: a
        (a.first + (b.first - a.first) * local) to (a.second + (b.second - a.second) * local)
    }
}
