package com.inversioncoach.app.recording

import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.pose.PoseScaleMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayTimelineJsonTest {
    @Test
    fun encodeDecodeRoundTripPreservesFrames() {
        val timeline = OverlayTimeline(
            startedAtMs = 100L,
            sampleIntervalMs = 80L,
            frames = listOf(
                OverlayTimelineFrame(
                    sessionId = 77L,
                    relativeTimestampMs = 20L,
                    absoluteVideoPtsUs = 20_000L,
                    timestampMs = 120L,
                    landmarks = emptyList(),
                    smoothedLandmarks = emptyList(),
                    skeletonLines = emptyList(),
                    headPoint = null,
                    hipPoint = null,
                    idealLine = null,
                    alignmentAngles = mapOf("hip" to 12.5f),
                    visibilityFlags = mapOf("bodyVisible" to true),
                    confidence = 0.8f,
                    captureWidth = 720,
                    captureHeight = 1280,
                    captureRotationDegrees = 90,
                    scaleMode = PoseScaleMode.FILL,
                    unreliableJointNames = setOf("left_wrist"),
                    drillMetadata = OverlayDrillMetadata(
                        sessionMode = SessionMode.DRILL,
                        drillCameraSide = DrillCameraSide.LEFT,
                        showSkeleton = true,
                        showIdealLine = true,
                        bodyVisible = true,
                        mirrorMode = false,
                    ),
                ),
            ),
        )

        val decoded = OverlayTimelineJson.decode(OverlayTimelineJson.encode(timeline))
        assertEquals(1, decoded.frames.size)
        assertEquals(77L, decoded.frames.first().sessionId)
        assertEquals(20L, decoded.frames.first().relativeTimestampMs)
        assertEquals(120L, decoded.frames.first().timestampMs)
        assertEquals(12.5f, decoded.frames.first().alignmentAngles["hip"])
        assertEquals(720, decoded.frames.first().captureWidth)
        assertEquals(1280, decoded.frames.first().captureHeight)
        assertEquals(90, decoded.frames.first().captureRotationDegrees)
        assertEquals(PoseScaleMode.FILL, decoded.frames.first().scaleMode)
        assertEquals(setOf("left_wrist"), decoded.frames.first().unreliableJointNames)
    }
}
