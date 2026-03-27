package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserBodyProfileBuilderTest {
    private val builder = UserBodyProfileBuilder()

    @Test
    fun buildsNonNullProfileFromValidCaptures() {
        val profile = builder.build(
            frontFrames = listOf(frontFrame()),
            sideFrames = listOf(sideFrame()),
            overheadFrames = listOf(overheadFrame()),
            holdFrames = listOf(holdFrame()),
        )

        assertNotNull(profile)
        assertTrue(profile.shoulderWidthNormalized > 0f)
        assertTrue(profile.torsoLengthNormalized > 0f)
    }

    @Test
    fun returnsConsistentNormalizedValuesForSymmetricPose() {
        val profile = builder.build(
            frontFrames = listOf(frontFrame()),
            sideFrames = listOf(sideFrame()),
            overheadFrames = listOf(overheadFrame()),
            holdFrames = emptyList(),
        )

        assertEquals(profile.shoulderWidthNormalized, profile.hipWidthNormalized, 0.0001f)
        assertTrue(profile.shoulderWidthNormalized > 1f)
    }

    @Test
    fun leftRightConsistencyDropsWhenArmsAsymmetric() {
        val symmetric = builder.build(
            frontFrames = listOf(frontFrame()),
            sideFrames = listOf(sideFrame()),
            overheadFrames = listOf(overheadFrame()),
            holdFrames = emptyList(),
        )
        val asymmetric = builder.build(
            frontFrames = listOf(frontFrame()),
            sideFrames = listOf(sideFrame()),
            overheadFrames = listOf(overheadFrame(rightElbowX = 0.62f)),
            holdFrames = emptyList(),
        )

        assertTrue(asymmetric.leftRightConsistency < symmetric.leftRightConsistency)
    }

    private fun frontFrame(): PoseFrame = frame(
        "left_shoulder" to (0.3f to 0.2f),
        "right_shoulder" to (0.7f to 0.2f),
        "left_hip" to (0.32f to 0.6f),
        "right_hip" to (0.72f to 0.6f),
        "left_knee" to (0.32f to 0.8f),
        "right_knee" to (0.72f to 0.8f),
        "left_ankle" to (0.32f to 0.95f),
        "right_ankle" to (0.72f to 0.95f),
        "left_elbow" to (0.2f to 0.35f),
        "right_elbow" to (0.8f to 0.35f),
        "left_wrist" to (0.15f to 0.5f),
        "right_wrist" to (0.85f to 0.5f),
    )

    private fun sideFrame(): PoseFrame = frame(
        "left_shoulder" to (0.5f to 0.2f),
        "right_shoulder" to (0.54f to 0.22f),
        "left_hip" to (0.52f to 0.6f),
        "right_hip" to (0.56f to 0.62f),
        "left_knee" to (0.52f to 0.8f),
        "right_knee" to (0.56f to 0.82f),
        "left_ankle" to (0.52f to 0.95f),
        "right_ankle" to (0.56f to 0.97f),
        "left_elbow" to (0.48f to 0.35f),
        "right_elbow" to (0.58f to 0.35f),
        "left_wrist" to (0.46f to 0.5f),
        "right_wrist" to (0.60f to 0.5f),
    )

    private fun overheadFrame(rightElbowX: Float = 0.8f): PoseFrame = frame(
        "left_shoulder" to (0.3f to 0.3f),
        "right_shoulder" to (0.7f to 0.3f),
        "left_elbow" to (0.2f to 0.15f),
        "right_elbow" to (rightElbowX to 0.15f),
        "left_wrist" to (0.1f to 0.05f),
        "right_wrist" to (0.9f to 0.05f),
        "left_hip" to (0.32f to 0.65f),
        "right_hip" to (0.72f to 0.65f),
        "left_knee" to (0.32f to 0.84f),
        "right_knee" to (0.72f to 0.84f),
        "left_ankle" to (0.32f to 0.98f),
        "right_ankle" to (0.72f to 0.98f),
    )

    private fun holdFrame(): PoseFrame = overheadFrame()

    private fun frame(vararg entries: Pair<String, Pair<Float, Float>>): PoseFrame = PoseFrame(
        timestampMs = 0L,
        joints = entries.map { (name, point) ->
            JointPoint(name = name, x = point.first, y = point.second, z = 0f, visibility = 0.99f)
        },
        confidence = 0.99f,
    )
}
