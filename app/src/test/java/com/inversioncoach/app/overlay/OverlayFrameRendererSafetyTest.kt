package com.inversioncoach.app.overlay

import com.inversioncoach.app.model.JointPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFrameRendererSafetyTest {
    @Test
    fun lowVisibilityJointIsNotRenderable() {
        val lowVisibilityWrist = joint("left_wrist", 0.45f, 0.30f, visibility = 0.2f)

        assertFalse(OverlayFrameRenderer.isRenderableJoint(lowVisibilityWrist))
    }

    @Test
    fun connectionWithLowVisibilityEndpointIsNotRenderable() {
        val shoulder = joint("left_shoulder", 0.45f, 0.30f, visibility = 0.9f)
        val elbow = joint("left_elbow", 0.43f, 0.44f, visibility = 0.1f)

        assertFalse(OverlayFrameRenderer.areConnectionEndpointsRenderable(shoulder, elbow))
    }

    @Test
    fun validVisibleLimbSegmentRemainsRenderable() {
        val shoulder = joint("left_shoulder", 0.45f, 0.30f)
        val elbow = joint("left_elbow", 0.43f, 0.44f)

        assertTrue(OverlayFrameRenderer.areConnectionEndpointsRenderable(shoulder, elbow))
        assertTrue(OverlayFrameRenderer.isConnectionLengthSafe(shoulder, elbow))
    }

    @Test
    fun longSegmentSafetyRejectsImplausiblyLongSegments() {
        val shoulder = joint("left_shoulder", 0.45f, 0.30f)
        val wrist = joint("left_wrist", 2.5f, 3.8f)

        assertFalse(OverlayFrameRenderer.isConnectionLengthSafe(shoulder, wrist))
    }

    @Test
    fun explicitlyUnreliableJointIsRejected() {
        val shoulder = joint("left_shoulder", 0.45f, 0.30f)
        val unreliable = setOf("left_shoulder")

        assertFalse(OverlayFrameRenderer.isRenderableJoint(shoulder, unreliable))
    }

    @Test
    fun jointSafetyAllowsNearbyOutOfFramePointsButRejectsFarOutliers() {
        assertTrue(OverlayFrameRenderer.isSafeJointPoint(x = 1120f, y = 960f, canvasWidth = 1080f, canvasHeight = 1920f))
        assertFalse(OverlayFrameRenderer.isSafeJointPoint(x = 3000f, y = 960f, canvasWidth = 1080f, canvasHeight = 1920f))
    }

    private fun joint(name: String, x: Float, y: Float, visibility: Float = 0.9f): JointPoint = JointPoint(
        name = name,
        x = x,
        y = y,
        z = 0f,
        visibility = visibility,
    )
}
