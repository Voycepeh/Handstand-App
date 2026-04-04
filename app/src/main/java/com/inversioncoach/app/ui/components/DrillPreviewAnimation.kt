package com.inversioncoach.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.motion.SkeletonAnimationEngine
import com.inversioncoach.app.motion.SkeletonAnimationSpec

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
    val pose = remember(animationSpec, progress, mirrored) {
        SkeletonAnimationEngine.interpolate(animationSpec, progress, mirrored)
            .mapKeys { (joint, _) -> joint.name.lowercase() }
            .mapValues { (_, point) -> JointPoint(point.x, point.y) }
    }
    OverlaySkeletonPreview(
        joints = pose,
        modifier = modifier,
        policy = SkeletonPreviewPolicies.motionPreview,
    )
}
