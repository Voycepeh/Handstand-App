package com.inversioncoach.app.recording

import android.util.Log

private const val TAG = "OverlayTimelineRecorder"

class OverlayTimelineRecorder(
    private val startedAtMs: Long,
    private val sampleIntervalMs: Long = DEFAULT_SAMPLE_INTERVAL_MS,
) {
    private val frames = mutableListOf<OverlayTimelineFrame>()
    private var lastRecordedRelativeMs: Long = Long.MIN_VALUE

    fun record(frame: OverlayTimelineFrame) {
        val clampedRelativeMs = frame.relativeTimestampMs.coerceAtLeast(0L)
        val normalized = frame.copy(
            relativeTimestampMs = clampedRelativeMs,
            absoluteVideoPtsUs = frame.absoluteVideoPtsUs ?: (clampedRelativeMs * 1_000L),
            timestampMs = startedAtMs + clampedRelativeMs,
        )
        if (lastRecordedRelativeMs != Long.MIN_VALUE && normalized.relativeTimestampMs < lastRecordedRelativeMs) {
            Log.w(TAG, "overlay_sample_regression relativeMs=${normalized.relativeTimestampMs} lastMs=$lastRecordedRelativeMs")
            return
        }
        if (lastRecordedRelativeMs != Long.MIN_VALUE && normalized.relativeTimestampMs - lastRecordedRelativeMs < sampleIntervalMs) return
        frames += normalized
        lastRecordedRelativeMs = normalized.relativeTimestampMs
        if (frames.size % LOG_SAMPLE_INTERVAL == 0) {
            Log.d(TAG, "overlay_sample_recorded samples=${frames.size} relativeMs=${normalized.relativeTimestampMs} ptsUs=${normalized.absoluteVideoPtsUs}")
        }
    }

    fun snapshot(): OverlayTimeline = OverlayTimeline(
        startedAtMs = startedAtMs,
        sampleIntervalMs = sampleIntervalMs,
        frames = frames.toList(),
    )

    companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MS = 80L
        private const val LOG_SAMPLE_INTERVAL = 20
    }
}
