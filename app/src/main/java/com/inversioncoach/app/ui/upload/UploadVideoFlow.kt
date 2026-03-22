package com.inversioncoach.app.ui.upload

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.model.AnnotatedExportStage
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.AnnotatedExportFailureReason
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import com.inversioncoach.app.movementprofile.ExistingDrillToProfileAdapter
import com.inversioncoach.app.movementprofile.MlKitVideoPoseFrameSource
import com.inversioncoach.app.movementprofile.UploadedVideoAnalyzer
import com.inversioncoach.app.movementprofile.VideoPoseFrameSource
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.FreestyleOrientationClassifier
import com.inversioncoach.app.recording.AnnotatedExportPipeline
import com.inversioncoach.app.recording.AnnotatedOverlayFrame
import com.inversioncoach.app.recording.AnnotatedVideoCompositor
import com.inversioncoach.app.recording.ExportPreset
import com.inversioncoach.app.recording.OverlayTimeline
import com.inversioncoach.app.recording.OverlayTimelineJson
import com.inversioncoach.app.recording.toTimelineFrame
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.storage.repository.SessionRepository
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.live.SessionDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "UploadVideoFlow"

enum class UploadStage(val label: String) {
    IDLE("Idle"),
    VIDEO_SELECTED("Video selected"),
    IMPORTING_RAW_VIDEO("Importing raw video"),
    RAW_IMPORT_COMPLETE("Raw import complete"),
    PREPARING_ANALYSIS("Preparing analysis"),
    ANALYZING_VIDEO("Analyzing frames"),
    RENDERING_OVERLAY("Rendering overlay"),
    EXPORTING_ANNOTATED_VIDEO("Exporting annotated replay"),
    VERIFYING_OUTPUT("Verifying output"),
    COMPLETED_RAW_ONLY("Raw replay ready, annotated replay unavailable"),
    COMPLETED_ANNOTATED("Annotated replay ready"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
}

data class UploadProgress(
    val stage: UploadStage,
    val percent: Float,
    val etaMs: Long? = null,
    val detail: String? = null,
)

data class UploadVideoUiState(
    val selectedVideoUri: Uri? = null,
    val stage: UploadStage = UploadStage.IDLE,
    val stageText: String = "Select a video to analyze.",
    val errorMessage: String? = null,
    val sessionId: Long? = null,
    val replayUri: String? = null,
    val canCancel: Boolean = false,
    val progressPercent: Float = 0f,
    val etaMs: Long? = null,
    val rawVideoStatus: RawPersistStatus = RawPersistStatus.NOT_STARTED,
    val annotatedVideoStatus: AnnotatedExportStatus = AnnotatedExportStatus.NOT_STARTED,
    val currentProcessingStage: UploadStage = UploadStage.IDLE,
    val technicalLog: String = "",
)

data class UploadFlowResult(
    val sessionId: Long,
    val replayUri: String?,
    val rawReady: Boolean,
    val annotatedReady: Boolean,
    val exportFailureReason: String? = null,
    val finalStage: UploadStage,
)

interface UploadVideoAnalysisRunner {
    suspend fun run(
        uri: Uri,
        onSessionCreated: (Long) -> Unit,
        onProgress: (UploadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): UploadFlowResult
}

class DefaultUploadVideoAnalysisRunner(
    private val context: Context,
    private val repository: SessionRepository,
    private val frameSourceFactory: (Context, Int) -> VideoPoseFrameSource = { appContext, fps ->
        MlKitVideoPoseFrameSource(appContext, sampleFps = fps)
    },
    private val analyzerFactory: (VideoPoseFrameSource) -> UploadedVideoAnalyzer = { UploadedVideoAnalyzer(it) },
    private val exportPipelineFactory: () -> AnnotatedExportPipeline = {
        AnnotatedExportPipeline(repository, AnnotatedVideoCompositor(context.applicationContext))
    },
) : UploadVideoAnalysisRunner {
    private val preset = ExportPreset.BALANCED

    private data class UploadSourceMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
    )

