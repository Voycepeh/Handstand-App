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
    private val repository: SessionRepository,
    private val compositor: AnnotatedVideoCompositor,
    private val debugValidationEnabled: Boolean = false,
) {
    suspend fun export(
        sessionId: Long,
        rawVideoUri: String,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
        overlayFrames: List<AnnotatedOverlayFrame>,
    ): String? {
        if (overlayFrames.isEmpty()) {
            repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.FAILED)
            return null
        }
        repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.PROCESSING)
        Log.d(TAG, "export_start sessionId=$sessionId overlayFrames=${overlayFrames.size}")
        val renderedUri = compositor.export(
            rawVideoUri = rawVideoUri,
            drillType = drillType,
            drillCameraSide = drillCameraSide,
            overlayFrames = overlayFrames,
            debugValidation = debugValidationEnabled,
        )
        if (renderedUri.isNullOrBlank()) {
            repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=rendered_uri_empty")
            return null
        }
        val persisted = repository.saveAnnotatedVideoBlob(sessionId, renderedUri)
        repository.updateAnnotatedExportStatus(
            sessionId,
            if (persisted.isNullOrBlank()) AnnotatedExportStatus.FAILED else AnnotatedExportStatus.READY,
        )
        Log.d(TAG, "export_complete sessionId=$sessionId persistedUri=$persisted")
        return persisted
    }
}
