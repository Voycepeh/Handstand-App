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
import androidx.compose.material3.OutlinedTextField
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
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.ReferenceAssetRecord
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import com.inversioncoach.app.calibration.RuntimeBodyProfileResolver
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.studio.UploadAnalysisDrillAuthoringMapper
import com.inversioncoach.app.movementprofile.ExistingDrillToProfileAdapter
import com.inversioncoach.app.movementprofile.AnalysisProgressObserver
import com.inversioncoach.app.movementprofile.MlKitVideoPoseFrameSource
import com.inversioncoach.app.movementprofile.MovementProfileExtractor
import com.inversioncoach.app.movementprofile.UploadedVideoAnalyzer
import com.inversioncoach.app.movementprofile.VideoPoseFrameSource
import com.inversioncoach.app.movementprofile.MovementProfile
import com.inversioncoach.app.movementprofile.MovementType
import com.inversioncoach.app.movementprofile.CameraViewConstraint
import com.inversioncoach.app.movementprofile.PhaseDefinition
import com.inversioncoach.app.movementprofile.ReadinessRule
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.EffectiveView
import com.inversioncoach.app.overlay.EffectiveViewResolver
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
import com.inversioncoach.app.storage.repository.SessionRepositoryUploadAuthoringStore
import com.inversioncoach.app.storage.repository.ensureDrillForUploadWriteback
import com.inversioncoach.app.storage.repository.persistUploadAuthoredDrillTemplate
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.drillstudio.toDrillTemplate
import com.inversioncoach.app.ui.live.SessionDiagnostics
import com.inversioncoach.app.pose.PoseScaleMode
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
import kotlin.coroutines.coroutineContext

private const val TAG = "UploadVideoFlow"
private const val ANALYSIS_PROGRESS_UPDATE_FRAME_INTERVAL = 1
private const val ANALYSIS_PROGRESS_UPDATE_MS = 100L
private const val UPLOADED_ANALYSIS_SAMPLE_FPS = 6

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
    val phaseLabel: String? = null,
    val processedFrames: Int? = null,
    val totalFrames: Int? = null,
    val phasePercent: Int? = null,
)

enum class UploadTrackingMode {
    HOLD_BASED,
    REP_BASED,
}

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
    val selectedTrackingMode: UploadTrackingMode? = null,
    val analysisPhaseLabel: String = "",
    val analysisProcessedFrames: Int = 0,
    val analysisTotalFrames: Int = 0,
    val analysisPhasePercent: Int? = null,
    val selectedReferenceTemplateId: String? = null,
    val selectedDrillId: String? = null,
    val isReferenceUpload: Boolean = false,
    val createDrillFromReferenceUpload: Boolean = false,
    val pendingDrillName: String = "",
)

data class UploadFlowResult(
    val sessionId: Long,
    val replayUri: String?,
    val rawReady: Boolean,
    val annotatedReady: Boolean,
    val exportFailureReason: String? = null,
    val finalStage: UploadStage,
    val drillId: String? = null,
    val referenceTemplateId: String? = null,
    val authoredDrillWritebackSucceeded: Boolean = false,
)

interface UploadVideoAnalysisRunner {
    suspend fun run(
        uri: Uri,
        trackingMode: UploadTrackingMode,
        selectedDrillId: String? = null,
        selectedReferenceTemplateId: String? = null,
        isReferenceUpload: Boolean = false,
        createDrillFromReferenceUpload: Boolean = false,
        pendingDrillName: String? = null,
        onSessionCreated: (Long) -> Unit,
        onProgress: (UploadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): UploadFlowResult
}

class DefaultUploadVideoAnalysisRunner(
    private val context: Context,
    private val repository: SessionRepository,
    private val runtimeBodyProfileResolver: RuntimeBodyProfileResolver? = null,
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
        val rotationDegrees: Int,
    )

    private data class AnalysisStagePresentation(
        val phaseLabel: String,
        val progressDetail: String,
    )

