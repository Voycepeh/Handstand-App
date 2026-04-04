package com.inversioncoach.app.ui.upload

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

class ActiveUploadCoordinator(
    private val scope: CoroutineScope,
    private val runner: UploadVideoAnalysisRunner,
) {
    private val _state = MutableStateFlow(ActiveUploadCoordinatorState())
    val state: StateFlow<ActiveUploadCoordinatorState> = _state.asStateFlow()
    private var activeJob: Job? = null

    fun start(request: ActiveUploadRequest): ActiveUploadStartResult {
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
                runner.run(
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

    fun clearBlockedMessage() {
        _state.update { it.copy(blockedMessage = null) }
    }

    fun cancelActiveUpload() {
        activeJob?.cancel()
    }

    fun hasActiveUpload(): Boolean {
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
