package com.inversioncoach.app.overlay

import com.inversioncoach.app.model.JointPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SideViewOverlayTest {

    @Test
    fun `classifier chooses bilateral when shoulder spread is wide`() {
        val classifier = FreestyleOrientationClassifier()
        val joints = listOf(
            JointPoint("left_shoulder", 0.2f, 0.2f, 0f, 0.9f),
            JointPoint("right_shoulder", 0.8f, 0.2f, 0f, 0.9f),
            JointPoint("left_hip", 0.25f, 0.5f, 0f, 0.9f),
            JointPoint("right_hip", 0.75f, 0.5f, 0f, 0.9f),
        )

        assertEquals(FreestyleViewMode.BILATERAL_VIEW, classifier.classify(joints))
    }

    @Test
    fun `drill strategy renders only selected side chain`() {
        val strategy = FixedDrillSideOverlayStrategy()
        val joints = buildLeftRight()

        val left = strategy.build(joints, DrillCameraSide.LEFT)
        val right = strategy.build(joints, DrillCameraSide.RIGHT)

        assertTrue(left.joints.any { it.name == "left_shoulder" })
        assertTrue(left.joints.none { it.name == "right_shoulder" })
        assertTrue(right.joints.any { it.name == "right_shoulder" })
        assertTrue(right.joints.none { it.name == "left_shoulder" })
    }

    private fun buildLeftRight(): List<JointPoint> = listOf(
        JointPoint("nose", 0.5f, 0.1f, 0f, 0.9f),
        JointPoint("left_shoulder", 0.35f, 0.2f, 0f, 0.9f),
        JointPoint("left_elbow", 0.32f, 0.3f, 0f, 0.9f),
        JointPoint("left_wrist", 0.31f, 0.4f, 0f, 0.9f),
        JointPoint("left_hip", 0.4f, 0.5f, 0f, 0.9f),
        JointPoint("left_knee", 0.41f, 0.65f, 0f, 0.9f),
        JointPoint("left_ankle", 0.42f, 0.85f, 0f, 0.9f),
        JointPoint("right_shoulder", 0.65f, 0.2f, 0f, 0.9f),
        JointPoint("right_elbow", 0.67f, 0.3f, 0f, 0.9f),
        JointPoint("right_wrist", 0.69f, 0.4f, 0f, 0.9f),
        JointPoint("right_hip", 0.6f, 0.5f, 0f, 0.9f),
        JointPoint("right_knee", 0.59f, 0.65f, 0f, 0.9f),
        JointPoint("right_ankle", 0.58f, 0.85f, 0f, 0.9f),
    )
}
