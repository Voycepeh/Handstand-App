package com.inversioncoach.app.recording

import android.util.Log
import com.inversioncoach.app.model.AnnotatedExportFailureReason
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.FreestyleViewMode
import com.inversioncoach.app.storage.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val TAG = "AnnotatedExportPipeline"

data class AnnotatedOverlayFrame(
    val timestampMs: Long,
    val landmarks: List<JointPoint>,
    val smoothedLandmarks: List<JointPoint>,
    val confidence: Float,
    val sessionMode: SessionMode,
    val drillCameraSide: DrillCameraSide?,
    val freestyleViewMode: FreestyleViewMode = FreestyleViewMode.UNKNOWN,
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
        freestyleViewMode: FreestyleViewMode = FreestyleViewMode.UNKNOWN,
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
            freestyleViewMode = freestyleViewMode,
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
    private val composer: AnnotatedVideoComposer = AnnotatedVideoComposer(compositor),
    private val debugValidationEnabled: Boolean = false,
    private val persistAnnotatedVideo: suspend (Long, String) -> String?,
    private val updateExportStatus: suspend (Long, AnnotatedExportStatus) -> Unit,
    private val verifyMedia: (String?) -> MediaVerificationResult = { uri -> MediaVerificationHelper.verify(uri) },
    private val exportTimeoutMs: Long = EXPORT_TIMEOUT_MS,
    private val renderAnnotatedVideo: suspend (String, DrillType, DrillCameraSide, OverlayTimeline, ExportPreset, (Int, Int) -> Unit, (AnnotatedExportTelemetry) -> Unit) -> ComposerResult =
        { rawUri, drill, side, timeline, preset, onProgress, onTelemetry ->
            composer.compose(rawUri, timeline, drill, side, preset, onProgress, onTelemetry)
        },
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
        val started: Boolean = false,
        val telemetry: AnnotatedExportTelemetry? = null,
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
        verifyMedia: (String?) -> MediaVerificationResult = { uri -> MediaVerificationHelper.verify(uri) },
        exportTimeoutMs: Long = EXPORT_TIMEOUT_MS,
        renderAnnotatedVideo: suspend (String, DrillType, DrillCameraSide, OverlayTimeline, ExportPreset, (Int, Int) -> Unit, (AnnotatedExportTelemetry) -> Unit) -> ComposerResult,
    ) : this(
        compositor = throw IllegalStateException("Test constructor requires renderAnnotatedVideo"),
        debugValidationEnabled = false,
        persistAnnotatedVideo = persistAnnotatedVideo,
        updateExportStatus = updateExportStatus,
        verifyMedia = verifyMedia,
        exportTimeoutMs = exportTimeoutMs,
        renderAnnotatedVideo = renderAnnotatedVideo,
    )

    suspend fun export(
        sessionId: Long,
        rawVideoUri: String,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
        overlayTimeline: OverlayTimeline,
        preset: ExportPreset = ExportPreset.BALANCED,
        onRenderProgress: (Int, Int) -> Unit = { _, _ -> },
    ): ExportResult {
        if (rawVideoUri.isBlank()) {
            updateExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=raw_video_missing_pre_export")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.EXPORT_NOT_STARTED.name,
                verificationStatus = VerificationStatus.FAILED,
            )
        }
        if (overlayTimeline.frames.isEmpty()) {
            updateExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=overlay_frames_empty")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.OVERLAY_TIMELINE_EMPTY.name,
                verificationStatus = VerificationStatus.FAILED,
            )
        }
        var telemetry: AnnotatedExportTelemetry? = null
        updateExportStatus(sessionId, AnnotatedExportStatus.PROCESSING)
        Log.d(
            TAG,
            "export_start sessionId=$sessionId rawVideoUri=$rawVideoUri timelineFrames=${overlayTimeline.frames.size} " +
                "firstFrameTs=${overlayTimeline.frames.first().timestampMs} lastFrameTs=${overlayTimeline.frames.last().timestampMs}",
        )
        val composeResult = try {
            coroutineScope {
                var exportWorkStartedAtMs: Long? = null
                val renderJob = async {
                    renderAnnotatedVideo(rawVideoUri, drillType, drillCameraSide, overlayTimeline, preset, onRenderProgress) {
                        telemetry = it
                        if (it.decoderInitializedAtMs != null && exportWorkStartedAtMs == null) {
                            exportWorkStartedAtMs = System.currentTimeMillis()
                        }
                        Log.d(TAG, it.structuredLogLine(event = "export_progress"))
                    }
                }
                var timedOut = false
                while (!renderJob.isCompleted) {
                    val startedAt = exportWorkStartedAtMs
                    if (startedAt != null && System.currentTimeMillis() - startedAt >= exportTimeoutMs) {
                        timedOut = true
                        renderJob.cancel(CancellationException("export_timeout_after_work_started"))
                        break
                    }
                    delay(25)
                }
                if (timedOut) null else renderJob.await()
            }
        } catch (_: CancellationException) {
            updateExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=cancelled")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.EXPORT_CANCELLED.name,
                verificationStatus = VerificationStatus.FAILED,
                started = telemetry?.decoderInitializedAtMs != null,
                telemetry = telemetry,
            )
        } catch (t: Throwable) {
            updateExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            Log.e(TAG, "export_failure sessionId=$sessionId reason=exception_${t::class.simpleName}", t)
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.RENDER_PIPELINE_EXCEPTION.name,
                verificationStatus = VerificationStatus.FAILED,
                started = telemetry?.decoderInitializedAtMs != null,
                telemetry = telemetry,
            )
        }
        if (composeResult == null) {
            updateExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=timeout")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.EXPORT_TIMEOUT.name,
                verificationStatus = VerificationStatus.FAILED,
                started = telemetry?.decoderInitializedAtMs != null,
                telemetry = telemetry,
            )
        }
        if (composeResult.uri.isNullOrBlank()) {
            updateExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            val reason = composeResult.failureReason ?: AnnotatedExportFailureReason.EXPORT_RETURNED_EMPTY.name
            Log.w(TAG, "export_failure sessionId=$sessionId reason=$reason")
            return ExportResult(
                failureReason = reason,
                verificationStatus = VerificationStatus.FAILED,
                started = telemetry?.decoderInitializedAtMs != null,
                telemetry = telemetry,
            )
        }

        val persisted = persistAnnotatedVideo(sessionId, composeResult.uri)
        val verification = verifyMedia(persisted)
        val status = if (verification.isValid) AnnotatedExportStatus.ANNOTATED_READY else AnnotatedExportStatus.ANNOTATED_FAILED
        updateExportStatus(sessionId, status)
        if (!verification.isValid) {
            val failure = verification.failureReason?.name ?: AnnotatedExportFailureReason.VERIFICATION_FAILED.name
            Log.w(TAG, "export_failure sessionId=$sessionId reason=$failure")
            return ExportResult(
                failureReason = failure,
                verificationStatus = VerificationStatus.FAILED,
                started = telemetry?.decoderInitializedAtMs != null,
                telemetry = telemetry,
            )
        } else {
            Log.d(TAG, "export_complete sessionId=$sessionId persistedUri=$persisted")
            return ExportResult(
                persistedUri = persisted,
                verificationStatus = VerificationStatus.PASSED,
                started = telemetry?.decoderInitializedAtMs != null,
                telemetry = telemetry,
            )
        }
    }

    companion object {
        private const val EXPORT_TIMEOUT_MS = 25_000L
    }
}
