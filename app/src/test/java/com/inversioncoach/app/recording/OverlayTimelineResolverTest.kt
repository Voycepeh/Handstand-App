package com.inversioncoach.app.recording

import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.overlay.DrillCameraSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OverlayTimelineResolverTest {

    @Test
    fun interpolatesBetweenSamples() {
        val resolver = OverlayTimelineResolver(
            listOf(
                frame(0L, x = 0.0f),
                frame(100L, x = 1.0f),
            ),
        )

        val mid = resolver.overlayAt(50L)
        assertNotNull(mid)
        assertEquals(0.5f, mid!!.smoothedLandmarks.first().x, 0.01f)
    }

    @Test
    fun holdsLastSampleWhenTimelineIsSparse() {
        val resolver = OverlayTimelineResolver(listOf(frame(0L, x = 0f)))
        val resolved = resolver.overlayAt(100L)
        assertNotNull(resolved)
        assertEquals(0f, resolved!!.smoothedLandmarks.first().x, 0.01f)
    }

    private fun frame(ts: Long, x: Float) = AnnotatedOverlayFrame(
        timestampMs = ts,
        landmarks = listOf(joint(x)),
        smoothedLandmarks = listOf(joint(x)),
        confidence = 1f,
        sessionMode = SessionMode.DRILL,
        drillCameraSide = DrillCameraSide.LEFT,
        bodyVisible = true,
        showSkeleton = true,
        showIdealLine = true,
        mirrorMode = false,
    )

    private fun joint(x: Float) = com.inversioncoach.app.model.JointPoint(
        name = "left_shoulder",
        x = x,
        y = 0f,
        z = 0f,
        visibility = 1f,
    )
}
