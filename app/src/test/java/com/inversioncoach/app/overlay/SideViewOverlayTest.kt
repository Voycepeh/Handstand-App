package com.inversioncoach.app.overlay

import com.inversioncoach.app.model.JointPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SideViewOverlayTest {

    @Test
    fun `classifier chooses front when bilateral spread is wide and face landmarks are visible`() {
        val classifier = FreestyleOrientationClassifier()
        val joints = listOf(
            JointPoint("left_shoulder", 0.2f, 0.2f, 0f, 0.9f),
            JointPoint("right_shoulder", 0.8f, 0.2f, 0f, 0.9f),
            JointPoint("left_hip", 0.25f, 0.5f, 0f, 0.9f),
            JointPoint("right_hip", 0.75f, 0.5f, 0f, 0.9f),
            JointPoint("nose", 0.5f, 0.12f, 0f, 0.9f),
            JointPoint("left_eye", 0.47f, 0.1f, 0f, 0.7f),
            JointPoint("right_eye", 0.53f, 0.1f, 0f, 0.7f),
        )

        assertEquals(FreestyleViewMode.FRONT, classifier.classify(joints))
    }

    @Test
    fun `classifier chooses back when bilateral spread is wide and face landmarks are not visible`() {
        val classifier = FreestyleOrientationClassifier()
        val joints = listOf(
            JointPoint("left_shoulder", 0.2f, 0.2f, 0f, 0.9f),
            JointPoint("right_shoulder", 0.8f, 0.2f, 0f, 0.9f),
            JointPoint("left_hip", 0.25f, 0.5f, 0f, 0.9f),
            JointPoint("right_hip", 0.75f, 0.5f, 0f, 0.9f),
            JointPoint("nose", 0.5f, 0.12f, 0f, 0.1f),
            JointPoint("left_eye", 0.47f, 0.1f, 0f, 0.1f),
            JointPoint("right_eye", 0.53f, 0.1f, 0f, 0.1f),
        )

        assertEquals(FreestyleViewMode.BACK, classifier.classify(joints))
    }

    @Test
    fun `drill rendering keeps only selected side chain`() {
        val joints = buildLeftRight()

        val left = OverlayGeometry.build(
            drillType = com.inversioncoach.app.model.DrillType.STANDARD_PUSH_UP,
            sessionMode = com.inversioncoach.app.model.SessionMode.DRILL,
            joints = joints,
            drillCameraSide = DrillCameraSide.LEFT,
        )
        val right = OverlayGeometry.build(
            drillType = com.inversioncoach.app.model.DrillType.STANDARD_PUSH_UP,
            sessionMode = com.inversioncoach.app.model.SessionMode.DRILL,
            joints = joints,
            drillCameraSide = DrillCameraSide.RIGHT,
        )

        assertTrue(left.joints.any { it.name == "left_shoulder" })
        assertTrue(left.joints.none { it.name == "right_shoulder" })
        assertTrue(right.joints.any { it.name == "right_shoulder" })
        assertTrue(right.joints.none { it.name == "left_shoulder" })
    }

    @Test
    fun `freestyle front keeps bilateral skeleton while hiding only low-confidence landmarks`() {
        val joints = buildLeftRight().map {
            if (it.name == "right_wrist") it.copy(visibility = 0.1f) else it
        } + listOf(
            JointPoint("left_eye", 0.47f, 0.1f, 0f, 0.8f),
            JointPoint("right_eye", 0.53f, 0.1f, 0f, 0.8f),
        )

        val front = OverlayGeometry.build(
            drillType = com.inversioncoach.app.model.DrillType.FREESTYLE,
            sessionMode = com.inversioncoach.app.model.SessionMode.FREESTYLE,
            joints = joints,
            drillCameraSide = DrillCameraSide.LEFT,
        )

        assertTrue(front.joints.any { it.name == "left_shoulder" })
        assertTrue(front.joints.any { it.name == "right_shoulder" })
        assertTrue(front.joints.none { it.name == "right_wrist" })
        assertTrue(front.connections.any { it.first == "left_shoulder" && it.second == "right_shoulder" })
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