    override suspend fun run(
        uri: Uri,
        onSessionCreated: (Long) -> Unit,
        onProgress: (UploadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): UploadFlowResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val drillType = DrillType.FREESTYLE
        val sessionId = repository.saveSession(
            SessionRecord(
                title = "Uploaded Video Analysis",
                drillType = drillType,
                sessionSource = SessionSource.UPLOADED_VIDEO,
                startedAtMs = startedAt,
                completedAtMs = startedAt,
                overallScore = 0,
                strongestArea = "Upload analysis",
                limitingFactor = "Pending analysis",
                issues = "",
                wins = "",
                metricsJson = "{}",
                annotatedVideoUri = null,
                rawVideoUri = null,
                notesUri = null,
                bestFrameTimestampMs = null,
                worstFrameTimestampMs = null,
                topImprovementFocus = "Maintain body line",
                rawPersistStatus = RawPersistStatus.PROCESSING,
                annotatedExportStatus = AnnotatedExportStatus.NOT_STARTED,
            ),
        )
        onSessionCreated(sessionId)
        Log.i(TAG, "analysis_start sessionId=$sessionId uri=$uri")
        SessionDiagnostics.record(
            sessionId = sessionId,
            stage = SessionDiagnostics.Stage.SESSION_START,
            status = SessionDiagnostics.Status.STARTED,
            message = "Uploaded video analysis session started",
            metrics = mapOf("sourceUri" to uri.toString(), "workflow" to "upload"),
        )

        var persistedRawUri: String? = null
        var sourceDurationMs: Long = 0L
        var currentStage = UploadStage.IMPORTING_RAW_VIDEO

        fun log(message: String) {
            val line = "${System.currentTimeMillis()} | session=$sessionId | stage=${currentStage.name} | $message"
            onLog(line)
            Log.i(TAG, line)
        }

        try {
            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.RAW_PERSIST,
                status = SessionDiagnostics.Status.STARTED,
                message = "Persisting uploaded raw video",
                metrics = mapOf("sourceUri" to uri.toString()),
            )
            persistedRawUri = repository.saveRawVideoBlob(sessionId, uri.toString())
                ?: error("Unable to import selected video")
            repository.updateRawPersistStatus(sessionId, RawPersistStatus.SUCCEEDED)
            repository.updateMediaPipelineState(sessionId) { session ->
                session.copy(rawVideoUri = persistedRawUri, bestPlayableUri = persistedRawUri)
            }
            log("repo_write raw persisted uri=$persistedRawUri")
            onProgress(UploadProgress(UploadStage.RAW_IMPORT_COMPLETE, 0.15f, detail = "Raw video imported"))
            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.RAW_PERSIST,
                status = SessionDiagnostics.Status.SUCCEEDED,
                message = "Uploaded raw video persisted",
                metrics = mapOf("rawUri" to persistedRawUri),
            )

            val metadata = extractMetadata(Uri.parse(persistedRawUri))
            sourceDurationMs = metadata.durationMs.coerceAtLeast(0L)
            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.TIMESTAMP_ALIGNMENT,
                status = SessionDiagnostics.Status.PROGRESS,
                message = "Uploaded source metadata extracted",
                metrics = mapOf(
                    "durationMs" to metadata.durationMs.toString(),
                    "width" to metadata.width.toString(),
                    "height" to metadata.height.toString(),
                ),
            )
            log("metadata durationMs=${metadata.durationMs} width=${metadata.width} height=${metadata.height}")

