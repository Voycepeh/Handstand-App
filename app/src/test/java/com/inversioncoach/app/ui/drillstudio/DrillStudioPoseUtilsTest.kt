package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.drills.catalog.JointPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillStudioPoseUtilsTest {
    private val pose = mapOf(
        "nose" to JointPoint(0.5f, 0.2f),
        "left_shoulder" to JointPoint(0.4f, 0.35f),
        "right_shoulder" to JointPoint(0.6f, 0.35f),
        "left_elbow" to JointPoint(0.37f, 0.42f),
        "right_elbow" to JointPoint(0.63f, 0.42f),
        "left_wrist" to JointPoint(0.35f, 0.5f),
        "right_wrist" to JointPoint(0.65f, 0.5f),
        "left_hip" to JointPoint(0.45f, 0.6f),
        "right_hip" to JointPoint(0.55f, 0.6f),
        "left_knee" to JointPoint(0.45f, 0.72f),
        "right_knee" to JointPoint(0.55f, 0.72f),
        "left_ankle" to JointPoint(0.45f, 0.85f),
        "right_ankle" to JointPoint(0.55f, 0.85f),
    )

    @Test
    fun mirrorWithSemanticSwap_swapsLeftRightJoints() {
        val mirrored = DrillStudioPoseUtils.mirrorWithSemanticSwap(pose)

        assertEquals(1f - pose.getValue("right_shoulder").x, mirrored.getValue("left_shoulder").x, 0.0001f)
        assertEquals(1f - pose.getValue("left_shoulder").x, mirrored.getValue("right_shoulder").x, 0.0001f)
    }

    @Test
    fun nearestJointWithinRadius_ignoresFarTouches() {
        val far = DrillStudioPoseUtils.nearestJointWithinRadius(pose, JointPoint(0.02f, 0.98f), hitRadius = 0.05f)
        assertNull(far)

        val near = DrillStudioPoseUtils.nearestJointWithinRadius(pose, JointPoint(0.41f, 0.35f), hitRadius = 0.06f)
        assertEquals("left_shoulder", near)
    }

    @Test
    fun applyAnatomicalGuardrails_clampsExtremeTargetAndUsesBodyProfileSoftly() {
        val profile = UserBodyProfile(
            shoulderWidthNormalized = 0.2f,
            hipWidthNormalized = 0.12f,
            torsoLengthNormalized = 0.25f,
            upperArmLengthNormalized = 0.12f,
            forearmLengthNormalized = 0.12f,
            femurLengthNormalized = 0.2f,
            shinLengthNormalized = 0.2f,
            leftRightConsistency = 0.95f,
        )

        val constrained = DrillStudioPoseUtils.applyAnatomicalGuardrails(
            pose = pose,
            joint = "left_wrist",
            target = JointPoint(-0.3f, 1.4f),
            bodyProfile = profile,
        )

        assertTrue(constrained.x in 0.05f..0.95f)
        assertTrue(constrained.y in 0.05f..0.95f)
        val shoulder = pose.getValue("left_shoulder")
        val length = kotlin.math.sqrt((constrained.x - shoulder.x) * (constrained.x - shoulder.x) + (constrained.y - shoulder.y) * (constrained.y - shoulder.y))
        assertTrue(length < 0.5f)
    }

    @Test
    fun renderPoseWithFallback_keepsCanonicalSkeletonWhenPoseIsSparse() {
        val sparsePose = mapOf(
            "head" to JointPoint(0.52f, 0.18f),
            "wrist_left" to JointPoint(0.30f, 0.52f),
        )

        val renderPose = DrillStudioPoseUtils.renderPoseWithFallback(
            joints = sparsePose,
            fallback = DrillStudioPosePresets.neutralUpright.joints,
        )

        assertTrue(renderPose.keys.containsAll(DrillStudioPoseUtils.connectedPairs.flatMap { listOf(it.first, it.second) }.distinct()))
        assertEquals(0.52f, renderPose.getValue("nose").x, 0.0001f)
        assertEquals(0.30f, renderPose.getValue("left_wrist").x, 0.0001f)
    }
}
