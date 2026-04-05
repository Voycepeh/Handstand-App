package com.inversioncoach.app.ui.upload

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.UploadJobStage
import com.inversioncoach.app.model.UploadJobStatus
import com.inversioncoach.app.model.UploadProcessingJob
import com.inversioncoach.app.storage.repository.SessionRepository
import com.inversioncoach.app.storage.repository.UploadProcessingQueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

data class ActiveUploadSessionState(
    val ownerToken: String,
    val selectedVideoUri: Uri,
    val trackingMode: UploadTrackingMode,
    val selectedDrillId: String?,
    val selectedReferenceTemplateId: String?,
    val isReferenceUpload: Boolean,
    val createDrillFromReferenceUpload: Boolean,
    val pendingDrillName: String?,
    val startedAtMs: Long,
    val stage: UploadStage = UploadStage.VIDEO_SELECTED,
    val stageText: String = UploadStage.VIDEO_SELECTED.label,
    val progressPercent: Float = 0f,
    val etaMs: Long? = null,
    val analysisPhaseLabel: String = "",
    val analysisProcessedFrames: Int = 0,
    val analysisTotalFrames: Int = 0,
    val analysisPhasePercent: Int? = null,
    val sessionId: Long? = null,
    val replayUri: String? = null,
    val technicalLog: String = "",
    val errorMessage: String? = null,
    val isTerminal: Boolean = false,
)

data class ActiveUploadCoordinatorState(
    val activeSession: ActiveUploadSessionState? = null,
    val blockedMessage: String? = null,
)

data class ActiveUploadRequest(
    val sourceUri: Uri,
    val trackingMode: UploadTrackingMode,
    val selectedDrillId: String?,
    val selectedReferenceTemplateId: String?,
    val isReferenceUpload: Boolean,
    val createDrillFromReferenceUpload: Boolean,
    val pendingDrillName: String?,
)

sealed interface ActiveUploadStartResult {
    data class Started(val ownerToken: String) : ActiveUploadStartResult
    data class Blocked(val message: String) : ActiveUploadStartResult
}

private data class WorkDependencies(
    val appContext: Context,
    val sessionRepository: SessionRepository,
    val queueRepository: UploadProcessingQueueRepository,
    val workManager: WorkManager,
)

class ActiveUploadCoordinator {
    private val _state = MutableStateFlow(ActiveUploadCoordinatorState())
    val state: StateFlow<ActiveUploadCoordinatorState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private val scope: CoroutineScope
    private val runner: UploadVideoAnalysisRunner?
    private val workDependencies: WorkDependencies?

    constructor(
        scope: CoroutineScope,
        runner: UploadVideoAnalysisRunner,
    ) {
        this.scope = scope
        this.runner = runner
        this.workDependencies = null
    }

    constructor(
        appContext: Context,
        sessionRepository: SessionRepository,
        queueRepository: UploadProcessingQueueRepository,
        workManager: WorkManager = WorkManager.getInstance(appContext),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ) {
        this.scope = scope
        this.runner = null
        this.workDependencies = WorkDependencies(
            appContext = appContext.applicationContext,
            sessionRepository = sessionRepository,
            queueRepository = queueRepository,
            workManager = workManager,
        )
        observeDurableJobs()
    }

    fun start(request: ActiveUploadRequest): ActiveUploadStartResult {
        return if (workDependencies != null) {
            startDurableWork(request, workDependencies)
        } else {
            startInMemoryWork(request)
        }
    }

    private fun startDurableWork(request: ActiveUploadRequest, deps: WorkDependencies): ActiveUploadStartResult {
        val queued = runBlocking(Dispatchers.IO) {
            deps.queueRepository.enqueue(
                sourceUri = request.sourceUri.toString(),
                trackingMode = request.trackingMode.name,
                selectedDrillId = request.selectedDrillId,
                selectedReferenceTemplateId = request.selectedReferenceTemplateId,
                isReferenceUpload = request.isReferenceUpload,
                createDrillFromReferenceUpload = request.createDrillFromReferenceUpload,
                pendingDrillName = request.pendingDrillName,
            )
        }
        if (queued == null) {
            val message = "Another upload is already in progress."
            _state.update { it.copy(blockedMessage = message) }
            return ActiveUploadStartResult.Blocked(message)
        }

        val requestBuilder = OneTimeWorkRequestBuilder<UploadVideoProcessingWorker>()
            .setInputData(UploadVideoProcessingWorker.inputData(queued.jobId))
            .addTag(UploadVideoProcessingWorker.TAG)
            .build()
        deps.workManager.enqueueUniqueWork(
            UploadVideoProcessingWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            requestBuilder,
        )
        return ActiveUploadStartResult.Started(queued.jobId)
    }