    override suspend fun run(
        uri: Uri,
        trackingMode: UploadTrackingMode,
        selectedDrillId: String?,
        selectedReferenceTemplateId: String?,
        isReferenceUpload: Boolean,
        createDrillFromReferenceUpload: Boolean,
        pendingDrillName: String?,
        onSessionCreated: (Long) -> Unit,
        onProgress: (UploadProgress) -> Unit,
        onLog: (String) -> Unit,
    ): UploadFlowResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val drillDefinition = selectedDrillId?.let { repository.getDrill(it) }
        val activeProfileContext = runtimeBodyProfileResolver?.resolve()
        validateSelectedDrillForUpload(selectedDrillId, drillDefinition)?.let { error(it) }
        val drillType = DrillType.FREESTYLE
        val resolvedCalibrationProfile: com.inversioncoach.app.calibration.DrillMovementProfile? = null
        val resolvedEffectiveView = EffectiveViewResolver.resolve(
            explicit = null,
            drillDefaultCameraView = drillDefinition?.cameraView,
            freestyleFallback = EffectiveView.FREESTYLE,
        )
        val sessionId = repository.saveSession(
            SessionRecord(
                title = drillDefinition?.name?.let { "$it Upload Analysis" } ?: "Uploaded Video Analysis",
                drillType = drillType,
                sessionSource = SessionSource.UPLOADED_VIDEO,
                startedAtMs = startedAt,
                completedAtMs = startedAt,
                overallScore = 0,
                strongestArea = "Upload analysis",
                limitingFactor = "Pending analysis",
                issues = "",
                wins = "",
                metricsJson = mergeMetricsJson(
                    "",
                    mapOf(
                        "trackingMode" to trackingMode.name,
                        "drillId" to (selectedDrillId ?: ""),
                        "referenceTemplateId" to (selectedReferenceTemplateId ?: ""),
                        "isReferenceUpload" to isReferenceUpload.toString(),
                        "effectiveView" to resolvedEffectiveView.name,
                    ),
                ),
                annotatedVideoUri = null,
                rawVideoUri = null,
                calibrationProfileVersion = null,
                calibrationUpdatedAtMs = null,
                userProfileId = activeProfileContext?.userProfileId,
                bodyProfileId = activeProfileContext?.bodyProfileId,
                bodyProfileVersion = activeProfileContext?.bodyProfileVersion,
                usedDefaultBodyModel = activeProfileContext?.usedDefaultBodyModel ?: true,
                drillId = selectedDrillId,
                referenceTemplateId = selectedReferenceTemplateId,
                notesUri = null,
                bestFrameTimestampMs = null,
                worstFrameTimestampMs = null,
                topImprovementFocus = "Maintain body line",
                rawPersistStatus = RawPersistStatus.PROCESSING,
                annotatedExportStatus = AnnotatedExportStatus.NOT_STARTED,
            ),
        )
        onSessionCreated(sessionId)
        repository.updateUploadPipelineProgress(
            sessionId = sessionId,
            stageLabel = "Importing raw video",
            processedFrames = 0,
            totalFrames = 0,
            detail = "Importing uploaded source video",
        )
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

