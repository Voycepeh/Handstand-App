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
import com.inversioncoach.app.pose.PoseScaleMode
import com.inversioncoach.app.storage.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val TAG = "AnnotatedExportPipeline"
private const val MAX_EXPORT_FPS = 12
private const val LONG_EXPORT_SESSION_MS = 30_000L
private const val BASE_EXPORT_TIMEOUT_MS = 180_000L
private const val MAX_EXPORT_TIMEOUT_MS = 300_000L
private const val EXPORT_TIMEOUT_PER_FRAME_MS = 80L
private const val EXPORT_TIMEOUT_DURATION_DIVISOR = 2L

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
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val sourceRotationDegrees: Int = 0,
    val scaleMode: PoseScaleMode = PoseScaleMode.FIT,
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
        scaleMode: PoseScaleMode = PoseScaleMode.FIT,
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
            mirrorMode = frame.mirrored,
            sourceWidth = frame.analysisWidth,
            sourceHeight = frame.analysisHeight,
            sourceRotationDegrees = frame.analysisRotationDegrees,
            scaleMode = scaleMode,
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
    private val exportTimeoutMs: Long = BASE_EXPORT_TIMEOUT_MS,
    private val renderAnnotatedVideo: suspend (String, DrillType, DrillCameraSide, OverlayTimeline, ExportPreset, (Int, Int) -> Unit, (AnnotatedExportTelemetry) -> Unit) -> ComposerResult =
        { rawUri, drill, side, timeline, preset, onProgress, onTelemetry ->
            composer.compose(rawUri, timeline, drill, side, preset, onProgress, onTelemetry)
        },
) {
    // Timeout scales with normalized timeline complexity so longer/denser sessions
    // receive more budget while still honoring a maximum cap.
    private fun computeExportTimeoutMs(overlayTimeline: OverlayTimeline): Long {
        if (overlayTimeline.frames.isEmpty()) return exportTimeoutMs

        val sortedFrames = overlayTimeline.frames.sortedBy { it.timestampMs }
        val firstTimestampMs = sortedFrames.first().timestampMs
        val lastTimestampMs = sortedFrames.last().timestampMs
        val durationMs = (lastTimestampMs - firstTimestampMs).coerceAtLeast(0L)

        val frameBudgetMs = sortedFrames.size * EXPORT_TIMEOUT_PER_FRAME_MS
        val durationBudgetMs = durationMs / EXPORT_TIMEOUT_DURATION_DIVISOR

        return maxOf(
            exportTimeoutMs,
            frameBudgetMs.toLong(),
            durationBudgetMs,
        ).coerceAtMost(MAX_EXPORT_TIMEOUT_MS)
    }

    private fun normalizedTimelineForExport(overlayTimeline: OverlayTimeline): OverlayTimeline {
        if (overlayTimeline.frames.isEmpty()) return overlayTimeline

        val sortedFrames = overlayTimeline.frames.sortedBy { it.timestampMs }
        val durationMs =
            (sortedFrames.last().timestampMs - sortedFrames.first().timestampMs).coerceAtLeast(0L)

        val targetFps =
            when {
                durationMs >= LONG_EXPORT_SESSION_MS -> MAX_EXPORT_FPS
                else -> MAX_EXPORT_FPS
            }

        val minSpacingMs = (1000L / targetFps).coerceAtLeast(1L)
        val normalizedFrames = ArrayList<OverlayTimelineFrame>(sortedFrames.size)
        var lastAcceptedTimestampMs = Long.MIN_VALUE

        for (frame in sortedFrames) {
            if (normalizedFrames.isEmpty()) {
                normalizedFrames += frame
                lastAcceptedTimestampMs = frame.timestampMs
                continue
            }

            val sameTimestamp = frame.timestampMs == lastAcceptedTimestampMs
            val tooClose = frame.timestampMs - lastAcceptedTimestampMs < minSpacingMs
            if (sameTimestamp || tooClose) continue

            normalizedFrames += frame
            lastAcceptedTimestampMs = frame.timestampMs
        }

        val finalFrames =
            when {
                normalizedFrames.isEmpty() -> listOf(sortedFrames.first())
                normalizedFrames.last().timestampMs != sortedFrames.last().timestampMs ->
                    normalizedFrames + sortedFrames.last()
                else -> normalizedFrames
            }

        Log.d(
            TAG,
            "export_timeline_normalized " +
                "inputFrames=${overlayTimeline.frames.size} " +
                "outputFrames=${finalFrames.size} " +
                "durationMs=$durationMs targetFps=$targetFps minSpacingMs=$minSpacingMs",
        )

        return overlayTimeline.copy(frames = finalFrames)
    }

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
        exportTimeoutMs: Long = BASE_EXPORT_TIMEOUT_MS,
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
        updateExportStatus(sessionId, AnnotatedExportStatus.VALIDATING_INPUT)
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
        val exportTimeline = normalizedTimelineForExport(overlayTimeline)
        val effectiveTimeoutMs = computeExportTimeoutMs(exportTimeline)
        if (exportTimeline.frames.isEmpty()) {
            updateExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=normalized_overlay_frames_empty")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.OVERLAY_TIMELINE_EMPTY.name,
                verificationStatus = VerificationStatus.FAILED,
            )
        }
        var telemetry: AnnotatedExportTelemetry? = null
        updateExportStatus(sessionId, AnnotatedExportStatus.PROCESSING)
        Log.d(
            TAG,
            "export_start sessionId=$sessionId rawVideoUri=$rawVideoUri " +
                "timelineFrames=${overlayTimeline.frames.size} " +
                "normalizedTimelineFrames=${exportTimeline.frames.size} " +
                "effectiveTimeoutMs=$effectiveTimeoutMs " +
                "firstFrameTs=${exportTimeline.frames.first().timestampMs} " +
                "lastFrameTs=${exportTimeline.frames.last().timestampMs}",
        )
        val composeResult = try {
            coroutineScope {
                var exportWorkStartedAtMs: Long? = null
                val renderJob = async {
                    renderAnnotatedVideo(rawVideoUri, drillType, drillCameraSide, exportTimeline, preset, onRenderProgress) {
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
                    if (startedAt != null && System.currentTimeMillis() - startedAt >= effectiveTimeoutMs) {
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
}
