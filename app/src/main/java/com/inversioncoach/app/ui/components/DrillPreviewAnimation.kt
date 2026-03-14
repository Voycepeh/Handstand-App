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
import com.inversioncoach.app.motion.BodyJoint
import com.inversioncoach.app.motion.SkeletonAnimationEngine
import com.inversioncoach.app.motion.SkeletonAnimationSpec
import com.inversioncoach.app.motion.SkeletonRig

@Composable
fun DrillPreviewAnimation(
    animationSpec: SkeletonAnimationSpec,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    isPlaying: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "drill_preview")
    val t = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (1000f / animationSpec.fpsHint * 16f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "preview_progress",
    ).value

    val progress = if (isPlaying) t else 0f
    val pose = SkeletonAnimationEngine.interpolate(animationSpec, progress, mirrored)

    Canvas(modifier = modifier.size(72.dp)) {
        fun p(joint: BodyJoint): Offset {
            val point = pose[joint] ?: return Offset(size.width / 2f, size.height / 2f)
            return Offset(point.x * size.width, point.y * size.height)
        }

        SkeletonRig.bones.forEach { (a, b) ->
            drawLine(
                color = Color(0xFF4A6CF7),
                start = p(a),
                end = p(b),
                strokeWidth = 4.5f,
            )
        }
        drawCircle(Color(0xFF1F2A5A), radius = 4.8f, center = p(BodyJoint.HEAD))
    }
}
