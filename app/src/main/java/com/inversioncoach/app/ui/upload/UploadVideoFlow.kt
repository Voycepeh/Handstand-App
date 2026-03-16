package com.inversioncoach.app.ui.upload

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.movementprofile.ExistingDrillToProfileAdapter
import com.inversioncoach.app.movementprofile.MlKitVideoPoseFrameSource
import com.inversioncoach.app.movementprofile.UploadedVideoAnalyzer
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.recording.AnnotatedExportPipeline
import com.inversioncoach.app.recording.AnnotatedOverlayFrame
import com.inversioncoach.app.recording.AnnotatedVideoCompositor
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.storage.repository.SessionRepository
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "UploadVideoFlow"

enum class UploadStage(val label: String) {
    IDLE("Idle"),
    PICKING("Importing video"),
    ANALYZING("Running pose analysis"),
    BUILDING_OVERLAY("Building overlay"),
    PREPARING_REPLAY("Preparing replay"),
    SUCCESS("Complete"),
    FAILURE("Failed"),
}

data class UploadVideoUiState(
    val selectedVideoUri: Uri? = null,
    val stage: UploadStage = UploadStage.IDLE,
    val stageText: String = "Select a video to analyze.",
    val errorMessage: String? = null,
    val sessionId: Long? = null,
    val replayUri: String? = null,
    val canCancel: Boolean = false,
)

data class UploadFlowResult(
    val sessionId: Long,
    val replayUri: String?,
)

interface UploadVideoAnalysisRunner {
    suspend fun run(uri: Uri, onStage: (UploadStage) -> Unit): UploadFlowResult
}

class DefaultUploadVideoAnalysisRunner(
    private val context: Context,
    private val repository: SessionRepository,
) : UploadVideoAnalysisRunner {
    override suspend fun run(uri: Uri, onStage: (UploadStage) -> Unit): UploadFlowResult {
        val startedAt = System.currentTimeMillis()
        val drillType = DrillType.FREESTYLE
        val sessionId = repository.saveSession(
            SessionRecord(
                title = "Uploaded Video Analysis",
                drillType = drillType,
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
        Log.i(TAG, "analysis_start sessionId=$sessionId uri=$uri")

        val persistedRawUri = repository.saveRawVideoBlob(sessionId, uri.toString())
            ?: error("Unable to import selected video")
        repository.updateRawPersistStatus(sessionId, RawPersistStatus.SUCCEEDED)

        onStage(UploadStage.ANALYZING)
        val profile = ExistingDrillToProfileAdapter().fromDrill(drillType)
        val analyzer = UploadedVideoAnalyzer(MlKitVideoPoseFrameSource(context.applicationContext))
        val analysis = analyzer.analyze(Uri.parse(persistedRawUri), profile)
        if (analysis.overlayTimeline.isEmpty()) {
            error("No body landmarks detected. Try another video with full-body side view.")
        }

        onStage(UploadStage.BUILDING_OVERLAY)
        val overlayFrames = analysis.overlayTimeline.map { point ->
            val joints = point.landmarks.map { (name, pointPair) ->
                com.inversioncoach.app.model.JointPoint(name, pointPair.first, pointPair.second, 0f, point.confidence)
            }
            AnnotatedOverlayFrame(
                timestampMs = point.timestampMs,
                landmarks = joints,
                smoothedLandmarks = joints,
                confidence = point.confidence,
                sessionMode = SessionMode.FREESTYLE,
                drillCameraSide = DrillCameraSide.LEFT,
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

        onStage(UploadStage.PREPARING_REPLAY)
        val exportPipeline = AnnotatedExportPipeline(repository, AnnotatedVideoCompositor(context.applicationContext))
        val export = exportPipeline.export(
            sessionId = sessionId,
            rawVideoUri = persistedRawUri,
            drillType = drillType,
            drillCameraSide = DrillCameraSide.LEFT,
            overlayFrames = overlayFrames,
        )
        if (!export.failureReason.isNullOrBlank()) {
            Log.w(TAG, "analysis_export_failed sessionId=$sessionId reason=${export.failureReason}")
        }

        val now = System.currentTimeMillis()
        val replayUri = export.persistedUri ?: persistedRawUri
        repository.updateMediaPipelineState(sessionId) { session ->
            session.copy(
                completedAtMs = now,
                overallScore = ((analysis.overlayTimeline.map { it.metrics["alignment_score"] ?: 0f }.average()) * 100f).toInt().coerceIn(0, 100),
                strongestArea = "Alignment",
                limitingFactor = "Consistency",
                wins = "Processed uploaded video",
                issues = if (analysis.droppedFrames > 0) "Dropped ${analysis.droppedFrames} low-confidence frames" else "",
                metricsJson = analysis.telemetry.toString(),
                bestPlayableUri = replayUri,
                rawVideoUri = persistedRawUri,
                annotatedVideoUri = export.persistedUri,
                overlayFrameCount = overlayFrames.size,
            )
        }
        Log.i(TAG, "analysis_complete sessionId=$sessionId replayUri=$replayUri")
        return UploadFlowResult(sessionId = sessionId, replayUri = replayUri)
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
                stage = UploadStage.PICKING,
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
                    stage = UploadStage.PICKING,
                    stageText = "Importing video",
                    errorMessage = null,
                    sessionId = null,
                    replayUri = null,
                    canCancel = true,
                )
            }
            runCatching {
                runner.run(uri) { stage ->
                    _state.update { current -> current.copy(stage = stage, stageText = stage.label) }
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        stage = UploadStage.SUCCESS,
                        stageText = "Analysis complete",
                        sessionId = result.sessionId,
                        replayUri = result.replayUri,
                        canCancel = false,
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "analysis_failed uri=$uri", error)
                _state.update {
                    it.copy(
                        stage = UploadStage.FAILURE,
                        stageText = "Analysis failed",
                        errorMessage = error.message ?: "Unable to process this video.",
                        canCancel = false,
                    )
                }
            }
        }
    }

    fun onInvalidSelection(message: String) {
        _state.update {
            it.copy(stage = UploadStage.FAILURE, stageText = "Invalid selection", errorMessage = message, canCancel = false)
        }
    }

    fun cancel() {
        activeJob?.cancel()
        _state.update {
            it.copy(stage = UploadStage.IDLE, stageText = "Analysis cancelled", canCancel = false)
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
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
        }
        Log.i(TAG, "picker_success uri=$uri type=$type")
        viewModel.analyze(uri)
    }

    ScaffoldedScreen(title = "Upload Video", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Analyze a recorded video with pose overlay", style = MaterialTheme.typography.titleMedium)
            Text(state.stageText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.selectedVideoUri?.let { Text("Selected: $it", style = MaterialTheme.typography.bodySmall) }

            if (state.stage in setOf(UploadStage.ANALYZING, UploadStage.BUILDING_OVERLAY, UploadStage.PREPARING_REPLAY)) {
                CircularProgressIndicator()
            }

            state.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry")
                }
            }

            Button(
                onClick = {
                    viewModel.onPickStarted()
                    picker.launch(arrayOf("video/*"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.stage !in setOf(UploadStage.ANALYZING, UploadStage.BUILDING_OVERLAY, UploadStage.PREPARING_REPLAY),
            ) {
                Text("Choose Video")
            }

            if (state.canCancel) {
                OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
            if (state.stage == UploadStage.SUCCESS && state.sessionId != null) {
                Button(onClick = { onOpenResults(state.sessionId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Results")
                }
            }
        }
    }
}
