package com.inversioncoach.app.ui.upload

import android.content.Context
import android.content.Intent
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
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import com.inversioncoach.app.movementprofile.ExistingDrillToProfileAdapter
import com.inversioncoach.app.movementprofile.MlKitVideoPoseFrameSource
import com.inversioncoach.app.movementprofile.UploadedVideoAnalyzer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    suspend fun run(uri: Uri, onProgress: (UploadProgress) -> Unit, onLog: (String) -> Unit): UploadFlowResult
}

class DefaultUploadVideoAnalysisRunner(
    private val context: Context,
    private val repository: SessionRepository,
) : UploadVideoAnalysisRunner {
    private val preset = ExportPreset.BALANCED

    override suspend fun run(uri: Uri, onProgress: (UploadProgress) -> Unit, onLog: (String) -> Unit): UploadFlowResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val drillType = DrillType.FREESTYLE
        var currentStage = UploadStage.VIDEO_SELECTED
        var persistedRawUri: String? = null
        var sessionId = -1L

        fun log(message: String) {
            val line = "${System.currentTimeMillis()} | session=$sessionId | stage=${currentStage.name} | $message"
            onLog(line)
            Log.i(TAG, line)
        }

        try {
            onProgress(UploadProgress(UploadStage.VIDEO_SELECTED, 0.03f, detail = "Selected URI: $uri"))
            sessionId = repository.saveSession(
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
            log("session_created uri=$uri")

            currentStage = UploadStage.IMPORTING_RAW_VIDEO
            onProgress(UploadProgress(currentStage, 0.08f, detail = "Persisting raw video"))
            log("raw_import_start")
            persistedRawUri = repository.saveRawVideoBlob(sessionId, uri.toString()) ?: error("Unable to import selected video")
            repository.updateRawPersistStatus(sessionId, RawPersistStatus.SUCCEEDED)
            repository.updateRawPersistFailureReason(sessionId, null)
            repository.updateMediaPipelineState(sessionId) { session ->
                session.copy(rawVideoUri = persistedRawUri, bestPlayableUri = persistedRawUri, rawPersistedAtMs = System.currentTimeMillis())
            }

            currentStage = UploadStage.RAW_IMPORT_COMPLETE
            onProgress(UploadProgress(currentStage, 0.2f, detail = "Raw replay ready"))
            log("raw_import_complete rawUri=$persistedRawUri")

            val profile = ExistingDrillToProfileAdapter().fromDrill(drillType)
            val frameSource = MlKitVideoPoseFrameSource(context.applicationContext)
            val analyzer = UploadedVideoAnalyzer(profile, frameSource)

            repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.PROCESSING)
            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.PREPARING,
                percent = 25,
                etaSeconds = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )

            currentStage = UploadStage.PREPARING_ANALYSIS
            onProgress(UploadProgress(currentStage, 0.28f, detail = "Loading analyzer"))
            log("analysis_prepare_start")

            currentStage = UploadStage.ANALYZING_VIDEO
            onProgress(UploadProgress(currentStage, 0.45f, detail = "Extracting frame landmarks"))
            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.DECODING_SOURCE,
                percent = 45,
                etaSeconds = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )

            val analysis = analyzer.analyze(uri.toString())
            log("analysis_complete frames=${analysis.overlayTimeline.size} dropped=${analysis.droppedFrames}")

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

            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.RENDERING,
                percent = 65,
                etaSeconds = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )
            currentStage = UploadStage.RENDERING_OVERLAY
            onProgress(UploadProgress(currentStage, 0.65f, detail = "Preparing overlay timeline"))
            log("overlay_timeline_ready frames=${overlayFrames.size}")

            currentStage = UploadStage.EXPORTING_ANNOTATED_VIDEO
            onProgress(UploadProgress(currentStage, 0.72f, detail = "Encoding annotated output"))
            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.ENCODING,
                percent = 72,
                etaSeconds = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )

            val exportPipeline = AnnotatedExportPipeline(repository, AnnotatedVideoCompositor(context.applicationContext))
            val export = exportPipeline.export(
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

            val overlayTimelineUri = repository.saveOverlayTimeline(sessionId, OverlayTimelineJson.encode(overlayTimeline))

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
            repository.updateMediaPipelineState(sessionId) { session ->
                session.copy(
                    completedAtMs = now,
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
                return@withContext UploadFlowResult(
                    sessionId = sessionId,
                    replayUri = replayUri,
                    rawReady = true,
                    annotatedReady = true,
                    finalStage = UploadStage.COMPLETED_ANNOTATED,
                )
            }

            val failureReason = export.failureReason ?: "ANNOTATED_EXPORT_FAILED"
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
            UploadFlowResult(
                sessionId = sessionId,
                replayUri = replayUri,
                rawReady = true,
                annotatedReady = false,
                exportFailureReason = failureReason,
                finalStage = UploadStage.COMPLETED_RAW_ONLY,
            )
        } catch (error: Throwable) {
            val message = error.message ?: "UPLOAD_ANALYSIS_FAILED"
            val stack = error.stackTraceToString().lineSequence().take(6).joinToString(" | ")
            log("exception message=$message stack=$stack")
            if (sessionId > 0L && !persistedRawUri.isNullOrBlank()) {
                repository.updateRawPersistStatus(sessionId, RawPersistStatus.SUCCEEDED)
                repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
                repository.updateAnnotatedExportFailureReason(sessionId, message)
                repository.updateAnnotatedExportProgress(
                    sessionId = sessionId,
                    stage = AnnotatedExportStage.FAILED,
                    percent = 100,
                    etaSeconds = 0,
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    failureReason = message,
                    failureDetail = "Failure after raw import at stage=${currentStage.name}",
                )
                onProgress(UploadProgress(UploadStage.COMPLETED_RAW_ONLY, 1f, detail = "Raw replay ready; downstream stage failed"))
                return@withContext UploadFlowResult(
                    sessionId = sessionId,
                    replayUri = persistedRawUri,
                    rawReady = true,
                    annotatedReady = false,
                    exportFailureReason = message,
                    finalStage = UploadStage.COMPLETED_RAW_ONLY,
                )
            }
            if (sessionId > 0L) {
                repository.updateRawPersistStatus(sessionId, RawPersistStatus.FAILED)
                repository.updateRawPersistFailureReason(sessionId, message)
                repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
                repository.updateAnnotatedExportFailureReason(sessionId, message)
                repository.updateAnnotatedExportProgress(
                    sessionId = sessionId,
                    stage = AnnotatedExportStage.FAILED,
                    percent = 100,
                    etaSeconds = 0,
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    failureReason = message,
                    failureDetail = "Failure before raw import stage=${currentStage.name}",
                )
            }
            throw error
        }
    }
}

class UploadVideoViewModel(
    private val runner: UploadVideoAnalysisRunner,
) : ViewModel() {
    private val _state = MutableStateFlow(UploadVideoUiState())
    val state: StateFlow<UploadVideoUiState> = _state.asStateFlow()
    private var activeJob: Job? = null

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
        activeJob = viewModelScope.launch {
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
        _state.update {
            it.copy(stage = UploadStage.CANCELLED, currentProcessingStage = UploadStage.CANCELLED, stageText = "Analysis cancelled", canCancel = false)
        }
    }
}

@Composable
fun UploadVideoScreen(
    onBack: () -> Unit,
    onOpenResults: (Long) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val viewModel = remember {
        UploadVideoViewModel(DefaultUploadVideoAnalysisRunner(context.applicationContext, repository))
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
                    Text("Open Results")
                }
            }
        }
    }
}
