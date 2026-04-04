package com.inversioncoach.app.recording

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayTimelineTimestampMappingTest {
    @Test
    fun toAnnotatedOverlayFrameUsesPresentationTimestamp() {
        val frame = OverlayTimelineFrame(
            sessionId = 1L,
            relativeTimestampMs = 200L,
            timestampMs = 5_000L,
            landmarks = listOf(JointPoint("NOSE", 0.5f, 0.5f, 0f, 1f)),
            skeletonLines = emptyList(),
            headPoint = null,
            hipPoint = null,
            idealLine = null,
            alignmentAngles = emptyMap(),
            visibilityFlags = emptyMap(),
            drillMetadata = OverlayDrillMetadata(
                sessionMode = SessionMode.FREESTYLE,
                drillCameraSide = null,
                showSkeleton = true,
                showIdealLine = true,
                bodyVisible = true,
                mirrorMode = false,
            ),
        )

        assertEquals(5_000L, frame.toAnnotatedOverlayFrame().timestampMs)
    }
}