    private fun startInMemoryWork(request: ActiveUploadRequest): ActiveUploadStartResult {
        val inMemoryRunner = runner ?: return ActiveUploadStartResult.Blocked("Upload service unavailable")
        if (hasActiveUpload()) {
            val message = "Another upload is already in progress."
            _state.update { it.copy(blockedMessage = message) }
            return ActiveUploadStartResult.Blocked(message)
        }
        val ownerToken = UUID.randomUUID().toString()
        val initial = ActiveUploadSessionState(
            ownerToken = ownerToken,
            selectedVideoUri = request.sourceUri,
            trackingMode = request.trackingMode,
            selectedDrillId = request.selectedDrillId,
            selectedReferenceTemplateId = request.selectedReferenceTemplateId,
            isReferenceUpload = request.isReferenceUpload,
            createDrillFromReferenceUpload = request.createDrillFromReferenceUpload,
            pendingDrillName = request.pendingDrillName,
            startedAtMs = System.currentTimeMillis(),
        )
        _state.value = ActiveUploadCoordinatorState(activeSession = initial, blockedMessage = null)
        activeJob = scope.launch {
            try {
                inMemoryRunner.run(
                    uri = request.sourceUri,
                    ownerToken = ownerToken,
                    trackingMode = request.trackingMode,
                    selectedDrillId = request.selectedDrillId,
                    selectedReferenceTemplateId = request.selectedReferenceTemplateId,
                    isReferenceUpload = request.isReferenceUpload,
                    createDrillFromReferenceUpload = request.createDrillFromReferenceUpload,
                    pendingDrillName = request.pendingDrillName,
                    onSessionCreated = { sessionId ->
                        update(ownerToken) { it.copy(sessionId = sessionId) }
                    },
                    onProgress = { progress ->
                        update(ownerToken) {
                            it.copy(
                                stage = progress.stage,
                                stageText = progress.detail ?: progress.stage.label,
                                progressPercent = progress.percent.coerceIn(0f, 1f),
                                etaMs = progress.etaMs,
                                analysisPhaseLabel = progress.phaseLabel ?: it.analysisPhaseLabel,
                                analysisProcessedFrames = progress.processedFrames ?: it.analysisProcessedFrames,
                                analysisTotalFrames = progress.totalFrames ?: it.analysisTotalFrames,
                                analysisPhasePercent = progress.phasePercent ?: it.analysisPhasePercent,
                            )
                        }
                    },
                    onLog = { line ->
                        update(ownerToken) {
                            it.copy(technicalLog = if (it.technicalLog.isBlank()) line else "${it.technicalLog}\n$line")
                        }
                    },
                ).also { result ->
                    update(ownerToken) {
                        it.copy(
                            stage = result.finalStage,
                            stageText = if (result.annotatedReady) "Annotated replay ready" else "Raw replay ready, annotated replay unavailable",
                            progressPercent = 1f,
                            sessionId = result.sessionId,
                            replayUri = result.replayUri,
                            selectedDrillId = result.drillId ?: it.selectedDrillId,
                            selectedReferenceTemplateId = result.referenceTemplateId ?: it.selectedReferenceTemplateId,
                            errorMessage = result.exportFailureReason?.let { reason -> "Annotated stage failed: $reason" },
                            analysisPhaseLabel = "",
                            analysisProcessedFrames = 0,
                            analysisTotalFrames = 0,
                            analysisPhasePercent = null,
                            isTerminal = true,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                update(ownerToken) {
                    it.copy(
                        stage = UploadStage.CANCELLED,
                        stageText = "Analysis cancelled",
                        errorMessage = null,
                        analysisPhaseLabel = "",
                        analysisProcessedFrames = 0,
                        analysisTotalFrames = 0,
                        analysisPhasePercent = null,
                        isTerminal = true,
                    )
                }
                throw cancelled
            } catch (error: Throwable) {
                update(ownerToken) {
                    it.copy(
                        stage = UploadStage.FAILED,
                        stageText = "Upload pipeline failed",
                        errorMessage = error.message ?: "Unable to process this video.",
                        analysisPhaseLabel = "",
                        analysisProcessedFrames = 0,
                        analysisTotalFrames = 0,
                        analysisPhasePercent = null,
                        isTerminal = true,
                    )
                }
            }
        }
        activeJob?.invokeOnCompletion { activeJob = null }
        return ActiveUploadStartResult.Started(ownerToken)
    }

    private fun observeDurableJobs() {
        val deps = workDependencies ?: return
        scope.launch {
            deps.queueRepository.observeJobs()
                .combine(deps.sessionRepository.observeSessions()) { jobs, sessions ->
                    val nonTerminal = jobs
                        .filter { it.status !in setOf(UploadJobStatus.COMPLETED, UploadJobStatus.FAILED, UploadJobStatus.CANCELLED) }
                        .maxByOrNull { it.updatedAt }
                    val current = nonTerminal ?: jobs.maxByOrNull { it.updatedAt }
                    current?.toActiveState(sessions.firstOrNull { it.id == current.sessionId })
                }
                .collectLatest { snapshot ->
                    _state.update { it.copy(activeSession = snapshot) }
                }
        }
    }

    fun clearBlockedMessage() {
        _state.update { it.copy(blockedMessage = null) }
    }

    fun cancelActiveUpload() {
        val deps = workDependencies
        if (deps != null) {
            deps.workManager.cancelUniqueWork(UploadVideoProcessingWorker.UNIQUE_WORK_NAME)
            val active = _state.value.activeSession ?: return
            scope.launch(Dispatchers.IO) {
                deps.queueRepository.cancel(active.ownerToken)
            }
            return
        }
        activeJob?.cancel()
    }

    fun hasActiveUpload(): Boolean {
        val deps = workDependencies
        if (deps != null) {
            val info = runBlocking(Dispatchers.IO) {
                deps.workManager.getWorkInfosForUniqueWork(UploadVideoProcessingWorker.UNIQUE_WORK_NAME).get()
            }
            return info.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }
        if (activeJob?.isActive != true) return false
        val active = _state.value.activeSession ?: return false
        if (active.isTerminal) return false
        if (active.ownerToken.isBlank()) return false
        return true
    }

    fun clearTerminalSession() {
        _state.update { current ->
            val active = current.activeSession
            if (active != null && active.isTerminal) {
                current.copy(activeSession = null, blockedMessage = null)
            } else {
                current
            }
        }
    }

    private fun update(ownerToken: String, mutate: (ActiveUploadSessionState) -> ActiveUploadSessionState) {
        _state.update { current ->
            val active = current.activeSession
            if (active == null || active.ownerToken != ownerToken) return@update current
            current.copy(activeSession = mutate(active))
        }
    }
}

private fun UploadProcessingJob.toActiveState(session: SessionRecord?): ActiveUploadSessionState {
    val stage = when (status) {
        UploadJobStatus.COMPLETED -> UploadStage.COMPLETED_ANNOTATED
        UploadJobStatus.FAILED, UploadJobStatus.STALLED -> UploadStage.FAILED
        UploadJobStatus.CANCELLED -> UploadStage.CANCELLED
        else -> when (currentStage) {
            UploadJobStage.IMPORTING_RAW_VIDEO -> UploadStage.IMPORTING_RAW_VIDEO
            UploadJobStage.VALIDATING_INPUT -> UploadStage.NORMALIZING_INPUT
            UploadJobStage.ANALYZING_VIDEO -> UploadStage.ANALYZING_VIDEO
            UploadJobStage.RENDERING_ANNOTATED_VIDEO -> UploadStage.RENDERING_OVERLAY
            UploadJobStage.FINALIZING -> UploadStage.VERIFYING_OUTPUT
            UploadJobStage.COMPLETED -> UploadStage.COMPLETED_ANNOTATED
            UploadJobStage.FAILED -> UploadStage.FAILED
            UploadJobStage.CANCELLED -> UploadStage.CANCELLED
        }
    }
    val total = totalFrames.coerceAtLeast(1)
    val pct = (processedFrames.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    return ActiveUploadSessionState(
        ownerToken = jobId,
        selectedVideoUri = Uri.parse(sourceUri),
        trackingMode = runCatching { UploadTrackingMode.valueOf(trackingMode) }.getOrDefault(UploadTrackingMode.HOLD_BASED),
        selectedDrillId = selectedDrillId,
        selectedReferenceTemplateId = selectedReferenceTemplateId,
        isReferenceUpload = isReferenceUpload,
        createDrillFromReferenceUpload = createDrillFromReferenceUpload,
        pendingDrillName = pendingDrillName,
        startedAtMs = startedAt ?: createdAt,
        stage = stage,
        stageText = failureReason ?: stage.label,
        progressPercent = if (status in setOf(UploadJobStatus.COMPLETED, UploadJobStatus.FAILED, UploadJobStatus.CANCELLED)) 1f else pct,
        analysisProcessedFrames = processedFrames,
        analysisTotalFrames = totalFrames,
        sessionId = sessionId,
        replayUri = session?.bestPlayableUri,
        errorMessage = failureReason,
        isTerminal = status in setOf(UploadJobStatus.COMPLETED, UploadJobStatus.FAILED, UploadJobStatus.CANCELLED, UploadJobStatus.STALLED),
    )
}
