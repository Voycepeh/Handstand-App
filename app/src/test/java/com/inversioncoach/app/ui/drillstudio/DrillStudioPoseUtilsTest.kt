package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.drills.catalog.JointPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillStudioPoseUtilsTest {
    private val pose = mapOf(
        "head" to JointPoint(0.5f, 0.2f),
        "shoulder_left" to JointPoint(0.4f, 0.35f),
        "shoulder_right" to JointPoint(0.6f, 0.35f),
        "wrist_left" to JointPoint(0.35f, 0.5f),
        "wrist_right" to JointPoint(0.65f, 0.5f),
        "hip_left" to JointPoint(0.45f, 0.6f),
        "hip_right" to JointPoint(0.55f, 0.6f),
        "ankle_left" to JointPoint(0.45f, 0.85f),
        "ankle_right" to JointPoint(0.55f, 0.85f),
    )

    @Test
    fun mirrorWithSemanticSwap_swapsLeftRightJoints() {
        val mirrored = DrillStudioPoseUtils.mirrorWithSemanticSwap(pose)

        assertEquals(1f - pose.getValue("shoulder_right").x, mirrored.getValue("shoulder_left").x, 0.0001f)
        assertEquals(1f - pose.getValue("shoulder_left").x, mirrored.getValue("shoulder_right").x, 0.0001f)
    }

    @Test
    fun nearestJointWithinRadius_ignoresFarTouches() {
        val far = DrillStudioPoseUtils.nearestJointWithinRadius(pose, JointPoint(0.02f, 0.98f), hitRadius = 0.05f)
        assertNull(far)

        val near = DrillStudioPoseUtils.nearestJointWithinRadius(pose, JointPoint(0.41f, 0.35f), hitRadius = 0.06f)
        assertEquals("shoulder_left", near)
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
            joint = "wrist_left",
            target = JointPoint(-0.3f, 1.4f),
            bodyProfile = profile,
        )

        assertTrue(constrained.x in 0.05f..0.95f)
        assertTrue(constrained.y in 0.05f..0.95f)
        val shoulder = pose.getValue("shoulder_left")
        val length = kotlin.math.sqrt((constrained.x - shoulder.x) * (constrained.x - shoulder.x) + (constrained.y - shoulder.y) * (constrained.y - shoulder.y))
        assertTrue(length < 0.5f)
    }
}
