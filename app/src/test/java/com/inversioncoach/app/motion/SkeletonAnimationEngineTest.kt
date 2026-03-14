package com.inversioncoach.app.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkeletonAnimationEngineTest {

    private val spec = SkeletonAnimationSpec(
        id = "test",
        mirroredSupported = true,
        keyframes = listOf(
            SkeletonKeyframe(
                name = "start",
                progress = 0f,
                joints = mapOf(BodyJoint.LEFT_WRIST to NormalizedPoint(0.2f, 0.4f), BodyJoint.RIGHT_WRIST to NormalizedPoint(0.8f, 0.4f)),
            ),
            SkeletonKeyframe(
                name = "end",
                progress = 1f,
                joints = mapOf(BodyJoint.LEFT_WRIST to NormalizedPoint(0.4f, 0.6f), BodyJoint.RIGHT_WRIST to NormalizedPoint(0.6f, 0.6f)),
            ),
        ),
    )

    @Test
    fun interpolation_midpoint_is_linear() {
        val pose = SkeletonAnimationEngine.interpolate(spec, 0.5f)
        assertEquals(0.3f, pose[BodyJoint.LEFT_WRIST]?.x ?: 0f, 0.0001f)
        assertEquals(0.5f, pose[BodyJoint.LEFT_WRIST]?.y ?: 0f, 0.0001f)
    }

    @Test
    fun mirrored_pose_swaps_sides_and_flips_x() {
        val pose = SkeletonAnimationEngine.interpolate(spec, 0f, mirrored = true)
        assertEquals(0.2f, pose[BodyJoint.LEFT_WRIST]?.x ?: 0f, 0.0001f)
        assertEquals(0.8f, pose[BodyJoint.RIGHT_WRIST]?.x ?: 0f, 0.0001f)
    }

    @Test
    fun loop_continuity_wraps_progress() {
        val beforeWrap = SkeletonAnimationEngine.interpolate(spec, 0.98f)
        val afterWrap = SkeletonAnimationEngine.interpolate(spec, 1.02f)
        assertTrue((beforeWrap[BodyJoint.LEFT_WRIST]!!.x - afterWrap[BodyJoint.LEFT_WRIST]!!.x) < 0.05f)
    }

    @Test
    fun catalog_animation_can_be_loaded_by_id() {
        val animation = DrillCatalog.byAnimationId("push_up")
        assertNotNull(animation)
        assertTrue(animation!!.keyframes.isNotEmpty())
    }
}
