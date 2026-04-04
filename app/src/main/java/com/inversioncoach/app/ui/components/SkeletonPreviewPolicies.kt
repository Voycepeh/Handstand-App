package com.inversioncoach.app.ui.components

/**
 * Cross-surface skeleton preview policies must stay unified.
 * Any sizing/styling changes should flow through [canonical].
 */
object SkeletonPreviewPolicies {
    val canonical: SkeletonRenderPolicy = SkeletonRenderContract.SharedPolicy
    val poseAuthoring: SkeletonRenderPolicy = canonical
    val motionPreview: SkeletonRenderPolicy = canonical
    val chooseDrillPreview: SkeletonRenderPolicy = canonical
}
