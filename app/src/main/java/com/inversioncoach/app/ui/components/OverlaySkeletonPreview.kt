package com.inversioncoach.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.model.JointPoint as OverlayJointPoint
import com.inversioncoach.app.overlay.OverlayCoordinateSpace
import com.inversioncoach.app.overlay.OverlayDrawingFrame
import com.inversioncoach.app.overlay.OverlayFrameRenderer
import com.inversioncoach.app.overlay.OverlayRenderModel
import com.inversioncoach.app.overlay.OverlayRenderTarget
import com.inversioncoach.app.pose.PoseScaleMode

object OverlaySkeletonPreviewDefaults {
    private val jointAliases = mapOf(
        "head" to "nose",
        "shoulder_left" to "left_shoulder",
        "shoulder_right" to "right_shoulder",
        "elbow_left" to "left_elbow",
        "elbow_right" to "right_elbow",
        "wrist_left" to "left_wrist",
        "wrist_right" to "right_wrist",
        "hip_left" to "left_hip",
        "hip_right" to "right_hip",
        "knee_left" to "left_knee",
        "knee_right" to "right_knee",
        "ankle_left" to "left_ankle",
        "ankle_right" to "right_ankle",
    )

    val canonicalBones: List<Pair<String, String>> = SkeletonRenderContract.SharedPolicy.canonicalBones

    fun normalizeJointNames(joints: Map<String, JointPoint>): Map<String, JointPoint> = joints.entries
        .associate { (name, point) -> (jointAliases[name] ?: name) to point }

    fun normalizeJointName(name: String): String = jointAliases[name] ?: name
}

/**
 * Cross-surface consistency guardrail:
 * pass [SkeletonPreviewPolicies.*] unless introducing an explicitly reviewed global policy change.
 */
@Composable
fun OverlaySkeletonPreview(
    joints: Map<String, JointPoint>,
    modifier: Modifier = Modifier,
    policy: SkeletonRenderPolicy = SkeletonPreviewPolicies.canonical,
    resolveOverlayBounds: ((Size) -> Rect)? = null,
    highlightedJoint: String? = null,
    showBackground: Boolean = true,
    overlayContent: DrawScope.() -> Unit = {},
) {
    val normalizedJoints = remember(joints) { OverlaySkeletonPreviewDefaults.normalizeJointNames(joints) }
    val renderModel = remember(normalizedJoints) {
        val overlayJoints = normalizedJoints.entries.map { (name, point) ->
            OverlayJointPoint(
                name = name,
                x = point.x,
                y = point.y,
                z = 0f,
                visibility = 1f,
            )
        }
        val visibleNames = overlayJoints.map { it.name }.toSet()
        val connections = OverlaySkeletonPreviewDefaults.canonicalBones.filter { (from, to) ->
            from in visibleNames && to in visibleNames
        }
        OverlayRenderModel(
            joints = overlayJoints,
            connections = connections,
            idealLine = OverlayJointPoint("line_top", 0.5f, 0f, 0f, 0f) to OverlayJointPoint("line_bottom", 0.5f, 1f, 0f, 0f),
            centerOfGravity = null,
            showBalanceLane = false,
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(policy.aspectRatio)
            .then(
                if (showBackground) {
                    Modifier.background(Color(0x10101822), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            ),
    ) {
        overlayContent()

        val minDimension = minOf(size.width, size.height)
        val contentRect = resolveOverlayBounds?.invoke(size) ?: SkeletonRenderContract.contentRect(size, policy)

        OverlayFrameRenderer.drawAndroid(
            canvas = drawContext.canvas.nativeCanvas,
            width = size.width.toInt().coerceAtLeast(1),
            height = size.height.toInt().coerceAtLeast(1),
            model = renderModel,
            frame = OverlayDrawingFrame(
                drawSkeleton = normalizedJoints.isNotEmpty(),
                drawIdealLine = false,
                drawCenterOfGravity = false,
                sourceWidth = 1,
                sourceHeight = 1,
                sourceRotationDegrees = 0,
                mirrored = false,
                previewContentRect = contentRect,
                scaleMode = PoseScaleMode.FIT,
                renderTarget = OverlayRenderTarget.LIVE_PREVIEW,
                coordinateSpace = OverlayCoordinateSpace.UPRIGHT_NORMALIZED,
                styleScaleMultiplier = policy.styleScaleMultiplier,
                jointRadiusScaleMultiplier = policy.jointRadiusScaleMultiplier,
                strokeWidthScaleMultiplier = policy.strokeWidthScaleMultiplier,
            ),
        )

        highlightedJoint
            ?.let { normalizedJoints[OverlaySkeletonPreviewDefaults.normalizeJointName(it)] }
            ?.let { selected ->
                val cx = contentRect.left + (selected.x.coerceIn(0f, 1f) * contentRect.width)
                val cy = contentRect.top + (selected.y.coerceIn(0f, 1f) * contentRect.height)
                val baseRadius = (minDimension * 0.018f).coerceAtLeast(8f)
                drawCircle(
                    color = Color(0xCCFFFFFF),
                    radius = baseRadius,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = Stroke(width = (minDimension * 0.006f).coerceAtLeast(2f)),
                )
            }
    }
}