            val profile = ExistingDrillToProfileAdapter().fromDrill(drillType)
            currentStage = UploadStage.PREPARING_ANALYSIS
            onProgress(UploadProgress(currentStage, 0.2f, detail = "Preparing uploaded analysis"))
            val frameSource = frameSourceFactory(context.applicationContext, preset.analysisFps)
            val analyzer = analyzerFactory(frameSource)

            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.OVERLAY_CAPTURE,
                status = SessionDiagnostics.Status.STARTED,
                message = "Uploaded frame analysis started",
                metrics = mapOf("analysisFps" to preset.analysisFps.toString()),
            )
            currentStage = UploadStage.ANALYZING_VIDEO
            onProgress(UploadProgress(currentStage, 0.25f, detail = "Analyzing uploaded frames"))
            val analysisStart = System.currentTimeMillis()
            val analysis = analyzer.analyze(Uri.parse(persistedRawUri), profile)
            val analysisDurationMs = System.currentTimeMillis() - analysisStart
            if (analysis.overlayTimeline.isEmpty()) {
                SessionDiagnostics.record(
                    sessionId = sessionId,
                    stage = SessionDiagnostics.Stage.OVERLAY_CAPTURE,
                    status = SessionDiagnostics.Status.FAILED,
                    message = "No overlay samples detected in uploaded video",
                    errorCode = AnnotatedExportFailureReason.OVERLAY_CAPTURE_EMPTY.name,
                )
                error("No body landmarks detected. Try another video with full-body side view.")
            }
            log("analysis_complete overlayFrames=${analysis.overlayTimeline.size} dropped=${analysis.droppedFrames} elapsedMs=$analysisDurationMs")
            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.OVERLAY_CAPTURE,
                status = SessionDiagnostics.Status.SUCCEEDED,
                message = "Uploaded frame analysis complete",
                metrics = mapOf(
                    "overlayFrameCount" to analysis.overlayTimeline.size.toString(),
                    "droppedFrames" to analysis.droppedFrames.toString(),
                    "analysisDurationMs" to analysisDurationMs.toString(),
                ),
            )

            val orientationClassifier = FreestyleOrientationClassifier()
            val overlayFrames = analysis.overlayTimeline.map { point ->
                val joints = point.landmarks.map { (name, pointPair) ->
                    com.inversioncoach.app.model.JointPoint(name, pointPair.first, pointPair.second, 0f, point.confidence)
                }
                val viewMode = orientationClassifier.classify(joints)
                AnnotatedOverlayFrame(
                    timestampMs = point.timestampMs,
                    landmarks = joints,
                    smoothedLandmarks = joints,
                    confidence = point.confidence,
                    sessionMode = SessionMode.FREESTYLE,
                    drillCameraSide = DrillCameraSide.LEFT,
                    freestyleViewMode = viewMode,
                    bodyVisible = point.confidence > 0f,
                    showSkeleton = true,
                    showIdealLine = true,
                    mirrorMode = false,
                )
            }

            overlayFrames.forEach { frame ->
                repository.saveFrameMetric(
                    FrameMetricRecord(
                        sessionId = sessionId,
                        timestampMs = frame.timestampMs,
                        confidence = frame.confidence,
                        overallScore = (analysis.overlayTimeline.firstOrNull { it.timestampMs == frame.timestampMs }?.metrics?.get("alignment_score")?.times(100f)
                            ?: 0f).toInt().coerceIn(0, 100),
                        limitingFactor = analysis.overlayTimeline.firstOrNull { it.timestampMs == frame.timestampMs }?.phaseId ?: "tracking",
                        metricScoresJson = "{}",
                        anglesJson = "{}",
                        activeIssue = null,
                    ),
                )
            }

            val overlayTimeline = OverlayTimeline(
                startedAtMs = 0L,
                sampleIntervalMs = 80L,
                frames = overlayFrames.map { it.toTimelineFrame(sessionId = sessionId, sessionStartedAtMs = 0L) },
            )
            val overlayTimelineUri = repository.saveOverlayTimeline(sessionId, OverlayTimelineJson.encode(overlayTimeline))
            repository.updateMediaPipelineState(sessionId) { session ->
                session.copy(
                    overlayFrameCount = overlayFrames.size,
                    overlayTimelineUri = overlayTimelineUri,
                    rawVideoUri = persistedRawUri,
                    bestPlayableUri = persistedRawUri,
                )
            }
            log("repo_write overlay timeline uri=$overlayTimelineUri frames=${overlayFrames.size}")

            repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.PROCESSING)
            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.LOADING_OVERLAYS,
                percent = 65,
                etaSeconds = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )
            currentStage = UploadStage.RENDERING_OVERLAY
            onProgress(UploadProgress(currentStage, 0.65f, detail = "Preparing overlay timeline"))

            currentStage = UploadStage.EXPORTING_ANNOTATED_VIDEO
            onProgress(UploadProgress(currentStage, 0.72f, detail = "Encoding annotated output"))
            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.ENCODING,
                percent = 72,
                etaSeconds = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )

            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.ANNOTATED_EXPORT_START,
                status = SessionDiagnostics.Status.STARTED,
                message = "Annotated export started for uploaded video",
                metrics = mapOf(
                    "overlayFrameCount" to overlayFrames.size.toString(),
                    "overlayTimelineUri" to overlayTimelineUri,
                ),
            )
            val export = exportPipelineFactory().export(
                sessionId = sessionId,
                rawVideoUri = persistedRawUri,
                drillType = drillType,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = overlayTimeline,
                preset = preset,
                onRenderProgress = { rendered, total ->
                    val ratio = if (total <= 0) 0f else rendered.toFloat() / total.toFloat()
                    val percent = (72 + (ratio * 18f)).toInt()
                    onProgress(UploadProgress(UploadStage.EXPORTING_ANNOTATED_VIDEO, percent / 100f, detail = "Rendered $rendered/$total"))
                },
            )
            log("export_done started=${export.started} failure=${export.failureReason.orEmpty()} uri=${export.persistedUri.orEmpty()}")

            currentStage = UploadStage.VERIFYING_OUTPUT
            onProgress(UploadProgress(currentStage, 0.93f, detail = "Verifying media files"))
            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.VERIFYING,
                percent = 93,
                etaSeconds = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )

            val now = System.currentTimeMillis()
            val replayUri = export.persistedUri ?: persistedRawUri
            val isAnnotatedReady = !export.persistedUri.isNullOrBlank()
            if (isAnnotatedReady) {
                SessionDiagnostics.record(
                    sessionId = sessionId,
                    stage = SessionDiagnostics.Stage.ANNOTATED_EXPORT_VERIFY,
                    status = SessionDiagnostics.Status.SUCCEEDED,
                    message = "Uploaded annotated export verified",
                    metrics = mapOf("annotatedUri" to export.persistedUri.orEmpty()),
                )
            } else {
                SessionDiagnostics.record(
                    sessionId = sessionId,
                    stage = SessionDiagnostics.Stage.ANNOTATED_EXPORT_VERIFY,
                    status = SessionDiagnostics.Status.FAILED,
                    message = "Uploaded annotated export failed",
                    errorCode = export.failureReason ?: AnnotatedExportFailureReason.EXPORT_NOT_STARTED.name,
                )
            }
            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
                status = SessionDiagnostics.Status.PROGRESS,
                message = "Replay source selected for uploaded workflow",
                metrics = mapOf("selectedUri" to replayUri.orEmpty(), "annotatedUri" to export.persistedUri.orEmpty()),
            )
            if (!isAnnotatedReady && !persistedRawUri.isNullOrBlank()) {
                SessionDiagnostics.record(
                    sessionId = sessionId,
                    stage = SessionDiagnostics.Stage.FALLBACK_DECISION,
                    status = SessionDiagnostics.Status.FALLBACK,
                    message = "Uploaded workflow replay fell back to raw",
                    errorCode = AnnotatedExportFailureReason.REPLAY_SELECTION_FELL_BACK_TO_RAW.name,
                )
            }

            repository.updateMediaPipelineState(sessionId) { session ->
                session.copy(
                    completedAtMs = startedAt + sourceDurationMs,
                    overallScore = ((analysis.overlayTimeline.map { it.metrics["alignment_score"] ?: 0f }.average()) * 100f).toInt().coerceIn(0, 100),
                    strongestArea = "Alignment",
                    limitingFactor = if (isAnnotatedReady) "Consistency" else "Annotated export unavailable",
                    wins = "Processed uploaded video",
                    issues = if (analysis.droppedFrames > 0) "Dropped ${analysis.droppedFrames} low-confidence frames" else "",
                    metricsJson = "trackingMode:HOLD_BASED|validFrames:${analysis.overlayTimeline.size}|invalidFrames:${analysis.droppedFrames}|totalJobTimeMs:${now - startedAt}",
                    bestPlayableUri = replayUri,
                    rawVideoUri = persistedRawUri,
                    annotatedVideoUri = export.persistedUri,
                    overlayFrameCount = overlayFrames.size,
                    overlayTimelineUri = overlayTimelineUri,
                )
            }
            log("repo_write final replayUri=$replayUri annotatedReady=$isAnnotatedReady")

            if (isAnnotatedReady) {
                repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_READY)
                repository.updateAnnotatedExportFailureReason(sessionId, null)
                repository.updateAnnotatedExportProgress(
                    sessionId = sessionId,
                    stage = AnnotatedExportStage.COMPLETED,
                    percent = 100,
                    etaSeconds = 0,
                    elapsedMs = now - startedAt,
                )
                currentStage = UploadStage.COMPLETED_ANNOTATED
                onProgress(UploadProgress(currentStage, 1f, etaMs = 0L, detail = "Annotated replay ready"))
                log("complete annotatedReady=true replayUri=$replayUri")
                SessionDiagnostics.persistReport(sessionId, repository.observeSession(sessionId).first(), repository)
                return@withContext UploadFlowResult(
                    sessionId = sessionId,
                    replayUri = replayUri,
                    rawReady = true,
                    annotatedReady = true,
                    finalStage = UploadStage.COMPLETED_ANNOTATED,
                )
            }

            val failureReason = export.failureReason ?: AnnotatedExportFailureReason.EXPORT_NOT_STARTED.name
            repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            repository.updateAnnotatedExportFailureReason(sessionId, failureReason)
            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.FAILED,
                percent = 100,
                etaSeconds = 0,
                elapsedMs = now - startedAt,
                failureReason = failureReason,
                failureDetail = "Raw ready, annotated export failed during ${currentStage.name}",
            )
            currentStage = UploadStage.COMPLETED_RAW_ONLY
            onProgress(UploadProgress(currentStage, 1f, etaMs = 0L, detail = "Raw replay ready; annotated export failed"))
            log("complete annotatedReady=false reason=$failureReason")
            SessionDiagnostics.persistReport(sessionId, repository.observeSession(sessionId).first(), repository)
            UploadFlowResult(
                sessionId = sessionId,
                replayUri = replayUri,
                rawReady = true,
                annotatedReady = false,
                exportFailureReason = failureReason,
                finalStage = UploadStage.COMPLETED_RAW_ONLY,
            )
        } catch (error: Throwable) {
            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.SESSION_FINALIZE,
                status = SessionDiagnostics.Status.FAILED,
                message = "Uploaded workflow failed",
                errorCode = error.message ?: AnnotatedExportFailureReason.UNKNOWN_EXCEPTION.name,
                throwable = error,
            )
            repository.updateRawPersistStatus(sessionId, if (persistedRawUri.isNullOrBlank()) RawPersistStatus.FAILED else RawPersistStatus.SUCCEEDED)
            repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
            val mappedFailure = when {
                error.message?.contains("No enum constant", ignoreCase = true) == true -> "INPUT_SCHEMA_MISMATCH"
                error.message?.contains("LEFT_EYE_INNER", ignoreCase = true) == true -> "UNSUPPORTED_LANDMARK_ID"
                else -> "UPLOAD_OVERLAY_GENERATION_FAILED"
            }
            repository.updateAnnotatedExportFailureReason(sessionId, mappedFailure)
            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.FAILED,
                percent = 100,
                etaSeconds = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
                failureReason = mappedFailure,
                failureDetail = "Upload workflow failed during ${currentStage.name}",
            )
            repository.updateMediaPipelineState(sessionId) { session ->
                session.copy(
                    completedAtMs = startedAt + sourceDurationMs,
                    bestPlayableUri = persistedRawUri,
                    rawVideoUri = persistedRawUri ?: session.rawVideoUri,
                    annotatedVideoUri = null,
                    limitingFactor = "Upload processing failed",
                )
            }
            SessionDiagnostics.persistReport(sessionId, repository.observeSession(sessionId).first(), repository)
            throw error
        }
    }

    private fun extractMetadata(uri: Uri): UploadSourceMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            UploadSourceMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }
}

