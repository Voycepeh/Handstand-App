package com.inversioncoach.app.recording

import android.util.Log
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.storage.repository.SessionRepository
import kotlin.math.abs

private const val TAG = "AnnotatedExportPipeline"

data class AnnotatedOverlayFrame(
    val timestampMs: Long,
    val landmarks: List<JointPoint>,
    val smoothedLandmarks: List<JointPoint>,
    val confidence: Float,
    val bodyVisible: Boolean,
    val drawSkeleton: Boolean,
    val drawIdealLine: Boolean,
    val orientation: SessionMode,
    val mirrorMode: Boolean,
)

class OverlayStabilizer {
    private var lastGood: List<JointPoint> = emptyList()
    private var lastGoodTimestampMs: Long = 0L

    fun stabilize(frame: SmoothedPoseFrame, sessionMode: SessionMode): AnnotatedOverlayFrame {
        val visibilityGood = frame.joints.count { it.visibility >= MIN_VISIBILITY } >= MIN_VISIBLE_JOINTS
        val confidenceGood = frame.confidence >= MIN_CONFIDENCE
        val jumpy = isJumpy(frame.joints)
        val shouldHold = (!visibilityGood || !confidenceGood || jumpy) &&
            lastGood.isNotEmpty() &&
            (frame.timestampMs - lastGoodTimestampMs) <= HOLD_LAST_GOOD_MS
        val stable = if (shouldHold) lastGood else frame.joints
        if (visibilityGood && confidenceGood && !jumpy) {
            lastGood = frame.joints
            lastGoodTimestampMs = frame.timestampMs
        }
        val drawSkeleton = stable.isNotEmpty() && (visibilityGood || shouldHold)
        return AnnotatedOverlayFrame(
            timestampMs = frame.timestampMs,
            landmarks = frame.joints,
            smoothedLandmarks = stable,
            confidence = frame.confidence,
            bodyVisible = visibilityGood,
            drawSkeleton = drawSkeleton,
            drawIdealLine = true,
            orientation = sessionMode,
            mirrorMode = false,
        )
    }

    private fun isJumpy(joints: List<JointPoint>): Boolean {
        if (lastGood.isEmpty()) return false
        val prev = lastGood.associateBy { it.name }
        val meanDelta = joints.mapNotNull { j ->
            prev[j.name]?.let { p -> abs(j.x - p.x) + abs(j.y - p.y) }
        }.average()
        return meanDelta > MAX_MEAN_JUMP
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.45f
        private const val MIN_VISIBILITY = 0.35f
        private const val MIN_VISIBLE_JOINTS = 5
        private const val HOLD_LAST_GOOD_MS = 250L
        private const val MAX_MEAN_JUMP = 0.25
    }
}

class AnnotatedExportPipeline(
    private val compositor: AnnotatedVideoCompositor,
    private val debugValidationEnabled: Boolean = false,
    private val persistAnnotatedVideo: suspend (Long, String) -> String?,
    private val updateExportStatus: suspend (Long, AnnotatedExportStatus) -> Unit,
    private val renderAnnotatedVideo: suspend (String, DrillType, DrillCameraSide, List<AnnotatedOverlayFrame>, Boolean) -> String? =
        { rawUri, drill, side, frames, debug ->
            compositor.export(rawUri, drill, side, frames, debug)
        },
) {
    constructor(
        repository: SessionRepository,
        compositor: AnnotatedVideoCompositor,
        debugValidationEnabled: Boolean = false,
    ) : this(
        compositor = compositor,
        debugValidationEnabled = debugValidationEnabled,
        persistAnnotatedVideo = repository::saveAnnotatedVideoBlob,
        updateExportStatus = repository::updateAnnotatedExportStatus,
    )

    internal constructor(
        persistAnnotatedVideo: suspend (Long, String) -> String?,
        updateExportStatus: suspend (Long, AnnotatedExportStatus) -> Unit,
        renderAnnotatedVideo: suspend (String, DrillType, DrillCameraSide, List<AnnotatedOverlayFrame>, Boolean) -> String?,
    ) : this(
        compositor = throw IllegalStateException("Test constructor requires renderAnnotatedVideo"),
        debugValidationEnabled = false,
        persistAnnotatedVideo = persistAnnotatedVideo,
        updateExportStatus = updateExportStatus,
        renderAnnotatedVideo = renderAnnotatedVideo,
    )

    suspend fun export(
        sessionId: Long,
        rawVideoUri: String,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
        overlayFrames: List<AnnotatedOverlayFrame>,
    ): String? {
        if (overlayFrames.isEmpty()) {
            updateExportStatus(sessionId, AnnotatedExportStatus.FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=overlay_frames_empty")
            return null
        }
        updateExportStatus(sessionId, AnnotatedExportStatus.PROCESSING)
        Log.d(
            TAG,
            "export_start sessionId=$sessionId rawVideoUri=$rawVideoUri overlayFrames=${overlayFrames.size} " +
                "firstFrameTs=${overlayFrames.first().timestampMs} lastFrameTs=${overlayFrames.last().timestampMs}",
        )
        val renderedUri = renderAnnotatedVideo(
            rawVideoUri,
            drillType,
            drillCameraSide,
            overlayFrames,
            debugValidationEnabled,
        )
        if (renderedUri.isNullOrBlank()) {
            updateExportStatus(sessionId, AnnotatedExportStatus.FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=rendered_uri_empty")
            return null
        }
        val persisted = persistAnnotatedVideo(sessionId, renderedUri)
        val status = if (persisted.isNullOrBlank()) AnnotatedExportStatus.FAILED else AnnotatedExportStatus.READY
        updateExportStatus(sessionId, status)
        if (persisted.isNullOrBlank()) {
            Log.w(TAG, "export_failure sessionId=$sessionId reason=persist_annotated_failed")
        } else {
            Log.d(TAG, "export_complete sessionId=$sessionId persistedUri=$persisted")
        }
        return persisted
    }
}
