package com.inversioncoach.app.ui.components

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkeletonRenderContractTest {

    @Test
    fun canonicalPolicy_isSharedAcrossPoseMotionAndChooseDrill() {
        assertEquals(SkeletonRenderContract.SharedPolicy, SkeletonPreviewPolicies.canonical)
        assertEquals(SkeletonPreviewPolicies.canonical, SkeletonPreviewPolicies.poseAuthoring)
        assertEquals(SkeletonPreviewPolicies.canonical, SkeletonPreviewPolicies.motionPreview)
        assertEquals(SkeletonPreviewPolicies.canonical, SkeletonPreviewPolicies.chooseDrillPreview)
    }

    @Test
    fun contentRect_andDisplayedImageBounds_shareSamePaddingBasis() {
        val canvas = Size(300f, 400f)
        val contentRect = SkeletonRenderContract.contentRect(canvas)
        assertEquals(36f, contentRect.left, 0.01f)
        assertEquals(36f, contentRect.top, 0.01f)
        assertEquals(264f, contentRect.right, 0.01f)
        assertEquals(364f, contentRect.bottom, 0.01f)

        val displayed = SkeletonRenderContract.displayedImageBounds(
            canvasSize = canvas,
            imageWidth = 1920,
            imageHeight = 1080,
        )
        assertEquals(contentRect.left, displayed.left, 0.01f)
        assertEquals(contentRect.right, displayed.right, 0.01f)
        assertTrue(displayed.height < contentRect.height)
    }
}