class UploadVideoViewModel(
    private val runner: UploadVideoAnalysisRunner,
    private val repository: SessionRepository?,
) : ViewModel() {
    private val _state = MutableStateFlow(UploadVideoUiState())
    val state: StateFlow<UploadVideoUiState> = _state.asStateFlow()
    companion object {
        private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var activeJob: Job? = null
        private var activeSessionId: Long? = null
    }

    constructor(runner: UploadVideoAnalysisRunner) : this(runner, null)

    init {
        val repo = repository
        if (repo != null) {
            viewModelScope.launch {
                repo.observeSessions().collectLatest { sessions ->
                    val sessionId = activeSessionId ?: return@collectLatest
                    val session = sessions.firstOrNull { it.id == sessionId } ?: return@collectLatest
                    val stage = deriveUploadStage(session)
                    _state.update { current ->
                        val persistedProgress = session.annotatedExportPercent.coerceIn(0, 100) / 100f
                        val preservedProgress = current.progressPercent.takeIf {
                            session.annotatedExportStatus != AnnotatedExportStatus.NOT_STARTED
                        } ?: 0f
                        current.copy(
                            sessionId = sessionId,
                            stage = stage,
                            currentProcessingStage = stage,
                            progressPercent = maxOf(persistedProgress, preservedProgress),
                            rawVideoStatus = session.rawPersistStatus,
                            annotatedVideoStatus = session.annotatedExportStatus,
                            etaMs = session.annotatedExportEtaSeconds?.times(1000L),
                        )
                    }
                }
            }
        }
    }

    fun onPickStarted() {
        _state.update {
            it.copy(
                stage = UploadStage.VIDEO_SELECTED,
                currentProcessingStage = UploadStage.VIDEO_SELECTED,
                stageText = "Choose a video from your device.",
                errorMessage = null,
            )
        }
    }

    fun analyze(uri: Uri) {
        if (activeJob?.isActive == true) return
        activeJob = uploadScope.launch {
            _state.update {
                it.copy(
                    selectedVideoUri = uri,
                    stage = UploadStage.VIDEO_SELECTED,
                    currentProcessingStage = UploadStage.VIDEO_SELECTED,
                    stageText = UploadStage.VIDEO_SELECTED.label,
                    errorMessage = null,
                    sessionId = null,
                    replayUri = null,
                    canCancel = true,
                    technicalLog = "",
                    rawVideoStatus = RawPersistStatus.PROCESSING,
                    annotatedVideoStatus = AnnotatedExportStatus.NOT_STARTED,
                )
            }
            runCatching {
                runner.run(
                    uri = uri,
                    onSessionCreated = { sessionId ->
                        activeSessionId = sessionId
                        _state.update { current -> current.copy(sessionId = sessionId) }
                    },
                    onProgress = { progress ->
                        _state.update { current ->
                            val rawStatus = if (progress.stage.ordinal >= UploadStage.RAW_IMPORT_COMPLETE.ordinal) {
                                RawPersistStatus.SUCCEEDED
                            } else {
                                current.rawVideoStatus
                            }
                            val annotatedStatus = when (progress.stage) {
                                UploadStage.PREPARING_ANALYSIS,
                                UploadStage.ANALYZING_VIDEO,
                                UploadStage.RENDERING_OVERLAY,
                                UploadStage.EXPORTING_ANNOTATED_VIDEO,
                                UploadStage.VERIFYING_OUTPUT,
                                -> AnnotatedExportStatus.PROCESSING

                                UploadStage.COMPLETED_ANNOTATED -> AnnotatedExportStatus.ANNOTATED_READY
                                UploadStage.COMPLETED_RAW_ONLY,
                                UploadStage.FAILED,
                                -> AnnotatedExportStatus.ANNOTATED_FAILED

                                else -> current.annotatedVideoStatus
                            }
                            current.copy(
                                stage = progress.stage,
                                currentProcessingStage = progress.stage,
                                stageText = progress.detail ?: progress.stage.label,
                                progressPercent = progress.percent.coerceIn(0f, 1f),
                                etaMs = progress.etaMs,
                                rawVideoStatus = rawStatus,
                                annotatedVideoStatus = annotatedStatus,
                            )
                        }
                    },
                    onLog = { line ->
                        (_state.value.sessionId ?: activeSessionId)?.let { sessionId ->
                            repository?.saveSessionDiagnostics(sessionId, (_state.value.technicalLog + "\n" + line).trim())
                        }
                        _state.update { current ->
                            current.copy(
                                technicalLog = if (current.technicalLog.isBlank()) line else "${current.technicalLog}\n$line",
                            )
                        }
                    },
                )
            }.onSuccess { result ->
                val completionMessage = if (result.annotatedReady) {
                    "Annotated replay ready"
                } else {
                    "Raw replay ready, annotated replay unavailable"
                }
                _state.update {
                    it.copy(
                        stage = result.finalStage,
                        currentProcessingStage = result.finalStage,
                        stageText = completionMessage,
                        sessionId = result.sessionId,
                        replayUri = result.replayUri,
                        errorMessage = result.exportFailureReason?.let { reason -> "Annotated stage failed: $reason" },
                        progressPercent = 1f,
                        canCancel = false,
                        rawVideoStatus = if (result.rawReady) RawPersistStatus.SUCCEEDED else RawPersistStatus.FAILED,
                        annotatedVideoStatus = if (result.annotatedReady) AnnotatedExportStatus.ANNOTATED_READY else AnnotatedExportStatus.ANNOTATED_FAILED,
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "analysis_failed uri=$uri", error)
                _state.update {
                    it.copy(
                        stage = UploadStage.FAILED,
                        currentProcessingStage = UploadStage.FAILED,
                        stageText = "Upload pipeline failed",
                        errorMessage = error.message ?: "Unable to process this video.",
                        progressPercent = if (it.rawVideoStatus == RawPersistStatus.SUCCEEDED) 1f else 0f,
                        canCancel = false,
                        annotatedVideoStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                    )
                }
            }
        }
    }

    fun onInvalidSelection(message: String) {
        _state.update {
            it.copy(stage = UploadStage.FAILED, stageText = "Invalid selection", errorMessage = message, canCancel = false)
        }
    }

    fun cancel() {
        activeJob?.cancel()
        _state.value.sessionId?.let { sessionId ->
            viewModelScope.launch {
                repository?.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
                repository?.updateAnnotatedExportFailureReason(sessionId, "EXPORT_CANCELLED")
            }
        }
        _state.update {
            it.copy(stage = UploadStage.CANCELLED, currentProcessingStage = UploadStage.CANCELLED, stageText = "Analysis cancelled", canCancel = false)
        }
    }
}

internal fun deriveUploadStage(session: SessionRecord): UploadStage = when {
    session.rawPersistStatus == RawPersistStatus.PROCESSING -> UploadStage.IMPORTING_RAW_VIDEO
    session.rawPersistStatus == RawPersistStatus.FAILED -> UploadStage.FAILED
    session.rawPersistFailureReason in setOf("RAW_REPLAY_INVALID", "RAW_MEDIA_CORRUPT", "SOURCE_VIDEO_UNREADABLE") -> UploadStage.FAILED
    session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY -> UploadStage.COMPLETED_ANNOTATED
    session.annotatedExportStatus in setOf(AnnotatedExportStatus.ANNOTATED_FAILED, AnnotatedExportStatus.SKIPPED) &&
        rawReplayPlayableForStage(session) -> UploadStage.COMPLETED_RAW_ONLY
    session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED -> UploadStage.FAILED
    session.annotatedExportStatus in setOf(AnnotatedExportStatus.VALIDATING_INPUT, AnnotatedExportStatus.PROCESSING, AnnotatedExportStatus.PROCESSING_SLOW) -> when (session.annotatedExportStage) {
        AnnotatedExportStage.PREPARING -> UploadStage.PREPARING_ANALYSIS
        AnnotatedExportStage.DECODING_SOURCE -> UploadStage.ANALYZING_VIDEO
        AnnotatedExportStage.LOADING_OVERLAYS,
        AnnotatedExportStage.RENDERING,
        -> UploadStage.RENDERING_OVERLAY

        AnnotatedExportStage.ENCODING -> UploadStage.EXPORTING_ANNOTATED_VIDEO
        AnnotatedExportStage.VERIFYING -> UploadStage.VERIFYING_OUTPUT
        else -> UploadStage.PREPARING_ANALYSIS
    }

    rawReplayPlayableForStage(session) &&
        session.annotatedExportStatus in setOf(AnnotatedExportStatus.NOT_STARTED, AnnotatedExportStatus.SKIPPED) -> UploadStage.RAW_IMPORT_COMPLETE
    else -> UploadStage.IDLE
}

private fun rawReplayPlayableForStage(session: SessionRecord): Boolean {
    if (session.rawPersistFailureReason in setOf("RAW_REPLAY_INVALID", "RAW_MEDIA_CORRUPT", "SOURCE_VIDEO_UNREADABLE")) return false
    val rawUri = session.rawFinalUri ?: session.rawVideoUri ?: session.rawMasterUri
    if (rawUri.isNullOrBlank()) return false
    val bestPlayableUri = session.bestPlayableUri
    return bestPlayableUri.isNullOrBlank() || bestPlayableUri == rawUri
}

@Composable
fun UploadVideoScreen(
    onBack: () -> Unit,
    onOpenResults: (Long) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val viewModel = remember {
        UploadVideoViewModel(DefaultUploadVideoAnalysisRunner(context.applicationContext, repository), repository)
    }
    val state by viewModel.state.collectAsState()
    var showTechLog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            Log.w(TAG, "picker_failure reason=no_selection")
            return@rememberLauncherForActivityResult
        }
        val type = context.contentResolver.getType(uri).orEmpty()
        if (!type.startsWith("video/")) {
            Log.w(TAG, "picker_failure reason=invalid_mime uri=$uri mime=$type")
            viewModel.onInvalidSelection("The selected file is not a readable video.")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            Log.w(TAG, "picker_permission_persist_failed uri=$uri", error)
        }
        viewModel.analyze(uri)
    }

    val inFlightStages = setOf(
        UploadStage.IMPORTING_RAW_VIDEO,
        UploadStage.PREPARING_ANALYSIS,
        UploadStage.ANALYZING_VIDEO,
        UploadStage.RENDERING_OVERLAY,
        UploadStage.EXPORTING_ANNOTATED_VIDEO,
        UploadStage.VERIFYING_OUTPUT,
    )

    ScaffoldedScreen(title = "Upload Video", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Analyze a recorded video with pose overlay", style = MaterialTheme.typography.titleMedium)
            Text(state.stageText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Current stage: ${state.currentProcessingStage.label}", style = MaterialTheme.typography.bodySmall)
            Text("Raw video status: ${state.rawVideoStatus.name}", style = MaterialTheme.typography.bodySmall)
            Text("Annotated video status: ${state.annotatedVideoStatus.name}", style = MaterialTheme.typography.bodySmall)
            state.selectedVideoUri?.let { Text("Selected: $it", style = MaterialTheme.typography.bodySmall) }

            if (state.stage in inFlightStages || state.stage == UploadStage.RAW_IMPORT_COMPLETE) {
                CircularProgressIndicator()
                LinearProgressIndicator(progress = { state.progressPercent }, modifier = Modifier.fillMaxWidth())
                Text("${(state.progressPercent * 100).toInt()}%")
                state.etaMs?.let { eta -> Text("ETA: ${eta / 1000}s", style = MaterialTheme.typography.bodySmall) }
            }

            state.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { viewModel.onPickStarted(); picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry")
                }
            }

            Button(
                onClick = {
                    viewModel.onPickStarted()
                    picker.launch(arrayOf("video/*"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.stage !in inFlightStages,
            ) {
                Text("Choose Video")
            }

            if (state.canCancel) {
                OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }

            OutlinedButton(onClick = { showTechLog = !showTechLog }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showTechLog) "Hide technical log" else "Show technical log")
            }
            if (showTechLog) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { clipboardManager.setText(AnnotatedString(state.technicalLog.ifBlank { "No log entries yet." })) }) {
                        Text("Copy log")
                    }
                }
                SelectionContainer {
                    Text(
                        text = state.technicalLog.ifBlank { "No log entries yet." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val resultSessionId = state.sessionId
            if (state.stage in setOf(UploadStage.COMPLETED_ANNOTATED, UploadStage.COMPLETED_RAW_ONLY) && resultSessionId != null) {
                Button(onClick = { onOpenResults(resultSessionId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("View session")
                }
            } else if (resultSessionId != null) {
                OutlinedButton(onClick = { onOpenResults(resultSessionId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("View session")
                }
            }
        }
    }
}
