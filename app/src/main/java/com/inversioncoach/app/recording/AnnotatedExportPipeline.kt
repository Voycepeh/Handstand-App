package com.inversioncoach.app.recording

import android.util.Log
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.inversioncoach.app.model.AnnotatedExportFailureReason
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.storage.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.math.abs

private const val TAG = "AnnotatedExportPipeline"

data class AnnotatedOverlayFrame(
    val timestampMs: Long,
    val landmarks: List<JointPoint>,
    val smoothedLandmarks: List<JointPoint>,
    val confidence: Float,
    val sessionMode: SessionMode,
    val drillCameraSide: DrillCameraSide?,
    val bodyVisible: Boolean,
    val showSkeleton: Boolean,
    val showIdealLine: Boolean,
    val mirrorMode: Boolean,
)

class OverlayStabilizer {
    private var lastGood: List<JointPoint> = emptyList()
    private var lastGoodTimestampMs: Long = 0L

    fun stabilize(
        frame: SmoothedPoseFrame,
        sessionMode: SessionMode,
        drillCameraSide: DrillCameraSide?,
        showIdealLine: Boolean,
        showSkeleton: Boolean,
    ): AnnotatedOverlayFrame {
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
            sessionMode = sessionMode,
            drillCameraSide = drillCameraSide,
            bodyVisible = visibilityGood,
            showSkeleton = drawSkeleton && showSkeleton,
            showIdealLine = showIdealLine,
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
    private val exportTimeoutMs: Long = EXPORT_TIMEOUT_MS,
    private val renderAnnotatedVideo: suspend (String, DrillType, DrillCameraSide, List<AnnotatedOverlayFrame>, Boolean) -> String? =
        { rawUri, drill, side, frames, debug ->
            compositor.export(rawUri, drill, side, frames, debug)
        },
    private val verifyOutputAsset: (String?) -> String? = ::defaultVerifyOutput,
) {

    enum class VerificationStatus {
        PASSED,
        FAILED,
        NOT_RUN,
    }

    data class ExportResult(
        val persistedUri: String? = null,
        val failureReason: String? = null,
        val verificationStatus: VerificationStatus = VerificationStatus.NOT_RUN,
    )

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
        exportTimeoutMs: Long = EXPORT_TIMEOUT_MS,
        renderAnnotatedVideo: suspend (String, DrillType, DrillCameraSide, List<AnnotatedOverlayFrame>, Boolean) -> String?,
        verifyOutputAsset: (String?) -> String? = ::defaultVerifyOutput,
    ) : this(
        compositor = throw IllegalStateException("Test constructor requires renderAnnotatedVideo"),
        debugValidationEnabled = false,
        persistAnnotatedVideo = persistAnnotatedVideo,
        updateExportStatus = updateExportStatus,
        exportTimeoutMs = exportTimeoutMs,
        renderAnnotatedVideo = renderAnnotatedVideo,
        verifyOutputAsset = verifyOutputAsset,
    )

    suspend fun export(
        sessionId: Long,
        rawVideoUri: String,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
        overlayFrames: List<AnnotatedOverlayFrame>,
    ): ExportResult {
        if (overlayFrames.isEmpty()) {
            updateExportStatus(sessionId, AnnotatedExportStatus.FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=overlay_frames_empty")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.OVERLAY_FRAMES_EMPTY.name,
                verificationStatus = VerificationStatus.FAILED,
            )
        }
        updateExportStatus(sessionId, AnnotatedExportStatus.PROCESSING)
        Log.d(
            TAG,
            "export_start sessionId=$sessionId rawVideoUri=$rawVideoUri overlayFrames=${overlayFrames.size} " +
                "firstFrameTs=${overlayFrames.first().timestampMs} lastFrameTs=${overlayFrames.last().timestampMs}",
        )
        val renderedUri = try {
            withTimeout(exportTimeoutMs) {
                renderAnnotatedVideo(
                    rawVideoUri,
                    drillType,
                    drillCameraSide,
                    overlayFrames,
                    debugValidationEnabled,
                )
            }
        } catch (t: TimeoutCancellationException) {
            updateExportStatus(sessionId, AnnotatedExportStatus.FAILED)
            Log.w(TAG, "export_timeout sessionId=$sessionId timeoutMs=$exportTimeoutMs")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.EXPORT_TIMED_OUT.name,
                verificationStatus = VerificationStatus.FAILED,
            )
        } catch (t: CancellationException) {
            updateExportStatus(sessionId, AnnotatedExportStatus.FAILED)
            Log.w(TAG, "export_cancelled sessionId=$sessionId")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.EXPORT_CANCELLED.name,
                verificationStatus = VerificationStatus.FAILED,
            )
        } catch (t: Throwable) {
            updateExportStatus(sessionId, AnnotatedExportStatus.FAILED)
            Log.w(TAG, "export_exception sessionId=$sessionId reason=${t::class.simpleName}", t)
            return ExportResult(failureReason = "EXCEPTION_${t::class.simpleName ?: "UNKNOWN"}", verificationStatus = VerificationStatus.FAILED)
        }
        if (renderedUri.isNullOrBlank()) {
            updateExportStatus(sessionId, AnnotatedExportStatus.FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=output_uri_null")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.OUTPUT_URI_NULL.name,
                verificationStatus = VerificationStatus.FAILED,
            )
        }
        val persisted = persistAnnotatedVideo(sessionId, renderedUri)
        val failure = verifyOutputAsset(persisted)
        val status = if (failure == null) AnnotatedExportStatus.READY else AnnotatedExportStatus.FAILED
        updateExportStatus(sessionId, status)
        if (failure != null) {
            Log.w(TAG, "export_failure sessionId=$sessionId reason=$failure")
            return ExportResult(failureReason = failure, verificationStatus = VerificationStatus.FAILED)
        } else {
            Log.d(TAG, "export_complete sessionId=$sessionId persistedUri=$persisted")
            return ExportResult(persistedUri = persisted, verificationStatus = VerificationStatus.PASSED)
        }
    }

    companion object {
        private const val EXPORT_TIMEOUT_MS = 25_000L

        private fun defaultVerifyOutput(uri: String?): String? {
            if (uri.isNullOrBlank()) return AnnotatedExportFailureReason.OUTPUT_URI_NULL.name
            val path = runCatching { Uri.parse(uri).path }.getOrNull()
            val candidate = path?.let { File(it) }
            if (candidate == null || !candidate.exists()) return AnnotatedExportFailureReason.OUTPUT_FILE_MISSING.name
            if (candidate.length() <= 0L) return AnnotatedExportFailureReason.OUTPUT_FILE_ZERO_BYTES.name
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(candidate.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (duration > 0L) null else AnnotatedExportFailureReason.METADATA_UNREADABLE.name
            } catch (t: Throwable) {
                AnnotatedExportFailureReason.METADATA_UNREADABLE.name
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
}
