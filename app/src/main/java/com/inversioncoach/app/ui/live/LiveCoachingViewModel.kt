package com.inversioncoach.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inversioncoach.app.biomechanics.AlignmentMetricsEngine
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.coaching.CueEngine
import com.inversioncoach.app.model.AnnotatedExportStage
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.CleanupStatus
import com.inversioncoach.app.model.CompressionStatus
import com.inversioncoach.app.model.AnnotatedExportFailureReason
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
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.RetainedAssetType
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.motion.DrillCatalog
import com.inversioncoach.app.motion.MotionAnalysisPipeline
import com.inversioncoach.app.motion.QualityThresholds
import com.inversioncoach.app.motion.UserCalibrationSettings
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.FreestyleOrientationClassifier
import com.inversioncoach.app.overlay.FreestyleViewMode
import com.inversioncoach.app.pose.PoseSmoother
import com.inversioncoach.app.recording.AnnotatedExportPipeline
import com.inversioncoach.app.recording.MediaVerificationHelper
import com.inversioncoach.app.recording.OverlayStabilizer
import com.inversioncoach.app.recording.VideoCompressionPipeline
import com.inversioncoach.app.storage.repository.SessionRepository
import com.inversioncoach.app.summary.SummaryGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class SessionVideoOutcome(
    val rawVideoUri: String?,
    val annotatedVideoUri: String?,
    val bestPlayableUri: String?,
    val retainedAssetType: RetainedAssetType,
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
    private val compressionPipeline: VideoCompressionPipeline = VideoCompressionPipeline(),
) : ViewModel() {

    enum class ExportLifecycleState {
        IDLE,
        PROCESSING,
        READY,
        FAILED,
    }

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
    private var rawMasterUri: String? = null
    private var annotatedMasterUri: String? = null
    private var rawFinalUri: String? = null
    private var annotatedFinalUri: String? = null
    private var bestPlayableUri: String? = null
    private var rawPersistStatus: RawPersistStatus = RawPersistStatus.NOT_STARTED
    private var rawPersistFailureReason: String? = null
    private var annotatedVideoUri: String? = null
    private var annotatedExportStatus: AnnotatedExportStatus = AnnotatedExportStatus.NOT_STARTED
    private var exportLifecycleState: ExportLifecycleState = ExportLifecycleState.IDLE
    private var annotatedExportFailureReason: String? = null
    private var annotatedExportFailureDetail: String? = null
    private var annotatedExportElapsedMs: Long? = null
    private var annotatedExportStageAtFailure: String? = null
    private var annotatedExportStage: AnnotatedExportStage = AnnotatedExportStage.QUEUED
    private var annotatedExportPercent: Int = 0
    private var annotatedExportEtaSeconds: Int? = null
    private var annotatedExportLastUpdatedAt: Long? = null
    private var rawCompressionStatus: CompressionStatus = CompressionStatus.NOT_STARTED
    private var annotatedCompressionStatus: CompressionStatus = CompressionStatus.NOT_STARTED
    private var cleanupStatus: CleanupStatus = CleanupStatus.NOT_STARTED
    private var retainedAssetType: RetainedAssetType = RetainedAssetType.NONE
    private var lastOverlayCaptureTsMs: Long = 0L
    private var rawVideoPersistJob: Job? = null
    private var annotatedExportJob: Job? = null
    private var compressionJob: Job? = null
    private var cleanupJob: Job? = null
    private val overlayFrames = mutableListOf<com.inversioncoach.app.recording.AnnotatedOverlayFrame>()
    private val freestyleOrientationClassifier = FreestyleOrientationClassifier()
    private var pendingStopCallback: ((SessionStopResult) -> Unit)? = null
    private var isSessionFinalizing = false
    private var activeSettings: UserSettings = UserSettings()
    private var sessionHadAnyVideo = false
    private val drillConfig = DrillConfigs.byTypeOrNull(drillType)
    private val readinessEngine = drillConfig?.let { SharedReadinessEngine(drillType, it, options.drillCameraSide) }
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
        SessionDiagnostics.logStructured(
            event = "session_started",
            sessionId = sessionId,
            drillType = drillType,
            rawUri = null,
            annotatedUri = null,
            overlayFrameCount = 0,
            failureReason = "analyzer=${metricsEngine::class.simpleName};movementPattern=$movementPattern",
        )
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
        val finalizedUri = uri?.takeIf { it.isNotBlank() }
        val activeSessionId = sessionId ?: return
        exportLifecycleState = ExportLifecycleState.PROCESSING
        AnnotatedExportJobTracker.markStarted(activeSessionId)
        AnnotatedExportJobTracker.updateProgress(activeSessionId, ExportProgressStage.PREPARING, 3, null)
        sessionHadAnyVideo = true
        SessionDiagnostics.logStructured(
            event = "annotated_export_requested",
            sessionId = activeSessionId,
            drillType = drillType,
            rawUri = finalizedUri,
            annotatedUri = null,
            overlayFrameCount = overlayFrames.size,
            failureReason = "timeoutMs=$ANNOTATED_EXPORT_TIMEOUT_MS;thread=${Thread.currentThread().name}",
        )
        if (finalizedUri.isNullOrBlank()) {
            setAnnotatedExportState(AnnotatedExportStatus.ANNOTATED_FAILED, AnnotatedExportFailureReason.RAW_URI_EMPTY.name)
            exportLifecycleState = ExportLifecycleState.FAILED
            setRawPersistState(RawPersistStatus.FAILED, AnnotatedExportFailureReason.RAW_SAVE_FAILED.name)
            AnnotatedExportJobTracker.markFinished(activeSessionId)
            return
        }
        rawVideoPersistJob = viewModelScope.launch {
            runFinalizationPipeline(activeSessionId, finalizedUri)
        }
    }



    fun onPoseFrame(frame: PoseFrame, settings: UserSettings) {
        activeSettings = settings
        val readiness = readinessEngine?.evaluate(frame)
        val sideForAnalysis = readiness?.actualSide ?: options.drillCameraSide
        val frameForSession = if (sessionMode == SessionMode.FREESTYLE) frame else frame.filterForDrillSide(sideForAnalysis)
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
        val motionEligible = sessionMode == SessionMode.DRILL && (readiness?.timerEligible ?: false)
        val freestyleViewMode = if (sessionMode == SessionMode.FREESTYLE) freestyleOrientationClassifier.classify(smoothed.joints) else FreestyleViewMode.UNKNOWN
        val motion = if (motionEligible) motionPipeline.analyze(frameForSession, settings.alignmentStrictness, calibration) else null
        _smoothedFrame.value = smoothed
        if (shouldCaptureOverlayFrame(smoothed.timestampMs)) {
            overlayFrames += overlayStabilizer.stabilize(
                frame = smoothed,
                sessionMode = sessionMode,
                drillCameraSide = if (sessionMode == SessionMode.FREESTYLE) null else options.drillCameraSide,
                showIdealLine = options.showIdealLine,
                showSkeleton = options.showSkeletonOverlay,
                freestyleViewMode = freestyleViewMode,
            )
            lastOverlayCaptureTsMs = smoothed.timestampMs
            if (overlayFrames.size % OVERLAY_FRAME_LOG_INTERVAL == 0) {
                SessionDiagnostics.logStructured(
                    event = "overlay_frames_count",
                    sessionId = sessionId,
                    drillType = drillType,
                    rawUri = rawVideoUri,
                    annotatedUri = annotatedVideoUri,
                    overlayFrameCount = overlayFrames.size,
                )
            }
        }
        if (sessionMode == SessionMode.DRILL && readiness == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Unsupported drill: $drillType")
            return
        }

        val rejectionReason = when {
            frame.rejectionReason != "none" -> frame.rejectionReason
            readiness != null && readiness.state < ReadinessState.READY_MINIMAL -> readiness.blockedReason
            else -> "none"
        }
        val setupMessage = if (readiness != null && readiness.state < ReadinessState.READY_MINIMAL) {
            setupGuidanceFor(readiness.blockedReason)
        } else {
            null
        }
        val rejectionMessage = setupMessage ?: rejectionMessageFor(rejectionReason)
        val readinessSummary = readiness?.let {
            "state=${it.state} preferred=${it.preferredSide} actual=${it.actualSide} left=${"%.2f".format(it.leftQuality.quality)} right=${"%.2f".format(it.rightQuality.quality)} timer=${it.timerEligible} rep=${it.repEligible} cue=${it.cueEligible} blocked=${it.blockedReason}"
        } ?: "state=unsupported"

        _uiState.value = _uiState.value.copy(
            confidence = smoothed.confidence,
            warningMessage = rejectionMessage,
            currentCue = if (readiness != null && readiness.state < ReadinessState.READY_MINIMAL) setupMessage ?: _uiState.value.currentCue else _uiState.value.currentCue,
            showDebugOverlay = settings.debugOverlayEnabled,
            debugLandmarksDetected = frameForSession.landmarksDetected,
            debugInferenceTimeMs = frameForSession.inferenceTimeMs,
            debugFrameDrops = frameForSession.droppedFrames,
            debugRejectionReason = "$rejectionReason|$readinessSummary",
            freestyleViewMode = freestyleViewMode,
        )
        SessionDiagnostics.log("readiness drill=$drillType $readinessSummary")

        if (sessionMode == SessionMode.DRILL && !motionEligible) {
            invalidFrameCount += 1
            invalidReasonCounts[rejectionReason] = (invalidReasonCounts[rejectionReason] ?: 0) + 1
            _uiState.value = _uiState.value.copy(
                debugMetrics = emptyList(),
                debugAngles = emptyList(),
                currentPhase = "setup",
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
                freestyleViewMode = freestyleViewMode,
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
            currentCue = cue?.text ?: motion?.cue?.text ?: _uiState.value.currentCue.ifBlank { "Ready. Start drill reps/hold." },
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


    private fun setupGuidanceFor(reason: String): String = when (reason) {
        "no_person_detected" -> "Step back so your full body is visible."
        "low_confidence" -> "Improve lighting and hold still for tracking."
        "missing_required_landmarks" -> "Keep hands, hips, and feet in frame."
        "wrong_orientation" -> "Turn to a clean side view for this drill."
        "body_not_fully_visible" -> "Keep your whole body inside the camera frame."
        else -> "Hold steady while we lock onto your setup pose."
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
                listOfNotNull(rawVideoPersistJob, annotatedExportJob, compressionJob, cleanupJob).joinAll()
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
                val hasActiveExportJob = AnnotatedExportJobTracker.isActive(activeSessionId)
                resolveTerminalAnnotatedExportStatus(hasActiveExportJob)
                val (reconciledStatus, reconciledFailureReason) = reconcileMediaStateForPersistence(hasActiveExportJob)
                if (reconciledStatus == AnnotatedExportStatus.ANNOTATED_FAILED && !reconciledFailureReason.isNullOrBlank()) {
                    repository.updateAnnotatedExportStatus(activeSessionId, reconciledStatus)
                    repository.updateAnnotatedExportFailureReason(activeSessionId, reconciledFailureReason)
                }
                val finalVideos = resolveSessionVideoOutcome(
                    rawVideoUri = rawVideoUri,
                    annotatedVideoUri = annotatedVideoUri,
                    exportStatus = reconciledStatus,
                )
                SessionDiagnostics.logStructured(
                    event = "replay_uri_selected",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = finalVideos.rawVideoUri,
                    annotatedUri = finalVideos.annotatedVideoUri,
                    overlayFrameCount = overlayFrames.size,
                    failureReason = annotatedExportFailureReason,
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
                        rawMasterUri = rawMasterUri,
                        annotatedMasterUri = annotatedMasterUri,
                        rawFinalUri = rawFinalUri ?: finalVideos.rawVideoUri,
                        annotatedFinalUri = annotatedFinalUri ?: finalVideos.annotatedVideoUri,
                        bestPlayableUri = finalVideos.bestPlayableUri,
                        rawPersistStatus = rawPersistStatus,
                        rawPersistFailureReason = rawPersistFailureReason,
                        annotatedExportStatus = finalVideos.annotatedExportStatus,
                        annotatedExportFailureReason = reconciledFailureReason,
                        annotatedExportFailureDetail = annotatedExportFailureDetail,
                        annotatedExportElapsedMs = annotatedExportElapsedMs,
                        annotatedExportStageAtFailure = annotatedExportStageAtFailure,
                        annotatedExportStage = annotatedExportStage,
                        annotatedExportPercent = annotatedExportPercent,
                        annotatedExportEtaSeconds = annotatedExportEtaSeconds,
                        annotatedExportLastUpdatedAt = annotatedExportLastUpdatedAt,
                        rawCompressionStatus = rawCompressionStatus,
                        annotatedCompressionStatus = annotatedCompressionStatus,
                        cleanupStatus = cleanupStatus,
                        retainedAssetType = finalVideos.retainedAssetType,
                        overlayFrameCount = overlayFrames.size,
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
                SessionDiagnostics.logStructured(
                    event = "session_finalized",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = rawVideoUri,
                    annotatedUri = annotatedVideoUri,
                    overlayFrameCount = overlayFrames.size,
                    failureReason = annotatedExportFailureReason,
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
                    rawMasterUri = null,
                    annotatedMasterUri = null,
                    rawFinalUri = null,
                    annotatedFinalUri = null,
                    bestPlayableUri = null,
                    rawPersistStatus = RawPersistStatus.NOT_STARTED,
                    rawPersistFailureReason = null,
                    annotatedExportStatus = AnnotatedExportStatus.NOT_STARTED,
                    annotatedExportFailureReason = null,
                    annotatedExportStage = AnnotatedExportStage.QUEUED,
                    annotatedExportPercent = 0,
                    rawCompressionStatus = CompressionStatus.NOT_STARTED,
                    annotatedCompressionStatus = CompressionStatus.NOT_STARTED,
                    cleanupStatus = CleanupStatus.NOT_STARTED,
                    retainedAssetType = RetainedAssetType.NONE,
                    overlayFrameCount = 0,
                    notesUri = null,
                    bestFrameTimestampMs = null,
                    worstFrameTimestampMs = null,
                    topImprovementFocus = "pending",
                ),
            )
            sessionId = newSessionId
            AnnotatedExportJobTracker.markFinished(newSessionId)
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

    private fun setRawPersistState(status: RawPersistStatus, failureReason: String?) {
        rawPersistStatus = status
        rawPersistFailureReason = if (status == RawPersistStatus.SUCCEEDED) null else failureReason
    }

    private fun setAnnotatedExportState(status: AnnotatedExportStatus, failureReason: String?) {
        annotatedExportStatus = status
        annotatedExportFailureReason = when (status) {
            AnnotatedExportStatus.PROCESSING,
            AnnotatedExportStatus.PROCESSING_SLOW,
            AnnotatedExportStatus.ANNOTATED_READY,
            AnnotatedExportStatus.NOT_STARTED -> null
            AnnotatedExportStatus.ANNOTATED_FAILED -> failureReason
        }
        if (status == AnnotatedExportStatus.ANNOTATED_READY && annotatedVideoUri.isNullOrBlank()) {
            annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED
            annotatedExportFailureReason = AnnotatedExportFailureReason.OUTPUT_URI_NULL.name
        }
    }

    private suspend fun updateAnnotatedExportProgress(
        activeSessionId: Long,
        stage: AnnotatedExportStage,
        percent: Int,
        etaMs: Long?,
        elapsedMs: Long,
    ) {
        annotatedExportStage = stage
        annotatedExportPercent = percent.coerceIn(0, 100)
        annotatedExportEtaSeconds = etaMs?.let { (it / 1000L).toInt().coerceAtLeast(1) }
        annotatedExportElapsedMs = elapsedMs
        annotatedExportLastUpdatedAt = System.currentTimeMillis()
        repository.updateAnnotatedExportProgress(
            sessionId = activeSessionId,
            stage = stage,
            percent = annotatedExportPercent,
            etaSeconds = annotatedExportEtaSeconds,
            elapsedMs = annotatedExportElapsedMs,
            failureDetail = annotatedExportFailureDetail,
            failureReason = annotatedExportFailureReason,
        )
        SessionDiagnostics.logStructured(
            event = "annotated_export_progress",
            sessionId = activeSessionId,
            drillType = drillType,
            rawUri = rawVideoUri,
            annotatedUri = annotatedVideoUri,
            overlayFrameCount = overlayFrames.size,
            failureReason = "stage=$stage;percent=$annotatedExportPercent;elapsedMs=$elapsedMs;etaSeconds=${annotatedExportEtaSeconds ?: -1};thread=${Thread.currentThread().name}",
        )
    }

    private fun reconcileMediaStateForPersistence(hasActiveExportJob: Boolean): Pair<AnnotatedExportStatus, String?> {
        if (!rawVideoUri.isNullOrBlank() && mediaAssetExists(rawVideoUri)) {
            rawPersistStatus = RawPersistStatus.SUCCEEDED
            rawPersistFailureReason = null
        }
        if (rawPersistStatus == RawPersistStatus.SUCCEEDED && rawPersistFailureReason == AnnotatedExportFailureReason.RAW_SAVE_FAILED.name) {
            rawPersistFailureReason = null
        }
        if ((annotatedExportStatus == AnnotatedExportStatus.PROCESSING || annotatedExportStatus == AnnotatedExportStatus.PROCESSING_SLOW) && hasActiveExportJob) {
            if (annotatedExportStatus != AnnotatedExportStatus.PROCESSING_SLOW && shouldMarkProcessingSlow()) {
                setAnnotatedExportState(AnnotatedExportStatus.PROCESSING_SLOW, null)
                annotatedExportStage = AnnotatedExportStage.RENDERING
                annotatedExportLastUpdatedAt = System.currentTimeMillis()
            }
        }
        if (annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY && annotatedVideoUri.isNullOrBlank()) {
            setAnnotatedExportState(AnnotatedExportStatus.ANNOTATED_FAILED, AnnotatedExportFailureReason.OUTPUT_URI_NULL.name)
        }
        return annotatedExportStatus to annotatedExportFailureReason
    }


    private fun shouldMarkProcessingSlow(): Boolean {
        val elapsedMs = annotatedExportElapsedMs ?: 0L
        return elapsedMs >= PROCESSING_SLOW_THRESHOLD_MS
    }

    private fun shouldCaptureOverlayFrame(timestampMs: Long): Boolean {
        if (timestampMs < lastOverlayCaptureTsMs) return false
        return overlayFrames.isEmpty() || (timestampMs - lastOverlayCaptureTsMs) >= MAX_EXPORT_FRAME_INTERVAL_MS
    }

    private fun exportFramesForSession(): List<com.inversioncoach.app.recording.AnnotatedOverlayFrame> {
        if (overlayFrames.isEmpty()) return emptyList()
        return overlayFrames.toList().sortedBy { it.timestampMs }
    }

    private suspend fun runFinalizationPipeline(activeSessionId: Long, finalizedUri: String) {
        val pipelineStartMs = System.currentTimeMillis()
        setRawPersistState(RawPersistStatus.PROCESSING, null)
        repository.updateRawPersistStatus(activeSessionId, RawPersistStatus.PROCESSING)
        repository.updateRawPersistFailureReason(activeSessionId, null)
        AnnotatedExportJobTracker.updateProgress(activeSessionId, ExportProgressStage.PREPARING, 15, null)
        val rawPersistStartMs = System.currentTimeMillis()
        SessionDiagnostics.logStructured("raw_persist_started", activeSessionId, drillType, finalizedUri, null, overlayFrames.size)
        rawMasterUri = repository.saveRawVideoBlob(activeSessionId, finalizedUri)
        rawVideoUri = rawMasterUri
        SessionDiagnostics.log("raw_persist_duration_ms=${System.currentTimeMillis() - rawPersistStartMs}")
        if (rawMasterUri.isNullOrBlank() || !MediaVerificationHelper.verify(rawMasterUri).isValid) {
            setRawPersistState(RawPersistStatus.FAILED, AnnotatedExportFailureReason.RAW_SAVE_FAILED.name)
            repository.updateRawPersistStatus(activeSessionId, RawPersistStatus.FAILED)
            repository.updateRawPersistFailureReason(activeSessionId, rawPersistFailureReason)
            persistAnnotatedExportFailed(activeSessionId, AnnotatedExportFailureReason.RAW_SAVE_FAILED.name)
            return
        }
        setRawPersistState(RawPersistStatus.SUCCEEDED, null)
        repository.updateRawPersistStatus(activeSessionId, RawPersistStatus.SUCCEEDED)
        repository.updateRawPersistFailureReason(activeSessionId, null)

        exportLifecycleState = ExportLifecycleState.PROCESSING
        setAnnotatedExportState(AnnotatedExportStatus.PROCESSING, null)
        repository.updateAnnotatedExportStatus(activeSessionId, AnnotatedExportStatus.PROCESSING)
        repository.updateAnnotatedExportFailureReason(activeSessionId, null)
        annotatedExportStage = AnnotatedExportStage.PREPARING
        annotatedExportPercent = 20
        annotatedExportEtaSeconds = null
        annotatedExportElapsedMs = 0L
        annotatedExportLastUpdatedAt = System.currentTimeMillis()
        repository.updateAnnotatedExportProgress(activeSessionId, AnnotatedExportStage.PREPARING, 20, null, 0L)
        SessionDiagnostics.logStructured("annotated_export_started", activeSessionId, drillType, rawMasterUri, null, overlayFrames.size)
        val exportStartMs = System.currentTimeMillis()
        AnnotatedExportJobTracker.updateProgress(activeSessionId, ExportProgressStage.LOADING_OVERLAYS, 30, null)

        val exportFrames = exportFramesForSession()
        try {
            updateAnnotatedExportProgress(activeSessionId, AnnotatedExportStage.LOADING_OVERLAYS, 30, null, System.currentTimeMillis() - exportStartMs)
            val exportResult = withTimeout(ANNOTATED_EXPORT_TIMEOUT_MS) {
                annotatedExportPipeline.export(
                    sessionId = activeSessionId,
                    rawVideoUri = rawMasterUri!!,
                    drillType = drillType,
                    drillCameraSide = options.drillCameraSide,
                    overlayFrames = exportFrames,
                    onRenderProgress = { rendered, total ->
                        val pct = 30 + (((rendered.toFloat() / total.coerceAtLeast(1).toFloat()) * 55f).toInt())
                        val elapsed = (System.currentTimeMillis() - exportStartMs).coerceAtLeast(1L)
                        val remainingFrames = (total - rendered).coerceAtLeast(0)
                        val frameRate = rendered.toDouble() / (elapsed / 1000.0)
                        val etaMs = if (frameRate > 0.01) ((remainingFrames / frameRate) * 1000.0).toLong() else null
                        AnnotatedExportJobTracker.updateProgress(activeSessionId, ExportProgressStage.RENDERING, pct, etaMs)
                        kotlinx.coroutines.runBlocking {
                            updateAnnotatedExportProgress(activeSessionId, AnnotatedExportStage.RENDERING, pct, etaMs, elapsed)
                        }
                    },
                )
            }
            val persistedUri = exportResult.persistedUri
            SessionDiagnostics.logStructured("annotated_export_output", activeSessionId, drillType, rawMasterUri, persistedUri, exportFrames.size)
            SessionDiagnostics.log("annotated_export_duration_ms=${System.currentTimeMillis() - exportStartMs}")
            AnnotatedExportJobTracker.updateProgress(activeSessionId, ExportProgressStage.VERIFYING, 90, null)
            val verifyStartMs = System.currentTimeMillis()
            val verification = MediaVerificationHelper.verify(persistedUri)
            SessionDiagnostics.log("verify_duration_ms=${System.currentTimeMillis() - verifyStartMs}")
            if (persistedUri.isNullOrBlank()) {
                persistAnnotatedExportFailed(activeSessionId, AnnotatedExportFailureReason.OUTPUT_URI_NULL.name)
                return
            }
            if (!verification.isValid) {
                persistAnnotatedExportFailed(
                    activeSessionId,
                    exportResult.failureReason ?: verification.failureReason?.name ?: AnnotatedExportFailureReason.ANNOTATED_EXPORT_FAILED.name,
                )
                return
            }
            annotatedMasterUri = persistedUri
            annotatedFinalUri = persistedUri
            annotatedVideoUri = persistedUri
            bestPlayableUri = persistedUri
            retainedAssetType = RetainedAssetType.ANNOTATED_FINAL
            setAnnotatedExportState(AnnotatedExportStatus.ANNOTATED_READY, null)
            exportLifecycleState = ExportLifecycleState.READY
            annotatedExportStage = AnnotatedExportStage.COMPLETED
            annotatedExportPercent = 100
            annotatedExportEtaSeconds = 0
            annotatedExportElapsedMs = System.currentTimeMillis() - exportStartMs
            annotatedExportFailureDetail = null
            annotatedExportStageAtFailure = null
            annotatedExportLastUpdatedAt = System.currentTimeMillis()
            repository.updateAnnotatedExportStatus(activeSessionId, AnnotatedExportStatus.ANNOTATED_READY)
            repository.updateAnnotatedExportFailureReason(activeSessionId, null)
            repository.updateAnnotatedExportProgress(
                sessionId = activeSessionId,
                stage = AnnotatedExportStage.COMPLETED,
                percent = 100,
                etaSeconds = 0,
                elapsedMs = annotatedExportElapsedMs,
                failureDetail = null,
                failureReason = null,
            )
            AnnotatedExportJobTracker.updateProgress(activeSessionId, ExportProgressStage.COMPLETED, 100, 0L)
        } catch (_: TimeoutCancellationException) {
            SessionDiagnostics.logStructured(
                event = "annotated_export_timeout",
                sessionId = activeSessionId,
                drillType = drillType,
                rawUri = rawVideoUri,
                annotatedUri = annotatedVideoUri,
                overlayFrameCount = exportFrames.size,
                failureReason = "timeoutMs=$ANNOTATED_EXPORT_TIMEOUT_MS;elapsedMs=${System.currentTimeMillis() - exportStartMs}",
            )
            persistAnnotatedExportFailed(activeSessionId, AnnotatedExportFailureReason.EXPORT_TIMED_OUT.name)
        } catch (_: CancellationException) {
            SessionDiagnostics.logStructured(
                event = "annotated_export_cancelled",
                sessionId = activeSessionId,
                drillType = drillType,
                rawUri = rawVideoUri,
                annotatedUri = annotatedVideoUri,
                overlayFrameCount = exportFrames.size,
                failureReason = "elapsedMs=${System.currentTimeMillis() - exportStartMs}",
            )
            persistAnnotatedExportFailed(activeSessionId, AnnotatedExportFailureReason.EXPORT_CANCELLED.name)
        } catch (t: Throwable) {
            persistAnnotatedExportFailed(activeSessionId, "EXCEPTION_${t::class.simpleName ?: "UNKNOWN"}")
        } finally {
            if (annotatedExportStatus == AnnotatedExportStatus.PROCESSING || annotatedExportStatus == AnnotatedExportStatus.PROCESSING_SLOW) {
                persistAnnotatedExportFailed(activeSessionId, AnnotatedExportFailureReason.UNKNOWN.name)
            }
            if (bestPlayableUri.isNullOrBlank()) {
                bestPlayableUri = rawVideoUri
                retainedAssetType = if (rawVideoUri.isNullOrBlank()) RetainedAssetType.NONE else RetainedAssetType.RAW_FINAL
            }
            SessionDiagnostics.logStructured(
                event = "annotated_export_final_status",
                sessionId = activeSessionId,
                drillType = drillType,
                rawUri = rawVideoUri,
                annotatedUri = annotatedVideoUri,
                overlayFrameCount = exportFrames.size,
                failureReason = "status=$annotatedExportStatus;reason=${annotatedExportFailureReason.orEmpty()}",
            )
            SessionDiagnostics.log("post_stop_total_duration_ms=${System.currentTimeMillis() - pipelineStartMs}")
            AnnotatedExportJobTracker.markFinished(activeSessionId)
        }
    }

    private suspend fun persistAnnotatedExportFailed(activeSessionId: Long, reason: String) {
        val previousStage = annotatedExportStage
        annotatedExportStage = AnnotatedExportStage.FAILED
        annotatedExportStageAtFailure = previousStage.name
        annotatedExportFailureReason = reason
        annotatedExportFailureDetail = "Export failed during ${previousStage.name.lowercase()}"
        annotatedExportLastUpdatedAt = System.currentTimeMillis()
        annotatedVideoUri = null
        annotatedFinalUri = null
        setAnnotatedExportState(AnnotatedExportStatus.ANNOTATED_FAILED, reason)
        exportLifecycleState = ExportLifecycleState.FAILED
        repository.updateAnnotatedExportStatus(activeSessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
        repository.updateAnnotatedExportFailureReason(activeSessionId, reason)
        repository.updateAnnotatedExportProgress(
            sessionId = activeSessionId,
            stage = AnnotatedExportStage.FAILED,
            percent = annotatedExportPercent,
            etaSeconds = null,
            elapsedMs = annotatedExportElapsedMs,
            failureDetail = annotatedExportFailureDetail,
            failureReason = reason,
        )
        AnnotatedExportJobTracker.updateProgress(activeSessionId, ExportProgressStage.FAILED, 100, 0L)
    }

    private fun resolveTerminalAnnotatedExportStatus(hasActiveExportJob: Boolean): AnnotatedExportStatus {
        if (annotatedExportStatus != AnnotatedExportStatus.PROCESSING && annotatedExportStatus != AnnotatedExportStatus.PROCESSING_SLOW) {
            return annotatedExportStatus
        }
        if (hasActiveExportJob) {
            if (shouldMarkProcessingSlow()) {
                setAnnotatedExportState(AnnotatedExportStatus.PROCESSING_SLOW, null)
            }
            return annotatedExportStatus
        }
        setAnnotatedExportState(AnnotatedExportStatus.PROCESSING_SLOW, null)
        return annotatedExportStatus
    }

    private suspend fun compressAnnotatedThenCleanup(activeSessionId: Long) {
        val source = annotatedMasterUri ?: return compressRawFallback(activeSessionId)
        annotatedCompressionStatus = CompressionStatus.COMPRESSING
        setAnnotatedExportState(AnnotatedExportStatus.PROCESSING, null)
        val compressed = compressionPipeline.compressTo(
            sourceUri = source,
            outputFile = repository.sessionWorkingFile(activeSessionId, "annotated_final_tmp.mp4"),
        )
        if (compressed.outputUri.isNullOrBlank()) {
            annotatedCompressionStatus = CompressionStatus.FAILED
            setAnnotatedExportState(AnnotatedExportStatus.ANNOTATED_FAILED, AnnotatedExportFailureReason.ANNOTATED_COMPRESSION_FAILED.name)
            compressRawFallback(activeSessionId)
            return
        }
        val persisted = repository.saveAnnotatedFinalVideoBlob(activeSessionId, compressed.outputUri)
        val verification = MediaVerificationHelper.verify(persisted)
        if (!verification.isValid) {
            annotatedCompressionStatus = CompressionStatus.FAILED
            setAnnotatedExportState(
                AnnotatedExportStatus.ANNOTATED_FAILED,
                verification.failureReason?.name ?: AnnotatedExportFailureReason.ANNOTATED_COMPRESSION_FAILED.name,
            )
            compressRawFallback(activeSessionId)
            return
        }
        annotatedFinalUri = persisted
        annotatedVideoUri = persisted
        bestPlayableUri = persisted
        retainedAssetType = RetainedAssetType.ANNOTATED_FINAL
        annotatedCompressionStatus = CompressionStatus.READY
        setAnnotatedExportState(AnnotatedExportStatus.ANNOTATED_READY, null)
        cleanupIntermediates(activeSessionId, keepRawMaster = false)
    }

    private suspend fun compressRawFallback(activeSessionId: Long) {
        val source = rawMasterUri ?: return
        rawCompressionStatus = CompressionStatus.COMPRESSING
        val compressed = compressionPipeline.compressTo(
            sourceUri = source,
            outputFile = repository.sessionWorkingFile(activeSessionId, "raw_final_tmp.mp4"),
        )
        if (compressed.outputUri.isNullOrBlank()) {
            rawCompressionStatus = CompressionStatus.FAILED
            setAnnotatedExportState(AnnotatedExportStatus.ANNOTATED_FAILED, AnnotatedExportFailureReason.RAW_COMPRESSION_FAILED.name)
            return
        }
        val persisted = repository.saveRawFinalVideoBlob(activeSessionId, compressed.outputUri)
        val verification = MediaVerificationHelper.verify(persisted)
        if (!verification.isValid) {
            rawCompressionStatus = CompressionStatus.FAILED
            setAnnotatedExportState(
                AnnotatedExportStatus.ANNOTATED_FAILED,
                verification.failureReason?.name ?: AnnotatedExportFailureReason.RAW_COMPRESSION_FAILED.name,
            )
            return
        }
        rawFinalUri = persisted
        rawVideoUri = persisted
        bestPlayableUri = persisted
        retainedAssetType = RetainedAssetType.RAW_FINAL
        rawCompressionStatus = CompressionStatus.READY
        setAnnotatedExportState(AnnotatedExportStatus.ANNOTATED_FAILED, annotatedExportFailureReason)
        cleanupIntermediates(activeSessionId, keepRawMaster = false)
    }

    private suspend fun cleanupIntermediates(activeSessionId: Long, keepRawMaster: Boolean) {
        val debugMode = activeSettings.debugOverlayEnabled
        cleanupStatus = CleanupStatus.DELETING_INTERMEDIATES
        var failed = false
        if (!debugMode && !keepRawMaster) {
            if (!repository.deleteUri(annotatedMasterUri)) failed = true
            if (!repository.deleteUri(rawMasterUri)) failed = true
        }
        cleanupStatus = when {
            failed -> CleanupStatus.PARTIAL
            else -> CleanupStatus.COMPLETE
        }
    }


    override fun onCleared() {
        super.onCleared()
        finalizeSessionSilentlyIfActive()
    }

    companion object {
        private const val FRAME_PERSIST_INTERVAL_MS = 250L
        private const val MAX_EXPORT_FRAME_INTERVAL_MS = 50L
        private const val OVERLAY_FRAME_LOG_INTERVAL = 60
        private const val ANNOTATED_EXPORT_TIMEOUT_MS = 120_000L
        private const val PROCESSING_SLOW_THRESHOLD_MS = 90_000L
    }
}

internal fun resolveSessionVideoOutcome(
    rawVideoUri: String?,
    annotatedVideoUri: String?,
    exportStatus: AnnotatedExportStatus,
): SessionVideoOutcome {
    val annotated = annotatedVideoUri?.takeIf(::mediaAssetExists)
    if (!annotated.isNullOrBlank()) {
        return SessionVideoOutcome(
            rawVideoUri = rawVideoUri,
            annotatedVideoUri = annotated,
            bestPlayableUri = annotated,
            retainedAssetType = RetainedAssetType.ANNOTATED_FINAL,
            annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_READY,
        )
    }
    val raw = rawVideoUri?.takeIf(::mediaAssetExists)
    val reconciledStatus = when {
        raw == null -> exportStatus
        exportStatus == AnnotatedExportStatus.ANNOTATED_READY -> AnnotatedExportStatus.ANNOTATED_FAILED
        else -> exportStatus
    }
    return SessionVideoOutcome(
        rawVideoUri = raw,
        annotatedVideoUri = null,
        bestPlayableUri = raw,
        retainedAssetType = if (raw == null) RetainedAssetType.NONE else RetainedAssetType.RAW_FINAL,
        annotatedExportStatus = reconciledStatus,
    )
}
