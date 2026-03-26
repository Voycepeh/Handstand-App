package com.inversioncoach.app.recording

import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.overlay.DrillCameraSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        val resolved = resolver.overlayAt(90L)
        assertNotNull(resolved)
        assertEquals(0f, resolved!!.smoothedLandmarks.first().x, 0.01f)
    }

    @Test
    fun returnsNullWhenRequestedTimestampIsOutsideTolerance() {
        val resolver = OverlayTimelineResolver(listOf(frame(0L, x = 0f)))
        val resolved = resolver.overlayAt(350L)
        assertEquals(null, resolved)
    }

    @Test
    fun resolvesWithIrregularDecoderTimestamps() {
        val resolver = OverlayTimelineResolver(
            listOf(
                frame(0L, x = 0.0f),
                frame(87L, x = 0.87f),
                frame(191L, x = 1.91f),
            ),
        )

        val decodedAt92 = resolver.overlayAt(92L)
        val decodedAt185 = resolver.overlayAt(185L)
        assertNotNull(decodedAt92)
        assertNotNull(decodedAt185)
        assertEquals(0.92f, decodedAt92!!.smoothedLandmarks.first().x, 0.03f)
        assertEquals(1.85f, decodedAt185!!.smoothedLandmarks.first().x, 0.03f)
    }

    @Test
    fun interpolationCarriesForwardUnreliableJointNames() {
        val resolver = OverlayTimelineResolver(
            listOf(
                frame(0L, x = 0.0f, unreliableJointNames = setOf("left_wrist")),
                frame(100L, x = 1.0f, unreliableJointNames = setOf("left_elbow")),
            ),
        )

        val mid = resolver.overlayAt(50L)
        assertNotNull(mid)
        assertTrue(mid!!.unreliableJointNames.contains("left_wrist"))
        assertTrue(mid.unreliableJointNames.contains("left_elbow"))
    }

    private fun frame(ts: Long, x: Float, unreliableJointNames: Set<String> = emptySet()) = AnnotatedOverlayFrame(
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
        unreliableJointNames = unreliableJointNames,
    )

    private fun joint(x: Float) = com.inversioncoach.app.model.JointPoint(
        name = "left_shoulder",
        x = x,
        y = 0f,
        z = 0f,
        visibility = 1f,
    )
}
