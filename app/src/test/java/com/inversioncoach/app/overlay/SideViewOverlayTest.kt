package com.inversioncoach.app.overlay

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SideViewOverlayTest {

    @Test
    fun `front-facing pose classifies as front`() {
        val classifier = FreestyleOrientationClassifier()
        val joints = bilateral(faceVisible = true)

        repeat(3) { classifier.classify(joints) }

        assertEquals(FreestyleViewMode.FRONT, classifier.classify(joints))
    }

    @Test
    fun `back-facing pose classifies as back`() {
        val classifier = FreestyleOrientationClassifier()
        val joints = bilateral(faceVisible = false)

        repeat(3) { classifier.classify(joints) }

        assertEquals(FreestyleViewMode.BACK, classifier.classify(joints))
    }

    @Test
    fun `left profile pose classifies as left profile`() {
        val classifier = FreestyleOrientationClassifier()
        val joints = leftProfile()

        repeat(3) { classifier.classify(joints) }

        assertEquals(FreestyleViewMode.LEFT_PROFILE, classifier.classify(joints))
    }

    @Test
    fun `right profile pose classifies as right profile`() {
        val classifier = FreestyleOrientationClassifier()
        val joints = rightProfile()

        repeat(3) { classifier.classify(joints) }

        assertEquals(FreestyleViewMode.RIGHT_PROFILE, classifier.classify(joints))
    }

    @Test
    fun `unstable partial pose stays unknown then holds last stable`() {
        val classifier = FreestyleOrientationClassifier()
        val front = bilateral(faceVisible = true)
        val unstable = listOf(
            JointPoint("left_shoulder", 0.45f, 0.2f, 0f, 0.3f),
            JointPoint("right_shoulder", 0.55f, 0.2f, 0f, 0.3f),
        )

        assertEquals(FreestyleViewMode.UNKNOWN, classifier.classify(unstable))

        repeat(3) { classifier.classify(front) }
        assertEquals(FreestyleViewMode.FRONT, classifier.classify(front))

        assertEquals(FreestyleViewMode.FRONT, classifier.classify(unstable))
    }

    @Test
    fun `drill rendering keeps only selected side chain`() {
        val joints = bilateral(faceVisible = true)

        val left = OverlayGeometry.build(
            drillType = DrillType.STANDARD_PUSH_UP,
            sessionMode = SessionMode.DRILL,
            joints = joints,
            drillCameraSide = DrillCameraSide.LEFT,
        )
        val right = OverlayGeometry.build(
            drillType = DrillType.STANDARD_PUSH_UP,
            sessionMode = SessionMode.DRILL,
            joints = joints,
            drillCameraSide = DrillCameraSide.RIGHT,
        )

        assertTrue(left.joints.any { it.name == "left_shoulder" })
        assertTrue(left.joints.none { it.name == "right_shoulder" })
        assertTrue(right.joints.any { it.name == "right_shoulder" })
        assertTrue(right.joints.none { it.name == "left_shoulder" })
    }

    @Test
    fun `drill center of gravity uses full detected body not rendered side chain`() {
        val joints = bilateral(faceVisible = true)

        val left = OverlayGeometry.build(
            drillType = DrillType.STANDARD_PUSH_UP,
            sessionMode = SessionMode.DRILL,
            joints = joints,
            drillCameraSide = DrillCameraSide.LEFT,
            effectiveView = EffectiveView.SIDE,
        )
        val right = OverlayGeometry.build(
            drillType = DrillType.STANDARD_PUSH_UP,
            sessionMode = SessionMode.DRILL,
            joints = joints,
            drillCameraSide = DrillCameraSide.RIGHT,
            effectiveView = EffectiveView.SIDE,
        )

        val leftCenter = requireNotNull(left.centerOfGravity)
        val rightCenter = requireNotNull(right.centerOfGravity)

        assertEquals(0.5f, leftCenter.x, 0.001f)
        assertEquals(0.398f, leftCenter.y, 0.001f)
        assertEquals(leftCenter.x, rightCenter.x, 0.0001f)
        assertEquals(leftCenter.y, rightCenter.y, 0.0001f)
    }

    @Test
    fun `freestyle profile vs bilateral overlay selection`() {
        val joints = bilateral(faceVisible = true)
        val front = OverlayGeometry.build(
            drillType = DrillType.FREESTYLE,
            sessionMode = SessionMode.FREESTYLE,
            joints = joints,
            drillCameraSide = DrillCameraSide.LEFT,
            freestyleViewMode = FreestyleViewMode.FRONT,
        )
        val leftProfile = OverlayGeometry.build(
            drillType = DrillType.FREESTYLE,
            sessionMode = SessionMode.FREESTYLE,
            joints = joints,
            drillCameraSide = DrillCameraSide.LEFT,
            freestyleViewMode = FreestyleViewMode.LEFT_PROFILE,
        )

        assertTrue(front.joints.any { it.name == "left_shoulder" && it.visibility > 0.3f })
        assertTrue(front.joints.any { it.name == "right_shoulder" && it.visibility > 0.3f })
        assertTrue(front.connections.any { it.first == "left_shoulder" && it.second == "right_shoulder" })

        assertTrue(leftProfile.joints.any { it.name == "left_shoulder" })
        assertTrue(leftProfile.joints.none { it.name == "right_shoulder" })
        assertTrue(leftProfile.connections.none { it.first == "left_shoulder" && it.second == "right_shoulder" })
    }

    private fun bilateral(faceVisible: Boolean): List<JointPoint> {
        val faceVisibility = if (faceVisible) 0.9f else 0.1f
        return listOf(
            JointPoint("nose", 0.5f, 0.1f, 0f, faceVisibility),
            JointPoint("left_eye", 0.47f, 0.1f, 0f, faceVisibility),
            JointPoint("right_eye", 0.53f, 0.1f, 0f, faceVisibility),
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

    private fun leftProfile(): List<JointPoint> = listOf(
        JointPoint("nose", 0.45f, 0.1f, 0f, 0.8f),
        JointPoint("left_shoulder", 0.46f, 0.2f, 0f, 0.9f),
        JointPoint("left_elbow", 0.45f, 0.3f, 0f, 0.9f),
        JointPoint("left_wrist", 0.44f, 0.4f, 0f, 0.9f),
        JointPoint("left_hip", 0.47f, 0.5f, 0f, 0.9f),
        JointPoint("left_knee", 0.48f, 0.65f, 0f, 0.9f),
        JointPoint("left_ankle", 0.49f, 0.85f, 0f, 0.9f),
        JointPoint("right_shoulder", 0.49f, 0.21f, 0f, 0.2f),
        JointPoint("right_hip", 0.5f, 0.51f, 0f, 0.2f),
    )

    private fun rightProfile(): List<JointPoint> = listOf(
        JointPoint("nose", 0.55f, 0.1f, 0f, 0.8f),
        JointPoint("right_shoulder", 0.54f, 0.2f, 0f, 0.9f),
        JointPoint("right_elbow", 0.55f, 0.3f, 0f, 0.9f),
        JointPoint("right_wrist", 0.56f, 0.4f, 0f, 0.9f),
        JointPoint("right_hip", 0.53f, 0.5f, 0f, 0.9f),
        JointPoint("right_knee", 0.52f, 0.65f, 0f, 0.9f),
        JointPoint("right_ankle", 0.51f, 0.85f, 0f, 0.9f),
        JointPoint("left_shoulder", 0.51f, 0.21f, 0f, 0.2f),
        JointPoint("left_hip", 0.5f, 0.51f, 0f, 0.2f),
    )
}
