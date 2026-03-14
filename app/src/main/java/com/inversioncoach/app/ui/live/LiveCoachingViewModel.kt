package com.inversioncoach.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.biomechanics.AlignmentMetricsEngine
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.coaching.CueEngine
import com.inversioncoach.app.model.CoachingCue
import com.inversioncoach.app.model.DrillScore
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.model.LiveSessionUiState
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.motion.DrillCatalog
import com.inversioncoach.app.motion.MotionAnalysisPipeline
import com.inversioncoach.app.pose.PoseSmoother
import com.inversioncoach.app.storage.repository.SessionRepository
import com.inversioncoach.app.summary.SummaryGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class LiveCoachingViewModel(
    private val drillType: DrillType,
    private val metricsEngine: AlignmentMetricsEngine,
    private val cueEngine: CueEngine,
    private val repository: SessionRepository,
    private val options: LiveSessionOptions,
    private val summaryGenerator: SummaryGenerator = SummaryGenerator(com.inversioncoach.app.summary.RecommendationEngine()),
    private val smoother: PoseSmoother = PoseSmoother(),
    private val motionPipeline: MotionAnalysisPipeline = MotionAnalysisPipeline(drillType),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LiveSessionUiState(
            drillType = drillType,
            isRecording = false,
            showOverlay = options.showSkeletonOverlay,
            showIdealLine = options.showIdealLine,
        ),
    )
    val uiState: StateFlow<LiveSessionUiState> = _uiState.asStateFlow()

    private val _smoothedFrame = MutableStateFlow<SmoothedPoseFrame?>(null)
    val smoothedFrame: StateFlow<SmoothedPoseFrame?> = _smoothedFrame.asStateFlow()

    private val _spokenCue = MutableStateFlow<CoachingCue?>(null)
    val spokenCue: StateFlow<CoachingCue?> = _spokenCue.asStateFlow()

    private var latestScore: DrillScore = DrillScore(0, emptyMap(), "-", "-")
    private var sessionId: Long? = null
    private var sessionStartedAtMs: Long = 0L
    private var lastFramePersistAt = 0L
    private var rawVideoUri: String? = null
    private var annotatedVideoUri: String? = null
    private var rawVideoPersistJob: Job? = null
    private var annotatedVideoPersistJob: Job? = null
    private var pendingStopCallback: ((Long) -> Unit)? = null
    private var isSessionFinalizing = false
    private val frameGate = FrameValidityGate(drillType, DrillConfigs.byType(drillType))
    private val issueAggregator = IssueEventAggregator()
    private val invalidReasonCounts = mutableMapOf<String, Int>()
    private var validFrameCount = 0
    private var invalidFrameCount = 0
    private val validFrameScores = mutableListOf<Int>()

    val sessionTitle: String
        get() = "${drillType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} session"

    init {
        SessionDiagnostics.log("session_started drill=$drillType analyzer=${metricsEngine::class.simpleName} motionPattern=${DrillCatalog.byType(drillType).movementPattern}")
        startSession()
    }

    fun onCameraPermissionChanged(granted: Boolean) {
        _uiState.value = _uiState.value.copy(cameraPermissionGranted = granted)
    }

    fun onCameraReady(ready: Boolean, error: String? = null) {
        _uiState.value = _uiState.value.copy(
            cameraReady = ready,
            errorMessage = error,
        )
    }

    fun onAnalyzerWarning(message: String) {
        _uiState.value = _uiState.value.copy(warningMessage = message)
    }

    fun onRecordingFinalized(uri: String?) {
        val finalizedUri = uri?.takeIf { it.isNotBlank() } ?: return
        val activeSessionId = sessionId ?: return
        rawVideoPersistJob = viewModelScope.launch {
            rawVideoUri = repository.saveRawVideoBlob(activeSessionId, finalizedUri)
            if (annotatedVideoUri.isNullOrBlank()) {
                annotatedVideoUri = repository.saveAnnotatedVideoBlob(activeSessionId, finalizedUri)
            }
        }
    }

    fun onAnnotatedRecordingFinalized(uri: String?) {
        val finalizedUri = uri?.takeIf { it.isNotBlank() } ?: return
        val activeSessionId = sessionId ?: return
        annotatedVideoPersistJob = viewModelScope.launch {
            annotatedVideoUri = repository.saveAnnotatedVideoBlob(activeSessionId, finalizedUri)
        }
    }

    fun onPoseFrame(frame: PoseFrame, settings: UserSettings) {
        val smoothed = smoother.smooth(frame)
        val motion = motionPipeline.analyze(frame)
        _smoothedFrame.value = smoothed
        val frameValidity = frameGate.evaluate(frame)
        val rejectionReason = if (frame.rejectionReason != "none") frame.rejectionReason else if (frameValidity.isValid) "none" else frameValidity.reason
        val rejectionMessage = rejectionMessageFor(rejectionReason)

        _uiState.value = _uiState.value.copy(
            confidence = smoothed.confidence,
            warningMessage = rejectionMessage,
            showDebugOverlay = settings.debugOverlayEnabled,
            debugLandmarksDetected = frame.landmarksDetected,
            debugInferenceTimeMs = frame.inferenceTimeMs,
            debugFrameDrops = frame.droppedFrames,
            debugRejectionReason = rejectionReason,
        )

        if (rejectionReason != "none") {
            invalidFrameCount += 1
            invalidReasonCounts[rejectionReason] = (invalidReasonCounts[rejectionReason] ?: 0) + 1
            _uiState.value = _uiState.value.copy(
                debugMetrics = emptyList(),
                debugAngles = emptyList(),
            )
            return
        }
        validFrameCount += 1

        val config = DrillConfigs.byType(drillType)
        val analysis = metricsEngine.analyze(config, smoothed.toPoseFrame())
        latestScore = analysis.score
        val cue = cueEngine.nextCue(
            config = config,
            metrics = analysis.metrics,
            style = settings.cueStyle,
            minSpacingMs = (settings.cueFrequencySeconds * 1000).toLong(),
        )

        _uiState.value = _uiState.value.copy(
            score = analysis.score.overall,
            confidence = smoothed.confidence,
            currentCue = cue?.text ?: motion.cue?.text ?: _uiState.value.currentCue.ifBlank { "Human detected. Hold steady for drill scoring." },
            currentCueId = cue?.id ?: _uiState.value.currentCueId,
            currentCueGeneratedAtMs = cue?.generatedAtMs ?: _uiState.value.currentCueGeneratedAtMs,
            warningMessage = null,
            errorMessage = null,
            showDebugOverlay = settings.debugOverlayEnabled,
            debugMetrics = analysis.metrics,
            debugAngles = analysis.angles,
            repCount = motion.movement.completedRepCount,
            currentPhase = motion.movement.currentPhase.name.lowercase(),
            activeFault = motion.faults.firstOrNull()?.code ?: "",
        )
        if (motion.faults.isNotEmpty()) {
            SessionDiagnostics.log("raw_faults drill=$drillType faults=${motion.faults.map { it.code }}")
        }

        if (cue != null) {
            _spokenCue.value = cue
        }

        validFrameScores += analysis.score.overall
        if (!analysis.fault.isNullOrBlank()) {
            issueAggregator.onIssue(
                ts = smoothed.timestampMs,
                issue = analysis.fault,
                severity = cue?.severity ?: 1,
                cue = cue?.text,
            )
        }

        persistFrameData(smoothed, analysis.score.overall, analysis.score.limitingFactor, analysis.metrics, analysis.angles, analysis.fault, cue?.severity ?: 1)
    }

    private fun rejectionMessageFor(reason: String): String? = when (reason) {
        "no_person_detected" -> "No person detected. Step back until your full body is visible in side view."
        "body_not_fully_visible" -> "Body not fully visible. Keep head, hips, and feet inside frame."
        "low_confidence" -> "Low confidence. Improve lighting and hold a stable side view."
        "missing_required_landmarks" -> "Required body landmarks are missing for this drill. Step back and keep full body visible."
        "too_close_to_camera" -> "You're too close to camera. Step back so your full body fits comfortably in frame."
        "too_far_from_camera" -> "You're too far from camera. Move a little closer while keeping full body in frame."
        "wrong_orientation" -> "Wrong orientation for this drill. Use a clear side view."
        "frame_processing_failure" -> "Frame processing failure. Hold steady and retry."
        else -> null
    }

    private fun persistFrameData(
        smoothed: SmoothedPoseFrame,
        overallScore: Int,
        limitingFactor: String,
        metrics: List<com.inversioncoach.app.model.AlignmentMetric>,
        angles: List<com.inversioncoach.app.model.AngleDebugMetric>,
        fault: String?,
        cueSeverity: Int,
    ) {
        val now = smoothed.timestampMs
        if (now - lastFramePersistAt < FRAME_PERSIST_INTERVAL_MS) return
        lastFramePersistAt = now

        viewModelScope.launch {
            val activeSessionId = sessionId ?: return@launch
            repository.saveFrameMetric(
                FrameMetricRecord(
                    sessionId = activeSessionId,
                    timestampMs = smoothed.timestampMs,
                    confidence = smoothed.confidence,
                    overallScore = overallScore,
                    limitingFactor = limitingFactor,
                    metricScoresJson = metrics.joinToString(";") { "${it.key}:${it.score}" },
                    anglesJson = angles.joinToString(";") { "${it.key}:${"%.1f".format(it.degrees)}" },
                    activeIssue = fault,
                ),
            )
            if (fault != null) {
                repository.saveIssueEvent(
                    IssueEvent(
                        sessionId = activeSessionId,
                        timestampMs = smoothed.timestampMs,
                        issue = fault,
                        severity = cueSeverity,
                    ),
                )
            }
        }
    }

    fun setRecording(isRecording: Boolean) {
        _uiState.value = _uiState.value.copy(isRecording = isRecording)
    }

    fun stopSession(onSessionFinalized: (Long) -> Unit) {
        viewModelScope.launch {
            if (isSessionFinalizing) return@launch
            val activeSessionId = sessionId
            if (activeSessionId == null) {
                pendingStopCallback = onSessionFinalized
                _uiState.value = _uiState.value.copy(
                    warningMessage = "Preparing session. Stopping as soon as initialization completes.",
                )
                return@launch
            }
            isSessionFinalizing = true
            try {
                listOfNotNull(rawVideoPersistJob, annotatedVideoPersistJob).joinAll()
                val frameMetrics = repository.observeSessionFrameMetrics(activeSessionId).first()
                val aggregatedIssues = issueAggregator.flushAll(System.currentTimeMillis())
                val validFrameMetrics = frameMetrics.filter { it.confidence >= 0.45f }
                val bestFrame = validFrameMetrics.maxByOrNull { it.overallScore }?.timestampMs
                val worstFrame = validFrameMetrics.minByOrNull { it.overallScore }?.timestampMs
                val sessionComputation = SessionSummaryComputer.compute(validFrameScores, latestScore, aggregatedIssues)
                val topIssues = aggregatedIssues
                    .sortedByDescending { it.durationMs }
                    .take(3)
                    .joinToString(", ") { "${it.issue} (${it.durationMs / 1000.0}s)" }
                    .ifBlank { if (sessionComputation.status == "invalid") "insufficient_data" else "No major issues detected" }
                val wins = sessionComputation.summaryWins
                val summary = summaryGenerator.generate(
                    drillType = drillType,
                    score = sessionComputation.score,
                    issues = aggregatedIssues.map { it.issue },
                    wins = listOf(wins),
                )
                val invalidReasonSummary = invalidReasonCounts.entries.sortedByDescending { it.value }.joinToString(";") { "${it.key}:${it.value}" }
                SessionDiagnostics.log(
                    "session_finalize drill=$drillType validFrames=$validFrameCount invalidFrames=$invalidFrameCount invalidReasons={$invalidReasonSummary} " +
                        "aggregatedIssues=${aggregatedIssues.size} savedRaw=$rawVideoUri savedAnnotated=$annotatedVideoUri",
                )
                repository.saveSession(
                    SessionRecord(
                        id = activeSessionId,
                        title = sessionTitle,
                        drillType = drillType,
                        startedAtMs = sessionStartedAtMs,
                        completedAtMs = System.currentTimeMillis(),
                        overallScore = sessionComputation.score.overall,
                        strongestArea = sessionComputation.strongestArea,
                        limitingFactor = sessionComputation.score.limitingFactor,
                        issues = topIssues,
                        wins = summary.whatWentWell.joinToString(" "),
                        metricsJson = buildString {
                            append(sessionComputation.score.subScores.entries.joinToString(",") { "${it.key}:${it.value}" })
                            append("|status:")
                            append(sessionComputation.status)
                            append("|validFrames:")
                            append(validFrameCount)
                            append("|invalidFrames:")
                            append(invalidFrameCount)
                            if (invalidReasonSummary.isNotBlank()) {
                                append("|invalidReasons:")
                                append(invalidReasonSummary)
                            }
                        },
                        annotatedVideoUri = annotatedVideoUri,
                        rawVideoUri = rawVideoUri,
                        notesUri = null,
                        bestFrameTimestampMs = bestFrame,
                        worstFrameTimestampMs = worstFrame,
                        topImprovementFocus = if (sessionComputation.status == "invalid") sessionComputation.topImprovementFocus else summary.nextFocus,
                    ),
                )
                onSessionFinalized(activeSessionId)
            } finally {
                isSessionFinalizing = false
            }
        }
    }

    fun finalizeSessionSilentlyIfActive() {
        stopSession { }
    }

    fun finalScore(): DrillScore = latestScore

    private fun startSession() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            sessionStartedAtMs = now
            val newSessionId = repository.saveSession(
                SessionRecord(
                    title = sessionTitle,
                    drillType = drillType,
                    startedAtMs = now,
                    completedAtMs = 0L,
                    overallScore = 0,
                    strongestArea = "pending",
                    limitingFactor = "pending",
                    issues = "",
                    wins = "",
                    metricsJson = "",
                    annotatedVideoUri = null,
                    rawVideoUri = null,
                    notesUri = null,
                    bestFrameTimestampMs = null,
                    worstFrameTimestampMs = null,
                    topImprovementFocus = "pending",
                ),
            )
            sessionId = newSessionId
            pendingStopCallback?.let { callback ->
                pendingStopCallback = null
                stopSession(callback)
            }
        }
    }

    private fun SmoothedPoseFrame.toPoseFrame(): PoseFrame = PoseFrame(timestampMs, joints, confidence)

    companion object {
        private const val FRAME_PERSIST_INTERVAL_MS = 250L
    }

    override fun onCleared() {
        super.onCleared()
        finalizeSessionSilentlyIfActive()
    }
}
