package com.inversioncoach.app.recording

import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.overlay.DrillCameraSide
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayTimelineRecorderTest {
    @Test
    fun recordsAtConfiguredCadence() {
        val recorder = OverlayTimelineRecorder(startedAtMs = 1_000L, sampleIntervalMs = 100L)
        recorder.record(frame(1_000L))
        recorder.record(frame(1_050L))
        recorder.record(frame(1_120L))

        val snapshot = recorder.snapshot()
        assertEquals(2, snapshot.frames.size)
        assertEquals(0L, snapshot.frames.first().relativeTimestampMs)
        assertEquals(120L, snapshot.frames.last().relativeTimestampMs)
        assertEquals(1_000L, snapshot.frames.first().timestampMs)
    }

    private fun frame(ts: Long) = OverlayTimelineFrame(
        sessionId = 7L,
        relativeTimestampMs = ts - 1_000L,
        absoluteVideoPtsUs = (ts - 1_000L) * 1_000L,
        timestampMs = ts,
        landmarks = emptyList(),
        smoothedLandmarks = emptyList(),
        skeletonLines = emptyList(),
        headPoint = null,
        hipPoint = null,
        idealLine = null,
        alignmentAngles = emptyMap(),
        visibilityFlags = emptyMap(),
        drillMetadata = OverlayDrillMetadata(
            sessionMode = SessionMode.DRILL,
            drillCameraSide = DrillCameraSide.LEFT,
            showSkeleton = true,
            showIdealLine = true,
            bodyVisible = true,
            mirrorMode = false,
        ),
    )
}
