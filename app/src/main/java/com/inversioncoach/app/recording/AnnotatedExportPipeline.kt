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
private const val BASE_EXPORT_TIMEOUT_MS = 30_000L
private const val MAX_EXPORT_TIMEOUT_MS = 300_000L
private const val DEFAULT_STALL_WINDOW_MS = 20_000L
private const val SOFT_SLOW_EXPORT_THRESHOLD_MS = 90_000L
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
    val unreliableJointNames: Set<String> = emptySet(),
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
        unreliableJointNames: Set<String> = emptySet(),
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
            unreliableJointNames = unreliableJointNames,
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
    private val stallWindowMs: Long = DEFAULT_STALL_WINDOW_MS,
    private val renderAnnotatedVideo: suspend (String, DrillType, DrillCameraSide, OverlayTimeline, ExportPreset, (Int, Int) -> Unit, (AnnotatedExportTelemetry) -> Unit) -> ComposerResult =
        { rawUri, drill, side, timeline, preset, onProgress, onTelemetry ->
            composer.compose(rawUri, timeline, drill, side, preset, onProgress, onTelemetry)
        },
) {
    // Timeout scales with normalized timeline complexity so longer/denser sessions
    // receive more budget while still honoring a maximum cap.
    internal fun computeDynamicTimeoutMs(
        rawDurationMs: Long,
        width: Int,
        height: Int,
        overlayFrameCount: Int,
    ): Long {
        val durationBudgetMs = (rawDurationMs.coerceAtLeast(0L) * 2L)
        val resolutionPixels = width.coerceAtLeast(0).toLong() * height.coerceAtLeast(0).toLong()
        val resolutionBudgetMs = when {
            resolutionPixels >= 3840L * 2160L -> 80_000L
            resolutionPixels >= 1920L * 1080L -> 40_000L
            resolutionPixels >= 1280L * 720L -> 20_000L
            else -> 0L
        }
        val overlayBudgetMs = (overlayFrameCount.coerceAtLeast(0).toLong() * 30L).coerceAtMost(90_000L)
        return (exportTimeoutMs + durationBudgetMs + resolutionBudgetMs + overlayBudgetMs)
            .coerceAtMost(MAX_EXPORT_TIMEOUT_MS)
    }

    internal fun freezeSnapshotForExport(
        overlayTimeline: OverlayTimeline,
        rawDurationMsHint: Long? = null,
    ): FrozenExportSnapshot {
        val sortedFrames = overlayTimeline.frames.sortedBy { it.timestampMs }
        val clampedFrames = if (rawDurationMsHint != null && rawDurationMsHint > 0L) {
            sortedFrames.filter { it.timestampMs <= rawDurationMsHint }
        } else {
            sortedFrames
        }
        val minSpacingMs = (1_000L / MAX_EXPORT_FPS).coerceAtLeast(1L)
        val normalizedFrames = ArrayList<OverlayTimelineFrame>(clampedFrames.size)
        var lastAcceptedTimestampMs = Long.MIN_VALUE
        for (frame in clampedFrames) {
            if (normalizedFrames.isEmpty()) {
                normalizedFrames += frame
                lastAcceptedTimestampMs = frame.timestampMs
                continue
            }
            val isDuplicateTs = frame.timestampMs == lastAcceptedTimestampMs
            val isTooClose = (frame.timestampMs - lastAcceptedTimestampMs) < minSpacingMs
            if (isDuplicateTs || isTooClose) continue
            normalizedFrames += frame
            lastAcceptedTimestampMs = frame.timestampMs
        }
        if (normalizedFrames.isNotEmpty() && normalizedFrames.last().timestampMs != clampedFrames.lastOrNull()?.timestampMs) {
            clampedFrames.lastOrNull()?.let { normalizedFrames += it }
        }
        val usableFrames = if (normalizedFrames.isNotEmpty()) normalizedFrames else clampedFrames
        val timeline = overlayTimeline.copy(frames = usableFrames)
        val first = usableFrames.firstOrNull()
        val last = usableFrames.lastOrNull()
        val resolvedRawDurationMs = rawDurationMsHint?.takeIf { it > 0L } ?: if (first != null && last != null) {
            (last.timestampMs - first.timestampMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val validMetadataFrame = usableFrames.firstOrNull {
            (it.captureWidth ?: 0) > 0 &&
                (it.captureHeight ?: 0) > 0 &&
                ((it.captureRotationDegrees ?: 0) in setOf(0, 90, 180, 270))
        }
        val sourceWidth = validMetadataFrame?.captureWidth ?: 0
        val sourceHeight = validMetadataFrame?.captureHeight ?: 0
        val sourceRotationDegrees = validMetadataFrame?.captureRotationDegrees ?: 0
        return FrozenExportSnapshot(
            overlayTimeline = timeline,
            usableOverlayFrameCount = usableFrames.size,
            rawOverlayFrameCount = overlayTimeline.frames.size,
            rawDurationMs = resolvedRawDurationMs,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            sourceRotationDegrees = sourceRotationDegrees,
        )
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
        stallWindowMs: Long = DEFAULT_STALL_WINDOW_MS,
        renderAnnotatedVideo: suspend (String, DrillType, DrillCameraSide, OverlayTimeline, ExportPreset, (Int, Int) -> Unit, (AnnotatedExportTelemetry) -> Unit) -> ComposerResult,
    ) : this(
        compositor = throw IllegalStateException("Test constructor requires renderAnnotatedVideo"),
        debugValidationEnabled = false,
        persistAnnotatedVideo = persistAnnotatedVideo,
        updateExportStatus = updateExportStatus,
        verifyMedia = verifyMedia,
        exportTimeoutMs = exportTimeoutMs,
        stallWindowMs = stallWindowMs,
        renderAnnotatedVideo = renderAnnotatedVideo,
    )

    data class ExportProgressSnapshot(
        val percent: Int,
        val decodedFrames: Int,
        val renderedFrames: Int,
        val encodedFrames: Int,
        val outputBytes: Long,
    )

    data class FrozenExportSnapshot(
        val overlayTimeline: OverlayTimeline,
        val usableOverlayFrameCount: Int,
        val rawOverlayFrameCount: Int,
        val rawDurationMs: Long,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val sourceRotationDegrees: Int,
    )

    private fun ExportProgressSnapshot.hasAdvancedSince(previous: ExportProgressSnapshot?): Boolean {
        if (previous == null) return true
        return percent > previous.percent ||
            decodedFrames > previous.decodedFrames ||
            renderedFrames > previous.renderedFrames ||
            encodedFrames > previous.encodedFrames ||
            outputBytes > previous.outputBytes
    }

    suspend fun export(
        sessionId: Long,
        rawVideoUri: String,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
        overlayTimeline: OverlayTimeline,
        rawDurationMsHint: Long? = null,
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
        val frozenSnapshot = freezeSnapshotForExport(
            overlayTimeline = overlayTimeline,
            rawDurationMsHint = rawDurationMsHint,
        )
        if (frozenSnapshot.overlayTimeline.frames.isEmpty()) {
            updateExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            Log.w(TAG, "export_failure sessionId=$sessionId reason=frozen_overlay_frames_empty")
            return ExportResult(
                failureReason = AnnotatedExportFailureReason.OVERLAY_TIMELINE_EMPTY.name,
                verificationStatus = VerificationStatus.FAILED,
            )
        }
        val dynamicTimeoutMs = computeDynamicTimeoutMs(
            rawDurationMs = frozenSnapshot.rawDurationMs,
            width = frozenSnapshot.sourceWidth,
            height = frozenSnapshot.sourceHeight,
            overlayFrameCount = frozenSnapshot.usableOverlayFrameCount,
        )
        var telemetry: AnnotatedExportTelemetry? = null
        updateExportStatus(sessionId, AnnotatedExportStatus.PROCESSING)
        Log.d(
            TAG,
                "export_start sessionId=$sessionId rawVideoUri=$rawVideoUri " +
                "timelineFrames=${overlayTimeline.frames.size} " +
                "usableOverlayFrameCount=${frozenSnapshot.usableOverlayFrameCount} " +
                "rawOverlayFrameCount=${frozenSnapshot.rawOverlayFrameCount} " +
                "rawDurationMs=${frozenSnapshot.rawDurationMs} sourceWidth=${frozenSnapshot.sourceWidth} sourceHeight=${frozenSnapshot.sourceHeight} sourceRotationDegrees=${frozenSnapshot.sourceRotationDegrees} " +
                "dynamicTimeoutMs=$dynamicTimeoutMs stallWindowMs=$stallWindowMs " +
                "firstFrameTs=${frozenSnapshot.overlayTimeline.frames.first().timestampMs} " +
                "lastFrameTs=${frozenSnapshot.overlayTimeline.frames.last().timestampMs}",
        )
        val composeResult = try {
            coroutineScope {
                var exportWorkStartedAtMs: Long? = null
                var lastMeaningfulProgressAtMs: Long? = null
                var lastProgressSnapshot: ExportProgressSnapshot? = null
                var latestPercent = 0
                var markedSlow = false
                val renderJob = async {
                    renderAnnotatedVideo(rawVideoUri, drillType, drillCameraSide, frozenSnapshot.overlayTimeline, preset, { rendered, total ->
                        val pct = ((rendered.toFloat() / total.coerceAtLeast(1).toFloat()) * 100f).toInt().coerceIn(0, 100)
                        latestPercent = maxOf(latestPercent, pct)
                        onRenderProgress(rendered, total)
                    }) {
                        telemetry = it
                        val now = System.currentTimeMillis()
                        it.usableOverlayFrameCount = frozenSnapshot.usableOverlayFrameCount
                        it.rawOverlayFrameCount = frozenSnapshot.rawOverlayFrameCount
                        it.dynamicTimeoutMs = dynamicTimeoutMs
                        it.stallWindowMs = stallWindowMs
                        if (it.decoderInitializedAtMs != null && exportWorkStartedAtMs == null) {
                            exportWorkStartedAtMs = now
                            if (lastMeaningfulProgressAtMs == null) {
                                lastMeaningfulProgressAtMs = now
                            }
                        }
                        val progressSnapshot = ExportProgressSnapshot(
                            percent = latestPercent,
                            decodedFrames = it.decodedFrameCount,
                            renderedFrames = it.renderedFrameCount,
                            encodedFrames = it.encodedFrameCount,
                            outputBytes = it.outputBytesWritten,
                        )
                        if (progressSnapshot.hasAdvancedSince(lastProgressSnapshot)) {
                            lastMeaningfulProgressAtMs = now
                            it.lastProgressAtMs = now
                            lastProgressSnapshot = progressSnapshot
                        }
                        val lastProgressAt = lastMeaningfulProgressAtMs
                        if (lastProgressAt != null) {
                            it.timeSinceLastProgressMs = (now - lastProgressAt).coerceAtLeast(0L)
                        }
                        Log.d(TAG, it.structuredLogLine(event = "export_progress"))
                    }
                }
                var timedOut = false
                while (!renderJob.isCompleted) {
                    val now = System.currentTimeMillis()
                    val startedAt = exportWorkStartedAtMs
                    if (!markedSlow && startedAt != null && now - startedAt >= SOFT_SLOW_EXPORT_THRESHOLD_MS) {
                        updateExportStatus(sessionId, AnnotatedExportStatus.PROCESSING_SLOW)
                        markedSlow = true
                    }
                    val stalledForMs = lastMeaningfulProgressAtMs?.let { now - it } ?: 0L
                    if (startedAt != null && now - startedAt >= dynamicTimeoutMs && stalledForMs >= stallWindowMs) {
                        timedOut = true
                        renderJob.cancel(CancellationException("export_timeout_after_work_started"))
                        break
                    }
                    delay(100)
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
            Log.w(TAG, "export_failure sessionId=$sessionId reason=timeout_without_progress dynamicTimeoutMs=$dynamicTimeoutMs stallWindowMs=$stallWindowMs")
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