        fun logStage(stage: String, message: String) {
            val line = "session=$sessionId | stage=$stage | $message"
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
            repository.updateMediaPipelineState(sessionId) { session ->
                session.copy(
                    completedAtMs = startedAt + sourceDurationMs,
                    rawVideoUri = persistedRawUri,
                    bestPlayableUri = persistedRawUri,
                    calibrationProfileVersion = resolvedCalibrationProfile?.profileVersion,
                    calibrationUpdatedAtMs = resolvedCalibrationProfile?.updatedAtMs,
                    metricsJson = mergeMetricsJson(
                        session.metricsJson,
                        mapOf(
                            "trackingMode" to trackingMode.name,
                            "calibrationProfileVersion" to (resolvedCalibrationProfile?.profileVersion?.toString() ?: ""),
                            "calibrationUpdatedAtMs" to (resolvedCalibrationProfile?.updatedAtMs?.toString() ?: ""),
                            "sourceDurationMs" to sourceDurationMs.toString(),
                            "sourceWidth" to metadata.width.toString(),
                            "sourceHeight" to metadata.height.toString(),
                        ),
                    ),
                )
            }
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
            currentStage = UploadStage.RAW_IMPORT_COMPLETE
            logStage("RAW_IMPORTED", "rawUri=$persistedRawUri durationMs=$sourceDurationMs width=${metadata.width} height=${metadata.height}")

            val profile = drillDefinition?.let { buildMovementProfileFromDrill(it) } ?: ExistingDrillToProfileAdapter().fromDrill(drillType)
            currentStage = UploadStage.PREPARING_ANALYSIS
            repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.VALIDATING_INPUT)
            repository.updateAnnotatedExportProgress(
                sessionId = sessionId,
                stage = AnnotatedExportStage.PREPARING,
                percent = 20,
                etaSeconds = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )
            repository.updateUploadPipelineProgress(
                sessionId = sessionId,
                stageLabel = "Analyzing uploaded video",
                processedFrames = 0,
                totalFrames = 0,
                detail = "Preparing uploaded analysis",
            )
            onProgress(UploadProgress(currentStage, 0.2f, detail = "Preparing uploaded analysis"))
            val frameSource = frameSourceFactory(context.applicationContext, UPLOADED_ANALYSIS_SAMPLE_FPS)
            val analyzer = analyzerFactory(frameSource)

            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.OVERLAY_CAPTURE,
                status = SessionDiagnostics.Status.STARTED,
                message = "Uploaded frame analysis started",
                metrics = mapOf("analysisFps" to UPLOADED_ANALYSIS_SAMPLE_FPS.toString()),
            )
            currentStage = UploadStage.ANALYZING_VIDEO
            logStage("ANALYZING_UPLOADED_VIDEO", "analysis_started=true")
            repository.updateUploadPipelineProgress(
                sessionId = sessionId,
                stageLabel = "Analyzing uploaded video",
                processedFrames = 0,
                totalFrames = 0,
                detail = "Sampling uploaded frames",
            )
            onProgress(UploadProgress(currentStage, 0.25f, detail = "Analyzing uploaded frames"))
            val analysisStart = System.currentTimeMillis()
            val progressScope = CoroutineScope(coroutineContext)
            var lastAnalysisPercent = 25
            var lastAnalysisUiProcessed = 0
            var lastAnalysisUiAt = 0L
            var analyzedFramesForUi = 0
            var totalFramesForUi = 0
            var lastPersistedStageLabel: String? = null
            var lastPersistedProcessedFrames = -1
            var lastPersistedTotalFrames = -1
            var lastPersistedAtMs = 0L
            fun persistUploadProgressThrottled(
                stageLabel: String?,
                processedFrames: Int,
                totalFrames: Int,
                timestampMs: Long? = null,
                detail: String? = null,
                force: Boolean = false,
            ) {
                val normalizedProcessed = processedFrames.coerceAtLeast(0)
                val normalizedTotal = totalFrames.coerceAtLeast(0)
                val now = System.currentTimeMillis()
                val stageChanged = stageLabel != lastPersistedStageLabel
                val advancedFrames = normalizedProcessed - lastPersistedProcessedFrames >= 3
                val totalChanged = normalizedTotal != lastPersistedTotalFrames
                val timeElapsed = now - lastPersistedAtMs >= 500L
                val shouldPersist = force || stageChanged || advancedFrames || totalChanged || timeElapsed
                if (!shouldPersist) return
                lastPersistedStageLabel = stageLabel
                lastPersistedProcessedFrames = normalizedProcessed
                lastPersistedTotalFrames = normalizedTotal
                lastPersistedAtMs = now
                progressScope.launch {
                    repository.updateUploadPipelineProgress(
                        sessionId = sessionId,
                        stageLabel = stageLabel,
                        processedFrames = normalizedProcessed,
                        totalFrames = normalizedTotal,
                        timestampMs = timestampMs,
                        detail = detail,
                    )
                }
            }
            val analysis = analyzer.analyze(
                Uri.parse(persistedRawUri),
                profile,
                drillMovementProfile = resolvedCalibrationProfile,
                progressObserver = AnalysisProgressObserver { event ->
                    val estimatedTotal = event.estimatedTotalFrames?.coerceAtLeast(1)
                    val processed = event.processedFrames.coerceAtLeast(0)
                    val boundedProcessed = if (estimatedTotal != null) processed.coerceAtMost(estimatedTotal) else processed
                    val isAnalyzedFrameEvent = event.stage in setOf(
                        "pose_detection_complete",
                        "analysis_frame_processed",
                        "analysis_frame_dropped",
                        "analysis_complete",
                    )
                    if (isAnalyzedFrameEvent) {
                        analyzedFramesForUi = maxOf(analyzedFramesForUi, boundedProcessed)
                    }
                    if (estimatedTotal != null && estimatedTotal > 0) {
                        totalFramesForUi = maxOf(totalFramesForUi, estimatedTotal)
                    }
                    val movementPercent = if (totalFramesForUi > 0) {
                        ((analyzedFramesForUi * 100f) / totalFramesForUi.toFloat()).toInt().coerceIn(0, 100)
                    } else {
                        null
                    }

                    val presentation = analysisStagePresentation(
                        stage = event.stage,
                        estimatedTotal = estimatedTotal,
                        boundedProcessed = boundedProcessed,
                        analyzedFramesForUi = analyzedFramesForUi,
                        totalFramesForUi = totalFramesForUi,
                    )
                    val phaseLabel = presentation.phaseLabel
                    val progressDetail = presentation.progressDetail
                    val processedForPersistence = analyzedFramesForUi
                    val totalForPersistence = totalFramesForUi.takeIf { it > 0 } ?: (estimatedTotal ?: 0)
                    persistUploadProgressThrottled(
                        stageLabel = phaseLabel,
                        processedFrames = processedForPersistence,
                        totalFrames = totalForPersistence,
                        timestampMs = event.timestampMs,
                        detail = progressDetail,
                        force = event.stage in setOf("decode_start", "analysis_started", "decode_complete", "analysis_complete", "analysis_exception"),
                    )

                    if (totalFramesForUi > 0 && analyzedFramesForUi > 0) {
                        val progressWindow = (analyzedFramesForUi.toFloat() / totalFramesForUi.toFloat()) * 35f
                        val percent = (25f + progressWindow).toInt().coerceIn(25, 60)
                        val now = System.currentTimeMillis()
                        val shouldEmitUi =
                            percent > lastAnalysisPercent ||
                                analyzedFramesForUi - lastAnalysisUiProcessed >= ANALYSIS_PROGRESS_UPDATE_FRAME_INTERVAL ||
                                now - lastAnalysisUiAt >= ANALYSIS_PROGRESS_UPDATE_MS ||
                                analyzedFramesForUi == totalFramesForUi
                        if (percent > lastAnalysisPercent) {
                            lastAnalysisPercent = percent
                            progressScope.launch {
                                repository.updateAnnotatedExportProgress(
                                    sessionId = sessionId,
                                    stage = AnnotatedExportStage.DECODING_SOURCE,
                                    percent = percent,
                                    etaSeconds = null,
                                    elapsedMs = System.currentTimeMillis() - startedAt,
                                )
                            }
                        }
                        if (shouldEmitUi) {
                            lastAnalysisUiProcessed = analyzedFramesForUi
                            lastAnalysisUiAt = now
                            onProgress(
                                UploadProgress(
                                    currentStage,
                                    (lastAnalysisPercent / 100f).coerceIn(0f, 1f),
                                    detail = "Analyzing movement: $analyzedFramesForUi / $totalFramesForUi frames",
                                    phaseLabel = phaseLabel,
                                    processedFrames = analyzedFramesForUi,
                                    totalFrames = totalFramesForUi,
                                    phasePercent = movementPercent,
                                ),
                            )
                        }
                    } else if (event.stage == "decode_start" || event.stage == "frame_sampled") {
                        onProgress(
                            UploadProgress(
                                currentStage,
                                lastAnalysisPercent / 100f,
                                detail = progressDetail,
                                phaseLabel = phaseLabel,
                                processedFrames = analyzedFramesForUi,
                                totalFrames = totalFramesForUi.takeIf { it > 0 } ?: (estimatedTotal ?: 0),
                                phasePercent = 0,
                            ),
                        )
                    } else if (event.stage == "analysis_complete") {
                        onProgress(
                            UploadProgress(
                                currentStage,
                                lastAnalysisPercent / 100f,
                                detail = "Finalizing results...",
                                phaseLabel = phaseLabel,
                                processedFrames = analyzedFramesForUi.coerceAtLeast(totalFramesForUi),
                                totalFrames = totalFramesForUi.coerceAtLeast(analyzedFramesForUi),
                                phasePercent = 100,
                            ),
                        )
                    }

                    if (event.stage in setOf("decode_start", "analysis_started", "decode_complete", "analysis_complete", "analysis_exception")) {
                        log(
                            "analysis_progress stage=${event.stage} processed=${event.processedFrames} total=${event.estimatedTotalFrames ?: -1} dropped=${event.droppedFrames} timestampMs=${event.timestampMs ?: -1} detail=${event.detail ?: ""}",
                        )
                    } else if (event.processedFrames % 2 == 0 && event.processedFrames > 0) {
                        log(
                            "analysis_progress stage=${event.stage} processed=${event.processedFrames} total=${event.estimatedTotalFrames ?: -1} dropped=${event.droppedFrames} timestampMs=${event.timestampMs ?: -1}",
                        )
                    }
                },
            )
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
            val extractor = MovementProfileExtractor()
            val writebackStore = SessionRepositoryUploadAuthoringStore(repository)
            var drillId = selectedDrillId ?: "legacy_${drillType.name}"
            var templateId = selectedReferenceTemplateId
            var writebackSeedDrill: com.inversioncoach.app.model.DrillDefinitionRecord? = drillDefinition
            val assetId = "asset-$sessionId"
            val profileId = "profile-$sessionId"
            repository.saveReferenceAsset(
                ReferenceAssetRecord(
                    id = assetId,
                    drillId = drillId,
                    displayName = if (isReferenceUpload) "Reference Upload $sessionId" else "Attempt Upload $sessionId",
                    ownerType = "USER",
                    sourceType = "UPLOAD",
                    videoUri = persistedRawUri,
                    poseUri = null,
                    profileUri = profileId,
                    thumbnailUri = null,
                    isReference = isReferenceUpload,
                    qualityLabel = null,
                    createdAtMs = System.currentTimeMillis(),
                ),
            )
            val subjectProfile = extractor.fromAnalysis(
                profileId = profileId,
                assetId = assetId,
                drillId = drillId,
                extractionVersion = 1,
                analysis = analysis,
                createdAtMs = System.currentTimeMillis(),
            )
            repository.saveMovementProfile(subjectProfile)

            if (isReferenceUpload) {
                val template = if (createDrillFromReferenceUpload) {
                    val fileNameSeed = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.substringBeforeLast('.')
                        ?.replace(Regex("[_-]+"), " ")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    val drillName = pendingDrillName
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: fileNameSeed
                        ?: "Custom Drill"
                    val (createdDrill, createdTemplate) = repository.createDrillFromReferenceUpload(
                        drillName = drillName,
                        description = "Created from uploaded reference video.",
                        sourceProfile = subjectProfile,
                        sourceSessionId = sessionId,
                    )
                    drillId = createdDrill.id
                    writebackSeedDrill = createdDrill
                    createdTemplate
                } else {
                    repository.createTemplateFromReferenceUpload(
                        drillId = drillId,
                        sourceProfile = subjectProfile,
                        title = "${drillDefinition?.name ?: drillType.displayName} Reference",
                        sourceSessionId = sessionId,
                        isBaseline = templatesAreEmptyForDrill(repository, drillId),
                    )
                }
                templateId = template.id
                repository.updateMediaPipelineState(sessionId) { session ->
                    session.copy(
                        drillId = drillId,
                        referenceTemplateId = template.id,
                        metricsJson = mergeMetricsJson(
                            session.metricsJson,
                            mapOf(
                                "drillId" to drillId,
                                "referenceTemplateId" to template.id,
                                "referenceTemplateName" to template.displayName,
                            ),
                        ),
                    )
                }
            }
            val writebackTarget = ensureDrillForUploadWriteback(
                store = writebackStore,
                preferredDrill = writebackSeedDrill,
                preferredDrillId = drillId.takeIf { !it.startsWith("legacy_") },
                createDrillFromReferenceUpload = createDrillFromReferenceUpload,
                pendingDrillName = pendingDrillName,
            )
            val drillRecord = writebackTarget?.drill
            var authoredWritebackSucceeded = false
            if (drillRecord != null) {
                drillId = drillRecord.id
                val extractedPayload = UploadAnalysisDrillAuthoringMapper.fromAnalysis(
                    analysis = analysis,
                    movementType = if (trackingMode == UploadTrackingMode.REP_BASED) CatalogMovementType.REP else CatalogMovementType.HOLD,
                )
                val updatedTemplate = UploadAnalysisDrillAuthoringMapper.applyToTemplate(
                    seed = drillRecord.toDrillTemplate(seed = null),
                    payload = extractedPayload,
                )
                persistUploadAuthoredDrillTemplate(
                    store = writebackStore,
                    record = drillRecord,
                    template = updatedTemplate,
                    ready = drillRecord.status == DrillStatus.READY,
                )
                repository.updateMediaPipelineState(sessionId) { session ->
                    session.copy(
                        drillId = drillRecord.id,
                        referenceTemplateId = templateId,
                        metricsJson = mergeMetricsJson(
                            session.metricsJson,
                            extractedPayload.inferredMetadata + mapOf(
                                "drillId" to drillRecord.id,
                                "comparisonDeferred" to "true",
                            ),
                        ),
                    )
                }
                authoredWritebackSucceeded = true
                log("repo_write authored_drill_updated drillId=${drillRecord.id} phaseCount=${extractedPayload.phases.size} keyframeCount=${extractedPayload.keyframes.size}")
            } else {
                log("repo_write authored_drill_skipped reason=missing_drill drillId=$drillId")
            }

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
                    effectiveView = resolvedEffectiveView,
                    freestyleViewMode = viewMode,
                    bodyVisible = point.confidence > 0f,
                    showSkeleton = true,
                    showIdealLine = true,
                    showCenterOfGravity = true,
                    mirrorMode = false,
                    sourceWidth = metadata.width,
                    sourceHeight = metadata.height,
                    sourceRotationDegrees = metadata.rotationDegrees,
                    scaleMode = PoseScaleMode.FIT,
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
            repository.updateUploadPipelineProgress(
                sessionId = sessionId,
                stageLabel = "Rendering annotated video",
                processedFrames = analysis.overlayTimeline.size,
                totalFrames = analysis.overlayTimeline.size + analysis.droppedFrames,
                detail = "Rendering annotated video",
            )
            currentStage = UploadStage.RENDERING_OVERLAY
            onProgress(UploadProgress(currentStage, 0.65f, detail = "Preparing overlay timeline"))

            currentStage = UploadStage.EXPORTING_ANNOTATED_VIDEO
            repository.updateUploadPipelineProgress(
                sessionId = sessionId,
                stageLabel = "Exporting video",
                processedFrames = analysis.overlayTimeline.size,
                totalFrames = analysis.overlayTimeline.size + analysis.droppedFrames,
                detail = "Exporting video",
            )
            onProgress(UploadProgress(currentStage, 0.72f, detail = "Encoding annotated output"))
            logStage("EXPORTING_ANNOTATED_VIDEO", "export_started=true")
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
                drillCameraSide = drillDefinition?.let(::toDrillCameraSide) ?: DrillCameraSide.LEFT,
                overlayTimeline = overlayTimeline,
                preset = preset,
                onRenderProgress = { rendered, total ->
                    val ratio = if (total <= 0) 0f else rendered.toFloat() / total.toFloat()
                    val percent = (72 + (ratio * 18f)).toInt()
                    persistUploadProgressThrottled(
                        stageLabel = "Rendering annotated video",
                        processedFrames = rendered,
                        totalFrames = total,
                        detail = "Rendering annotated video",
                    )
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
            logStage("VERIFYING_OUTPUT", "annotatedUri=${export.persistedUri.orEmpty()} verified=${!export.persistedUri.isNullOrBlank()}")

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
                    calibrationProfileVersion = resolvedCalibrationProfile?.profileVersion,
                    calibrationUpdatedAtMs = resolvedCalibrationProfile?.updatedAtMs,
                    metricsJson = mergeMetricsJson(
                        session.metricsJson,
                        mapOf(
                            "trackingMode" to trackingMode.name,
                            "calibrationProfileVersion" to (resolvedCalibrationProfile?.profileVersion?.toString() ?: ""),
                            "calibrationUpdatedAtMs" to (resolvedCalibrationProfile?.updatedAtMs?.toString() ?: ""),
                            "validFrames" to analysis.overlayTimeline.size.toString(),
                            "invalidFrames" to analysis.droppedFrames.toString(),
                            "totalJobTimeMs" to (now - startedAt).toString(),
                            "sessionTrackedMs" to sourceDurationMs.toString(),
                        ),
                    ),
                    bestPlayableUri = replayUri,
                    rawVideoUri = persistedRawUri,
                    annotatedVideoUri = export.persistedUri,
                    overlayFrameCount = overlayFrames.size,
                    overlayTimelineUri = overlayTimelineUri,
                )
            }
            logStage("BUILDING_UPLOAD_SUMMARY", "summary_written=true")
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
                repository.updateUploadPipelineProgress(
                    sessionId = sessionId,
                    stageLabel = "Completed",
                    processedFrames = analysis.overlayTimeline.size + analysis.droppedFrames,
                    totalFrames = analysis.overlayTimeline.size + analysis.droppedFrames,
                    detail = "Annotated replay ready",
                )
                currentStage = UploadStage.COMPLETED_ANNOTATED
                onProgress(UploadProgress(currentStage, 1f, etaMs = 0L, detail = "Annotated replay ready"))
                log("complete annotatedReady=true replayUri=$replayUri")
                logStage("COMPLETED", "replaySource=annotated")
                SessionDiagnostics.persistReport(sessionId, repository.observeSession(sessionId).first(), repository)
                return@withContext UploadFlowResult(
                    sessionId = sessionId,
                    replayUri = replayUri,
                    rawReady = true,
                    annotatedReady = true,
                    finalStage = UploadStage.COMPLETED_ANNOTATED,
                    drillId = drillId,
                    referenceTemplateId = templateId,
                    authoredDrillWritebackSucceeded = authoredWritebackSucceeded,
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
            repository.updateUploadPipelineProgress(
                sessionId = sessionId,
                stageLabel = "Completed",
                processedFrames = analysis.overlayTimeline.size + analysis.droppedFrames,
                totalFrames = analysis.overlayTimeline.size + analysis.droppedFrames,
                detail = "Raw replay ready; annotated export failed",
            )
            currentStage = UploadStage.COMPLETED_RAW_ONLY
            onProgress(UploadProgress(currentStage, 1f, etaMs = 0L, detail = "Raw replay ready; annotated export failed"))
            log("complete annotatedReady=false reason=$failureReason")
            logStage("COMPLETED", "replaySource=raw")
            SessionDiagnostics.persistReport(sessionId, repository.observeSession(sessionId).first(), repository)
            UploadFlowResult(
                sessionId = sessionId,
                replayUri = replayUri,
                rawReady = true,
                annotatedReady = false,
                exportFailureReason = failureReason,
                finalStage = UploadStage.COMPLETED_RAW_ONLY,
                drillId = drillId,
                referenceTemplateId = templateId,
                authoredDrillWritebackSucceeded = authoredWritebackSucceeded,
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
            repository.updateUploadPipelineProgress(
                sessionId = sessionId,
                stageLabel = "Failed",
                detail = "Upload workflow failed",
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
            logStage("FAILED", "reason=${error.message ?: mappedFailure}")
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
                rotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun mergeMetricsJson(base: String, updates: Map<String, String>): String {
        val pairs = base.split('|')
            .mapNotNull { token ->
                val idx = token.indexOf(':')
                if (idx <= 0) null else token.substring(0, idx) to token.substring(idx + 1)
            }
            .toMap()
            .toMutableMap()
        updates.forEach { (key, value) ->
            pairs[key] = value
        }
        return pairs.entries.joinToString(separator = "|") { (key, value) -> "$key:$value" }
    }

    private fun analysisStagePresentation(
        stage: String,
        estimatedTotal: Int?,
        boundedProcessed: Int,
        analyzedFramesForUi: Int,
        totalFramesForUi: Int,
    ): AnalysisStagePresentation {
        val phaseLabel = when (stage) {
            "decode_start" -> "Analyzing uploaded video"
            "frame_sampled" -> "Sampling video frames"
            "analysis_complete" -> "Finalizing results..."
            else -> "Analyzing movement"
        }
        val progressDetail = when (stage) {
            "decode_start" -> "Analyzing uploaded video"
            "frame_sampled" -> if (estimatedTotal != null && boundedProcessed > 0) {
                "Sampling uploaded frames: $boundedProcessed / $estimatedTotal"
            } else {
                "Sampling uploaded frames"
            }
            "analysis_complete" -> "Finalizing results..."
            else -> if (totalFramesForUi > 0 && analyzedFramesForUi > 0) {
                "Analyzing movement: $analyzedFramesForUi / $totalFramesForUi frames"
            } else {
                "Analyzing movement"
            }
        }
        return AnalysisStagePresentation(
            phaseLabel = phaseLabel,
            progressDetail = progressDetail,
        )
    }

    private fun buildMovementProfileFromDrill(drill: com.inversioncoach.app.model.DrillDefinitionRecord): MovementProfile {
        val phases = drill.phaseSchemaJson.split('|').filter { it.isNotBlank() }
        val movementType = if (drill.movementMode == "REP") MovementType.REP else MovementType.HOLD
        val view = when (drill.cameraView) {
            DrillCameraView.FRONT -> CameraViewConstraint.FRONT
            DrillCameraView.LEFT -> CameraViewConstraint.SIDE_LEFT
            DrillCameraView.RIGHT -> CameraViewConstraint.SIDE_RIGHT
            DrillCameraView.BACK -> CameraViewConstraint.BACK
            DrillCameraView.FREESTYLE -> CameraViewConstraint.ANY
            "SIDE" -> CameraViewConstraint.SIDE_LEFT
            else -> CameraViewConstraint.ANY
        }
        return MovementProfile(
            id = drill.id,
            displayName = drill.name,
            drillType = null,
            movementType = movementType,
            allowedViews = setOf(view, CameraViewConstraint.ANY),
            phaseDefinitions = phases.mapIndexed { index, phase ->
                PhaseDefinition(id = phase, displayName = phase.replaceFirstChar { it.uppercase() }, sequenceIndex = index)
            }.ifEmpty { listOf(PhaseDefinition(id = "setup", displayName = "Setup", sequenceIndex = 0)) },
            alignmentRules = emptyList(),
            readinessRule = ReadinessRule(
                minConfidence = 0.35f,
                requiredLandmarks = setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip"),
                minVisibleLandmarkCount = 3,
                sideViewPrimary = drill.cameraView in setOf(DrillCameraView.LEFT, DrillCameraView.RIGHT, "SIDE"),
            ),
            keyJoints = drill.keyJointsJson.split('|').filter { it.isNotBlank() }.toSet().ifEmpty { setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip") },
        )
    }

    private fun toDrillCameraSide(drill: com.inversioncoach.app.model.DrillDefinitionRecord): DrillCameraSide = when (drill.cameraView) {
        DrillCameraView.RIGHT -> DrillCameraSide.RIGHT
        else -> DrillCameraSide.LEFT
    }

    private suspend fun templatesAreEmptyForDrill(repository: SessionRepository, drillId: String): Boolean =
        repository.listTemplatesForDrill(drillId).first().isEmpty()
}

internal fun validateSelectedDrillForUpload(
    selectedDrillId: String?,
    drillDefinition: com.inversioncoach.app.model.DrillDefinitionRecord?,
): String? {
    if (selectedDrillId == null) return null
    if (drillDefinition == null) return "Selected drill no longer exists. Please choose another drill."
    if (drillDefinition.status != DrillStatus.READY) {
        return "Selected drill is not ready yet. Open Manage Drills and mark it ready first."
    }
    return null
}

class UploadVideoViewModel(
    private val runner: UploadVideoAnalysisRunner,
    private val repository: SessionRepository?,
    private val selectedDrillId: String?,
    private val selectedReferenceTemplateId: String?,
    private val isReferenceUpload: Boolean,
    private val createDrillFromReferenceUpload: Boolean,
    private val pendingDrillName: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        UploadVideoUiState(
            selectedReferenceTemplateId = selectedReferenceTemplateId,
            selectedDrillId = selectedDrillId,
            isReferenceUpload = isReferenceUpload,
            createDrillFromReferenceUpload = createDrillFromReferenceUpload,
            pendingDrillName = pendingDrillName.orEmpty(),
        ),
    )
    val state: StateFlow<UploadVideoUiState> = _state.asStateFlow()
    companion object {
        private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var activeJob: Job? = null
        private var activeSessionId: Long? = null
    }

    constructor(runner: UploadVideoAnalysisRunner) : this(runner, null, null, null, false, false, null)

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
                            stageText = session.uploadProgressDetail ?: current.stageText,
                            rawVideoStatus = session.rawPersistStatus,
                            annotatedVideoStatus = session.annotatedExportStatus,
                            etaMs = session.annotatedExportEtaSeconds?.times(1000L),
                            analysisPhaseLabel = session.uploadPipelineStageLabel ?: current.analysisPhaseLabel,
                            analysisProcessedFrames = session.uploadAnalysisProcessedFrames.coerceAtLeast(current.analysisProcessedFrames),
                            analysisTotalFrames = maxOf(session.uploadAnalysisTotalFrames, current.analysisTotalFrames),
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

    fun onTrackingModeSelected(mode: UploadTrackingMode) {
        _state.update {
            it.copy(
                selectedTrackingMode = mode,
                errorMessage = null,
            )
        }
    }


    fun onPendingDrillNameChanged(name: String) {
        _state.update { it.copy(pendingDrillName = name, errorMessage = null) }
    }

    fun analyze(uri: Uri) {
        if (activeJob?.isActive == true) return
        val trackingMode = _state.value.selectedTrackingMode
        if (trackingMode == null) {
            _state.update {
                it.copy(
                    stage = UploadStage.FAILED,
                    currentProcessingStage = UploadStage.FAILED,
                    stageText = "Select movement type",
                    errorMessage = "Choose Hold-based or Rep-based before processing an upload.",
                    canCancel = false,
                )
            }
            return
        }
        if (createDrillFromReferenceUpload && _state.value.pendingDrillName.isBlank()) {
            _state.update {
                it.copy(
                    stage = UploadStage.FAILED,
                    currentProcessingStage = UploadStage.FAILED,
                    stageText = "Name required",
                    errorMessage = "Enter a drill name before uploading a new reference drill.",
                    canCancel = false,
                )
            }
            return
        }
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
                    analysisPhaseLabel = "",
                    analysisProcessedFrames = 0,
                    analysisTotalFrames = 0,
                    analysisPhasePercent = null,
                )
            }
            runCatching {
                runner.run(
                    uri = uri,
                    trackingMode = trackingMode,
                    selectedDrillId = selectedDrillId,
                    selectedReferenceTemplateId = selectedReferenceTemplateId,
                    isReferenceUpload = isReferenceUpload,
                    createDrillFromReferenceUpload = createDrillFromReferenceUpload,
                    pendingDrillName = _state.value.pendingDrillName,
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
                                analysisPhaseLabel = progress.phaseLabel ?: current.analysisPhaseLabel,
                                analysisProcessedFrames = progress.processedFrames ?: current.analysisProcessedFrames,
                                analysisTotalFrames = progress.totalFrames ?: current.analysisTotalFrames,
                                analysisPhasePercent = progress.phasePercent ?: current.analysisPhasePercent,
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
                val completionMessage = if (result.authoredDrillWritebackSucceeded && result.annotatedReady) {
                    "Annotated replay ready; extracted drill phases saved (comparison deferred)"
                } else if (result.authoredDrillWritebackSucceeded) {
                    "Raw replay ready, annotated replay unavailable; extracted drill phases saved (comparison deferred)"
                } else if (result.annotatedReady) {
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
                        selectedDrillId = result.drillId ?: it.selectedDrillId,
                        selectedReferenceTemplateId = result.referenceTemplateId ?: it.selectedReferenceTemplateId,
                        errorMessage = result.exportFailureReason?.let { reason -> "Annotated stage failed: $reason" },
                        progressPercent = 1f,
                        canCancel = false,
                        rawVideoStatus = if (result.rawReady) RawPersistStatus.SUCCEEDED else RawPersistStatus.FAILED,
                        annotatedVideoStatus = if (result.annotatedReady) AnnotatedExportStatus.ANNOTATED_READY else AnnotatedExportStatus.ANNOTATED_FAILED,
                        analysisPhaseLabel = "",
                        analysisProcessedFrames = 0,
                        analysisTotalFrames = 0,
                        analysisPhasePercent = null,
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
                        analysisPhaseLabel = "",
                        analysisProcessedFrames = 0,
                        analysisTotalFrames = 0,
                        analysisPhasePercent = null,
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
            it.copy(
                stage = UploadStage.CANCELLED,
                currentProcessingStage = UploadStage.CANCELLED,
                stageText = "Analysis cancelled",
                canCancel = false,
                analysisPhaseLabel = "",
                analysisProcessedFrames = 0,
                analysisTotalFrames = 0,
                analysisPhasePercent = null,
            )
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
    onOpenDrillStudio: ((String, String?) -> Unit)? = null,
    selectedDrillId: String? = null,
    selectedReferenceTemplateId: String? = null,
    isReferenceUpload: Boolean = false,
    createDrillFromReferenceUpload: Boolean = false,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val defaultDraftName = remember(selectedDrillId) {
        selectedDrillId?.substringAfterLast("_")
            ?.replace(Regex("[_-]+"), " ")
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ?: ""
    }
    val viewModel = remember(selectedDrillId, selectedReferenceTemplateId, isReferenceUpload, createDrillFromReferenceUpload) {
        UploadVideoViewModel(
            DefaultUploadVideoAnalysisRunner(
                context = context.applicationContext,
                repository = repository,
                runtimeBodyProfileResolver = ServiceLocator.runtimeBodyProfileResolver(context),
            ),
            repository,
            selectedDrillId,
            selectedReferenceTemplateId,
            isReferenceUpload,
            createDrillFromReferenceUpload,
            pendingDrillName = defaultDraftName,
        )
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
            Text("Movement type: ${state.selectedTrackingMode?.name ?: "Not selected"}", style = MaterialTheme.typography.bodySmall)
            Text("Drill: ${state.selectedDrillId ?: "Legacy freestyle"}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Reference template: ${state.selectedReferenceTemplateId ?: "None"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Upload role: ${if (state.isReferenceUpload) "Reference" else "Attempt"}", style = MaterialTheme.typography.bodySmall)
            if (state.createDrillFromReferenceUpload) {
                Text("New drill will be created from this reference upload.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = state.pendingDrillName,
                    onValueChange = viewModel::onPendingDrillNameChanged,
                    label = { Text("New drill name") },
                    supportingText = { Text("You can rename it later in Drill Studio.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Raw video status: ${state.rawVideoStatus.name}", style = MaterialTheme.typography.bodySmall)
            Text("Annotated video status: ${state.annotatedVideoStatus.name}", style = MaterialTheme.typography.bodySmall)
            state.selectedVideoUri?.let { Text("Selected: $it", style = MaterialTheme.typography.bodySmall) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { viewModel.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.selectedTrackingMode == UploadTrackingMode.HOLD_BASED) "✓ Hold-based" else "Hold-based")
                }
                OutlinedButton(
                    onClick = { viewModel.onTrackingModeSelected(UploadTrackingMode.REP_BASED) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.selectedTrackingMode == UploadTrackingMode.REP_BASED) "✓ Rep-based" else "Rep-based")
                }
            }

            if (state.stage in inFlightStages || state.stage == UploadStage.RAW_IMPORT_COMPLETE) {
                CircularProgressIndicator()
                LinearProgressIndicator(progress = { state.progressPercent }, modifier = Modifier.fillMaxWidth())
                Text("${(state.progressPercent * 100).toInt()}%")
                state.analysisPhaseLabel.takeIf { it.isNotBlank() }?.let { phase ->
                    Text(phase, style = MaterialTheme.typography.bodySmall)
                }
                if (state.analysisTotalFrames > 0) {
                    Text(
                        "Analyzing movement: ${state.analysisProcessedFrames.coerceAtMost(state.analysisTotalFrames)} / ${state.analysisTotalFrames} frames",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.analysisPhasePercent?.let { phasePercent ->
                    Text("Analyzing movement (${phasePercent}%)", style = MaterialTheme.typography.bodySmall)
                }
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
                enabled = state.stage !in inFlightStages && state.selectedTrackingMode != null,
            ) {
                Text("Choose Video")
            }
            if (state.selectedTrackingMode == null) {
                Text("Select movement type before choosing a video.", style = MaterialTheme.typography.bodySmall)
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
                if (onOpenDrillStudio != null && !state.selectedDrillId.isNullOrBlank()) {
                    OutlinedButton(onClick = { onOpenDrillStudio(state.selectedDrillId!!, state.selectedReferenceTemplateId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open populated Drill Studio")
                    }
                }
            } else if (resultSessionId != null) {
                OutlinedButton(onClick = { onOpenResults(resultSessionId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("View session")
                }
            }
        }
    }
}
