package com.inversioncoach.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.biomechanics.AlignmentMetricsEngine
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.coaching.CueEngine
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.CoachingCue
import com.inversioncoach.app.model.DrillScore
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.model.sessionMode
import com.inversioncoach.app.model.LiveSessionUiState
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.motion.DrillCatalog
import com.inversioncoach.app.motion.MotionAnalysisPipeline
import com.inversioncoach.app.motion.QualityThresholds
import com.inversioncoach.app.motion.UserCalibrationSettings
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.pose.PoseSmoother
import com.inversioncoach.app.recording.AnnotatedExportPipeline
import com.inversioncoach.app.recording.OverlayStabilizer
import com.inversioncoach.app.storage.repository.SessionRepository
import com.inversioncoach.app.summary.SummaryGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class SessionVideoOutcome(
    val rawVideoUri: String?,
    val annotatedVideoUri: String?,
    val annotatedExportStatus: AnnotatedExportStatus,
)

data class SessionStopResult(
    val sessionId: Long,
    val wasDiscardedForShortDuration: Boolean,
    val elapsedSessionMs: Long,
    val minSessionDurationSeconds: Int,
)

class LiveCoachingViewModel(
    private val drillType: DrillType,
    private val metricsEngine: AlignmentMetricsEngine,
    private val cueEngine: CueEngine,
    private val repository: SessionRepository,
    private val options: LiveSessionOptions,
    private val summaryGenerator: SummaryGenerator = SummaryGenerator(com.inversioncoach.app.summary.RecommendationEngine()),
    private val smoother: PoseSmoother = PoseSmoother(),
    private val motionPipeline: MotionAnalysisPipeline = MotionAnalysisPipeline(drillType),
    private val overlayStabilizer: OverlayStabilizer = OverlayStabilizer(),
    private val annotatedExportPipeline: AnnotatedExportPipeline,
) : ViewModel() {

    private val sessionMode = drillType.sessionMode()
    private val drillDefinition = DrillCatalog.byType(drillType)

    private val _uiState = MutableStateFlow(
        LiveSessionUiState(
            drillType = drillType,
            sessionMode = sessionMode,
            isRecording = false,
            showOverlay = options.showSkeletonOverlay,
            showIdealLine = options.showIdealLine,
            drillCameraSide = if (sessionMode == SessionMode.FREESTYLE) null else options.drillCameraSide,
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
    private var annotatedExportStatus: AnnotatedExportStatus = AnnotatedExportStatus.NOT_STARTED
    private var lastOverlayCaptureTsMs: Long = 0L
    private var rawVideoPersistJob: Job? = null
    private var annotatedExportJob: Job? = null
    private val overlayFrames = mutableListOf<com.inversioncoach.app.recording.AnnotatedOverlayFrame>()
    private var pendingStopCallback: ((SessionStopResult) -> Unit)? = null
    private var isSessionFinalizing = false
    private var activeSettings: UserSettings = UserSettings()
    private var sessionHadAnyVideo = false
    private val drillConfig = DrillConfigs.byTypeOrNull(drillType)
    private val frameGate = drillConfig?.let { FrameValidityGate(drillType, it) }
    private val issueAggregator = IssueEventAggregator()
    private val invalidReasonCounts = mutableMapOf<String, Int>()
    private var validFrameCount = 0
    private var invalidFrameCount = 0
    private val validFrameScores = mutableListOf<Int>()

    val sessionTitle: String
        get() = if (sessionMode == SessionMode.FREESTYLE) "Freestyle Live Coaching session" else "${drillType.displayName} session"

    val sessionStartTimestampMs: Long
        get() = sessionStartedAtMs

    init {
        val movementPattern = runCatching { drillDefinition.movementPattern.name }.getOrDefault("GENERIC")
        SessionDiagnostics.log("session_started drill=$drillType analyzer=${metricsEngine::class.simpleName} motionPattern=$movementPattern")
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
        sessionHadAnyVideo = true
        SessionDiagnostics.log("recording_finalized sessionId=$activeSessionId uri=$finalizedUri")
        rawVideoPersistJob = viewModelScope.launch {
            rawVideoUri = repository.saveRawVideoBlob(activeSessionId, finalizedUri)
            if (rawVideoUri.isNullOrBlank()) {
                annotatedExportStatus = AnnotatedExportStatus.FAILED
                SessionDiagnostics.log("raw_video_persist_failed sessionId=$activeSessionId")
                onAnalyzerWarning("Raw replay could not be saved")
                repository.updateAnnotatedExportStatus(activeSessionId, AnnotatedExportStatus.FAILED)
                return@launch
            }
            SessionDiagnostics.log("raw_video_persisted sessionId=$activeSessionId rawUri=$rawVideoUri")
            val exportFrames = exportFramesForSession()
            SessionDiagnostics.log(
                "overlay_frames_collected sessionId=$activeSessionId count=${exportFrames.size} " +
                    "firstTs=${exportFrames.firstOrNull()?.timestampMs ?: -1L} lastTs=${exportFrames.lastOrNull()?.timestampMs ?: -1L}",
            )
            annotatedExportJob = viewModelScope.launch {
                annotatedExportStatus = AnnotatedExportStatus.PROCESSING
                SessionDiagnostics.log("annotated_export_started sessionId=$activeSessionId")
                annotatedVideoUri = annotatedExportPipeline.export(
                    sessionId = activeSessionId,
                    rawVideoUri = rawVideoUri!!,
                    drillType = drillType,
                    drillCameraSide = options.drillCameraSide,
                    overlayFrames = exportFrames,
                )
                annotatedExportStatus = if (annotatedVideoUri.isNullOrBlank()) AnnotatedExportStatus.FAILED else AnnotatedExportStatus.READY
                if (annotatedVideoUri.isNullOrBlank()) {
                    SessionDiagnostics.log("annotated_export_failed sessionId=$activeSessionId reason=export_returned_empty")
                    onAnalyzerWarning("Annotated replay unavailable, showing raw replay")
                } else {
                    SessionDiagnostics.log("annotated_export_finished sessionId=$activeSessionId annotatedUri=$annotatedVideoUri")
                }
            }
        }
    }


    fun onPoseFrame(frame: PoseFrame, settings: UserSettings) {
        activeSettings = settings
        val frameForSession = if (sessionMode == SessionMode.FREESTYLE) frame else frame.filterForDrillSide(options.drillCameraSide)
        val smoothed = smoother.smooth(frameForSession)
        val calibration = if (settings.alignmentStrictness.name == "CUSTOM") {
            UserCalibrationSettings(
                strictness = settings.alignmentStrictness,
                customThresholds = QualityThresholds(
                    acceptableLineDeviation = settings.customLineDeviation,
                    minimumGoodFormScore = settings.customMinimumGoodFormScore,
                    repAcceptanceThreshold = settings.customRepAcceptanceThreshold,
                    holdAlignedThreshold = settings.customHoldAlignedThreshold,
                    alignmentPersistenceMs = 300L,
                    allowedRepAlignmentDropMs = 300L,
                ),
            )
        } else {
            UserCalibrationSettings(settings.alignmentStrictness)
        }
        val motion = if (sessionMode == SessionMode.FREESTYLE) null else motionPipeline.analyze(frameForSession, settings.alignmentStrictness, calibration)
        _smoothedFrame.value = smoothed
        if (shouldCaptureOverlayFrame(smoothed.timestampMs)) {
            overlayFrames += overlayStabilizer.stabilize(smoothed, sessionMode)
            lastOverlayCaptureTsMs = smoothed.timestampMs
        }
        val gate = frameGate
        if (gate == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Unsupported drill: $drillType")
            return
        }
        val frameValidity = gate.evaluate(frameForSession)
        val rejectionReason = if (frame.rejectionReason != "none") frame.rejectionReason else if (frameValidity.isValid) "none" else frameValidity.reason
        val rejectionMessage = rejectionMessageFor(rejectionReason)

        _uiState.value = _uiState.value.copy(
            confidence = smoothed.confidence,
            warningMessage = rejectionMessage,
            showDebugOverlay = settings.debugOverlayEnabled,
            debugLandmarksDetected = frameForSession.landmarksDetected,
            debugInferenceTimeMs = frameForSession.inferenceTimeMs,
            debugFrameDrops = frameForSession.droppedFrames,
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

        if (sessionMode == SessionMode.FREESTYLE) {
            _uiState.value = _uiState.value.copy(
                score = 0,
                alignmentScore = 0,
                smoothedAlignmentScore = 0,
                stabilityScore = 0,
                confidence = smoothed.confidence,
                currentCue = "",
                currentCueId = "",
                currentCueGeneratedAtMs = 0L,
                warningMessage = null,
                errorMessage = null,
                showDebugOverlay = settings.debugOverlayEnabled,
                debugMetrics = emptyList(),
                debugAngles = emptyList(),
                repCount = 0,
                rawRepCount = 0,
                totalAlignedDurationMs = 0L,
                currentAlignedStreakMs = 0L,
                bestAlignedStreakMs = 0L,
                totalSessionTrackedMs = 0L,
                currentPhase = "tracking",
                activeFault = "",
                alignmentRate = 0f,
                averageAlignmentScore = 0,
                lastRepScore = 0,
                acceptedReps = 0,
                rejectedReps = 0,
                averageRepQuality = 0,
                bestRepScore = 0,
                mostCommonFailureReason = "",
                averageStabilityScore = 0,
                peakDrift = 0f,
            )
            persistFrameData(
                smoothed = smoothed,
                overallScore = 0,
                limitingFactor = "not_tracked",
                metrics = emptyList(),
                angles = emptyList(),
                fault = null,
                cueSeverity = 0,
            )
            return
        }

        val config = drillConfig ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Unsupported drill: $drillType")
            return
        }
        val analysis = metricsEngine.analyze(config, smoothed.toPoseFrame())
        latestScore = analysis.score
        val cue = cueEngine.nextCue(
            config = config,
            metrics = analysis.metrics,
            style = settings.cueStyle,
            minSpacingMs = (settings.cueFrequencySeconds * 1000).toLong(),
        )

        val repTracking = motion?.repTracking
        val holdTracking = motion?.holdTracking
        _uiState.value = _uiState.value.copy(
            score = analysis.score.overall,
            alignmentScore = motion?.alignment?.rawScore ?: 0,
            smoothedAlignmentScore = motion?.alignment?.smoothedScore ?: 0,
            stabilityScore = motion?.stability?.stabilityScore ?: 0,
            confidence = smoothed.confidence,
            currentCue = cue?.text ?: motion?.cue?.text ?: _uiState.value.currentCue.ifBlank { "Human detected. Hold steady for drill scoring." },
            currentCueId = cue?.id ?: _uiState.value.currentCueId,
            currentCueGeneratedAtMs = cue?.generatedAtMs ?: _uiState.value.currentCueGeneratedAtMs,
            warningMessage = null,
            errorMessage = null,
            showDebugOverlay = settings.debugOverlayEnabled,
            debugMetrics = analysis.metrics,
            debugAngles = analysis.angles,
            repCount = repTracking?.validRepCount ?: 0,
            rawRepCount = repTracking?.rawRepAttempts ?: 0,
            totalAlignedDurationMs = motion?.holdQuality?.alignedHoldDurationMs ?: holdTracking?.totalAlignedDurationMs ?: _uiState.value.totalAlignedDurationMs,
            currentAlignedStreakMs = holdTracking?.currentAlignedStreakMs ?: _uiState.value.currentAlignedStreakMs,
            bestAlignedStreakMs = motion?.holdQuality?.bestAlignedStreakMs ?: holdTracking?.bestAlignedStreakMs ?: _uiState.value.bestAlignedStreakMs,
            totalSessionTrackedMs = motion?.holdQuality?.totalHoldDurationMs ?: holdTracking?.totalSessionDurationMs ?: _uiState.value.totalSessionTrackedMs,
            currentPhase = motion?.movement?.currentPhase?.name?.lowercase() ?: "setup",
            activeFault = motion?.alignment?.dominantFault ?: motion?.faults?.firstOrNull()?.code.orEmpty(),
            alignmentRate = motion?.holdQuality?.alignmentRate ?: _uiState.value.alignmentRate,
            averageAlignmentScore = motion?.holdQuality?.averageAlignmentScore ?: _uiState.value.averageAlignmentScore,
            lastRepScore = motion?.repQuality?.latestRep?.repScore ?: _uiState.value.lastRepScore,
            acceptedReps = motion?.repQuality?.acceptedReps ?: _uiState.value.acceptedReps,
            rejectedReps = motion?.repQuality?.rejectedReps ?: _uiState.value.rejectedReps,
            averageRepQuality = motion?.repQuality?.averageRepQuality ?: _uiState.value.averageRepQuality,
            bestRepScore = motion?.repQuality?.bestRepScore ?: _uiState.value.bestRepScore,
            mostCommonFailureReason = motion?.repQuality?.mostCommonFailureReason ?: _uiState.value.mostCommonFailureReason,
            averageStabilityScore = (((_uiState.value.averageStabilityScore + (motion?.stability?.stabilityScore ?: 0)) / 2f).toInt()),
            peakDrift = maxOf(_uiState.value.peakDrift, motion?.stability?.centerlineDeviation ?: 0f),
        )
        val motionFaults = motion?.faults.orEmpty()
        if (motionFaults.isNotEmpty()) {
            SessionDiagnostics.log("raw_faults drill=$drillType faults=${motionFaults.map { it.code }}")
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

    fun stopSession(onSessionFinalized: (SessionStopResult) -> Unit) {
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
                listOfNotNull(rawVideoPersistJob, annotatedExportJob).joinAll()
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
                val wins = if (sessionMode == SessionMode.FREESTYLE) "Not tracked" else sessionComputation.summaryWins
                val summary = if (sessionMode == SessionMode.FREESTYLE) null else summaryGenerator.generate(
                    drillType = drillType,
                    score = sessionComputation.score,
                    issues = aggregatedIssues.map { it.issue },
                    wins = listOf(wins),
                )
                val invalidReasonSummary = invalidReasonCounts.entries.sortedByDescending { it.value }.joinToString(";") { "${it.key}:${it.value}" }
                val completedAtMs = System.currentTimeMillis()
                val elapsedSessionSeconds = ((completedAtMs - sessionStartedAtMs).coerceAtLeast(0L)) / 1000.0
                val hasPersistedVideo = !rawVideoUri.isNullOrBlank() || !annotatedVideoUri.isNullOrBlank()
                val shouldDeleteSession = !hasPersistedVideo && !sessionHadAnyVideo &&
                    elapsedSessionSeconds < activeSettings.minSessionDurationSeconds
                SessionDiagnostics.log(
                    "session_finalize drill=$drillType validFrames=$validFrameCount invalidFrames=$invalidFrameCount invalidReasons={$invalidReasonSummary} " +
                        "aggregatedIssues=${aggregatedIssues.size} savedRaw=$rawVideoUri exportedAnnotated=$annotatedVideoUri elapsed=${"%.2f".format(elapsedSessionSeconds)}s " +
                        "minKeepWithoutVideo=${activeSettings.minSessionDurationSeconds}s shouldDelete=$shouldDeleteSession",
                )
                if (shouldDeleteSession) {
                    repository.deleteSession(activeSessionId)
                    onSessionFinalized(
                        SessionStopResult(
                            sessionId = activeSessionId,
                            wasDiscardedForShortDuration = true,
                            elapsedSessionMs = (completedAtMs - sessionStartedAtMs).coerceAtLeast(0L),
                            minSessionDurationSeconds = activeSettings.minSessionDurationSeconds,
                        ),
                    )
                    return@launch
                }
                val finalVideos = resolveSessionVideoOutcome(
                    rawVideoUri = rawVideoUri,
                    annotatedVideoUri = annotatedVideoUri,
                    exportStatus = annotatedExportStatus,
                )
                repository.saveSession(
                    SessionRecord(
                        id = activeSessionId,
                        title = sessionTitle,
                        drillType = drillType,
                        startedAtMs = sessionStartedAtMs,
                        completedAtMs = completedAtMs,
                        overallScore = if (sessionMode == SessionMode.FREESTYLE) 0 else sessionComputation.score.overall,
                        strongestArea = if (sessionMode == SessionMode.FREESTYLE) "Not tracked" else sessionComputation.strongestArea,
                        limitingFactor = if (sessionMode == SessionMode.FREESTYLE) "not_tracked" else sessionComputation.score.limitingFactor,
                        issues = if (sessionMode == SessionMode.FREESTYLE) "" else topIssues,
                        wins = if (sessionMode == SessionMode.FREESTYLE) "Not tracked" else summary?.whatWentWell?.joinToString(" ").orEmpty(),
                        metricsJson = buildString {
                            if (sessionMode != SessionMode.FREESTYLE) {
                                append(sessionComputation.score.subScores.entries.joinToString(",") { "${it.key}:${it.value}" })
                            }
                            append("|status:")
                            append(sessionComputation.status)
                            append("|validFrames:")
                            append(validFrameCount)
                            append("|invalidFrames:")
                            append(invalidFrameCount)
                            append("|trackingMode:")
                            append(drillDefinition.repMode.name)
                            append("|validReps:")
                            append(_uiState.value.repCount)
                            append("|rawRepAttempts:")
                            append(_uiState.value.rawRepCount)
                            append("|alignedDurationMs:")
                            append(_uiState.value.totalAlignedDurationMs)
                            append("|bestAlignedStreakMs:")
                            append(_uiState.value.bestAlignedStreakMs)
                            append("|sessionTrackedMs:")
                            append(_uiState.value.totalSessionTrackedMs)
                            append("|alignmentRaw:")
                            append(_uiState.value.alignmentScore)
                            append("|alignmentSmoothed:")
                            append(_uiState.value.smoothedAlignmentScore)
                            append("|alignmentRate:")
                            append("%.3f".format(_uiState.value.alignmentRate))
                            append("|avgAlignment:")
                            append(_uiState.value.averageAlignmentScore)
                            append("|avgStability:")
                            append(_uiState.value.averageStabilityScore)
                            append("|peakDrift:")
                            append("%.4f".format(_uiState.value.peakDrift))
                            append("|acceptedReps:")
                            append(_uiState.value.acceptedReps)
                            append("|rejectedReps:")
                            append(_uiState.value.rejectedReps)
                            append("|avgRepScore:")
                            append(_uiState.value.averageRepQuality)
                            append("|bestRepScore:")
                            append(_uiState.value.bestRepScore)
                            append("|repFailureReason:")
                            append(_uiState.value.mostCommonFailureReason)
                            if (invalidReasonSummary.isNotBlank()) {
                                append("|invalidReasons:")
                                append(invalidReasonSummary)
                            }
                        },
                        annotatedVideoUri = finalVideos.annotatedVideoUri,
                        rawVideoUri = finalVideos.rawVideoUri,
                        annotatedExportStatus = finalVideos.annotatedExportStatus,
                        notesUri = null,
                        bestFrameTimestampMs = bestFrame,
                        worstFrameTimestampMs = worstFrame,
                        topImprovementFocus = when {
                            sessionMode == SessionMode.FREESTYLE -> "Not tracked"
                            sessionComputation.status == "invalid" -> sessionComputation.topImprovementFocus
                            else -> summary?.nextFocus.orEmpty()
                        },
                    ),
                )
                onSessionFinalized(
                    SessionStopResult(
                        sessionId = activeSessionId,
                        wasDiscardedForShortDuration = false,
                        elapsedSessionMs = (completedAtMs - sessionStartedAtMs).coerceAtLeast(0L),
                        minSessionDurationSeconds = activeSettings.minSessionDurationSeconds,
                    ),
                )
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
                    annotatedExportStatus = AnnotatedExportStatus.NOT_STARTED,
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


    private fun PoseFrame.filterForDrillSide(side: DrillCameraSide): PoseFrame {
        val prefix = if (side == DrillCameraSide.LEFT) "left_" else "right_"
        val filtered = joints.filter { it.name == "nose" || !it.name.contains("_") || it.name.startsWith(prefix) }
        return copy(joints = filtered, landmarksDetected = filtered.size)
    }

    private fun SmoothedPoseFrame.toPoseFrame(): PoseFrame = PoseFrame(timestampMs, joints, confidence)

    private fun shouldCaptureOverlayFrame(timestampMs: Long): Boolean {
        return overlayFrames.isEmpty() || (timestampMs - lastOverlayCaptureTsMs) >= MAX_EXPORT_FRAME_INTERVAL_MS
    }

    private fun exportFramesForSession(): List<com.inversioncoach.app.recording.AnnotatedOverlayFrame> {
        if (overlayFrames.isNotEmpty()) return overlayFrames.toList()
        val fallbackFrame = _smoothedFrame.value?.let { overlayStabilizer.stabilize(it, sessionMode) }
        return listOfNotNull(fallbackFrame)
    }

    override fun onCleared() {
        super.onCleared()
        finalizeSessionSilentlyIfActive()
    }

    companion object {
        private const val FRAME_PERSIST_INTERVAL_MS = 250L
        private const val MAX_EXPORT_FRAME_INTERVAL_MS = 50L
    }
}

internal fun resolveSessionVideoOutcome(
    rawVideoUri: String?,
    annotatedVideoUri: String?,
    exportStatus: AnnotatedExportStatus,
): SessionVideoOutcome {
    val annotated = annotatedVideoUri?.takeIf { it.isNotBlank() }
    return if (annotated != null) {
        SessionVideoOutcome(
            rawVideoUri = rawVideoUri,
            annotatedVideoUri = annotated,
            annotatedExportStatus = AnnotatedExportStatus.READY,
        )
    } else {
        SessionVideoOutcome(
            rawVideoUri = rawVideoUri,
            annotatedVideoUri = null,
            annotatedExportStatus = if (rawVideoUri.isNullOrBlank()) exportStatus else AnnotatedExportStatus.FAILED,
        )
    }
}
