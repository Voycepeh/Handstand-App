package com.inversioncoach.app.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.inversioncoach.app.overlay.OverlaySkeletonSpec

data class SkeletonRenderPolicy(
    val aspectRatio: Float,
    val contentPaddingFraction: Float,
    val styleScaleMultiplier: Float,
    val jointRadiusScaleMultiplier: Float,
    val strokeWidthScaleMultiplier: Float,
    val canonicalBones: List<Pair<String, String>>,
)

object SkeletonRenderContract {
    val SharedPolicy = SkeletonRenderPolicy(
        aspectRatio = 3f / 4f,
        contentPaddingFraction = 0.12f,
        styleScaleMultiplier = 1f,
        jointRadiusScaleMultiplier = 1f,
        strokeWidthScaleMultiplier = 1f,
        canonicalBones = OverlaySkeletonSpec.sideConnections("left") +
            OverlaySkeletonSpec.sideConnections("right") +
            OverlaySkeletonSpec.bilateralConnectors,
    )

    fun contentRect(canvasSize: Size, policy: SkeletonRenderPolicy = SharedPolicy): Rect {
        val minDimension = minOf(canvasSize.width, canvasSize.height)
        val padding = minDimension * policy.contentPaddingFraction.coerceAtLeast(0f)
        return Rect(
            left = padding,
            top = padding,
            right = (canvasSize.width - padding).coerceAtLeast(padding + 1f),
            bottom = (canvasSize.height - padding).coerceAtLeast(padding + 1f),
        )
    }

    fun displayedImageBounds(
        canvasSize: Size,
        imageWidth: Int?,
        imageHeight: Int?,
        policy: SkeletonRenderPolicy = SharedPolicy,
    ): Rect {
        val contentRect = contentRect(canvasSize, policy)
        val width = imageWidth ?: return contentRect
        val height = imageHeight ?: return contentRect
        if (width <= 0 || height <= 0) return contentRect

        val imageAspect = width.toFloat() / height.toFloat()
        val contentAspect = if (contentRect.height == 0f) 1f else contentRect.width / contentRect.height
        return if (imageAspect > contentAspect) {
            val displayedHeight = contentRect.width / imageAspect
            Rect(
                left = contentRect.left,
                top = contentRect.top + (contentRect.height - displayedHeight) / 2f,
                right = contentRect.right,
                bottom = contentRect.top + (contentRect.height + displayedHeight) / 2f,
            )
        } else {
            val displayedWidth = contentRect.height * imageAspect
            Rect(
                left = contentRect.left + (contentRect.width - displayedWidth) / 2f,
                top = contentRect.top,
                right = contentRect.left + (contentRect.width + displayedWidth) / 2f,
                bottom = contentRect.bottom,
            )
        }
    }
}
