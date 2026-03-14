package com.inversioncoach.app.overlay

import com.inversioncoach.app.model.JointPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SideViewOverlayTest {

    @Test
    fun `determineTrackedSide keeps side stable until sustained advantage`() {
        val selector = TrackedSideSelector()

        repeat(5) {
            val state = selector.determineTrackedSide(buildSideVisibility(left = 0.95f, right = 0.2f))
            assertEquals(TrackedSide.LEFT, state.trackedSide)
        }

        repeat(5) {
            val state = selector.determineTrackedSide(buildSideVisibility(left = 0.2f, right = 0.98f))
            assertEquals(TrackedSide.LEFT, state.trackedSide)
        }

        val switched = selector.determineTrackedSide(buildSideVisibility(left = 0.2f, right = 0.98f))
        assertEquals(TrackedSide.RIGHT, switched.trackedSide)
    }

    @Test
    fun `getRenderableLandmarks returns side-view subset only`() {
        val landmarks = buildList {
            add(JointPoint("nose", 0.5f, 0.1f, 0f, 0.8f))
            add(JointPoint("left_shoulder", 0.4f, 0.2f, 0f, 0.9f))
            add(JointPoint("left_elbow", 0.35f, 0.3f, 0f, 0.9f))
            add(JointPoint("left_wrist", 0.3f, 0.4f, 0f, 0.9f))
            add(JointPoint("left_hip", 0.45f, 0.45f, 0f, 0.9f))
            add(JointPoint("left_knee", 0.46f, 0.62f, 0f, 0.9f))
            add(JointPoint("left_ankle", 0.47f, 0.82f, 0f, 0.9f))
            add(JointPoint("left_index", 0.2f, 0.1f, 0f, 0.9f))
            add(JointPoint("right_shoulder", 0.6f, 0.2f, 0f, 0.9f))
        }

        val renderable = getRenderableLandmarks(landmarks, TrackedSide.LEFT)

        assertTrue(renderable.any { it.name == "nose" })
        assertTrue(renderable.any { it.name == "left_shoulder" })
        assertTrue(renderable.none { it.name == "left_index" })
        assertTrue(renderable.none { it.name == "right_shoulder" })
    }

    private fun buildSideVisibility(left: Float, right: Float): List<JointPoint> {
        fun side(prefix: String, visibility: Float) = listOf(
            JointPoint("${prefix}_shoulder", 0.5f, 0.2f, 0f, visibility),
            JointPoint("${prefix}_elbow", 0.5f, 0.3f, 0f, visibility),
            JointPoint("${prefix}_wrist", 0.5f, 0.4f, 0f, visibility),
            JointPoint("${prefix}_hip", 0.5f, 0.5f, 0f, visibility),
            JointPoint("${prefix}_knee", 0.5f, 0.7f, 0f, visibility),
            JointPoint("${prefix}_ankle", 0.5f, 0.9f, 0f, visibility),
        )

        return side("left", left) + side("right", right)
    }
}
