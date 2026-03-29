package com.inversioncoach.app.drills.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickFigureAnimatorPhasePreviewTest {
    @Test
    fun interpolateTimeline_smoothlyInterpolatesGeneratedKeyframes() {
        val template = SkeletonTemplate(
            id = "s1",
            loop = false,
            framesPerSecond = 24,
            phasePoses = listOf(
                PhasePoseTemplate("phase_1", "Start", mapOf("head" to JointPoint(0.5f, 0.2f))),
                PhasePoseTemplate("phase_2", "End", mapOf("head" to JointPoint(0.5f, 0.8f))),
            ),
            keyframes = listOf(
                SkeletonKeyframeTemplate(0f, mapOf("head" to JointPoint(0.5f, 0.2f))),
                SkeletonKeyframeTemplate(1f, mapOf("head" to JointPoint(0.5f, 0.8f))),
            ),
        )

        val frames = StickFigureAnimator.interpolateTimeline(template, sampleCount = 5)
        assertEquals(5, frames.size)
        assertEquals(0.2f, frames.first().getValue("head").y, 0.0001f)
        assertEquals(0.8f, frames.last().getValue("head").y, 0.0001f)
        assertTrue(frames[2].getValue("head").y > 0.45f)
    }

    @Test
    fun canonicalBones_andPoseAt_useCanonicalJointNames() {
        assertTrue(StickFigureAnimator.canonicalBones.any { it.first == "head" && it.second == "shoulder_left" })
        val template = SkeletonTemplate(
            id = "s2",
            loop = false,
            framesPerSecond = 24,
            phasePoses = emptyList(),
            keyframes = listOf(
                SkeletonKeyframeTemplate(
                    progress = 0f,
                    joints = mapOf(
                        "head" to JointPoint(0.5f, 0.2f),
                        "shoulder_left" to JointPoint(0.4f, 0.3f),
                        "shoulder_right" to JointPoint(0.6f, 0.3f),
                    ),
                ),
                SkeletonKeyframeTemplate(
                    progress = 1f,
                    joints = mapOf(
                        "head" to JointPoint(0.5f, 0.25f),
                        "shoulder_left" to JointPoint(0.4f, 0.35f),
                        "shoulder_right" to JointPoint(0.6f, 0.35f),
                    ),
                ),
            ),
        )
        val pose = StickFigureAnimator.poseAt(template, 0.5f)
        assertFalse(pose.isEmpty())
        assertTrue(pose.containsKey("head"))
        assertTrue(pose.containsKey("shoulder_left"))
    }
}
