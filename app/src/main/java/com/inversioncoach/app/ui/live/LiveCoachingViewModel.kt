package com.inversioncoach.app.ui.live

import android.media.MediaExtractor
import android.media.MediaFormat
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
import com.inversioncoach.app.model.SessionStartupState
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.RetainedAssetType
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.drills.core.DrillRegistry
import com.inversioncoach.app.motion.MotionAnalysisPipeline
import com.inversioncoach.app.motion.UserCalibrationSettings
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.FreestyleOrientationClassifier
import com.inversioncoach.app.overlay.FreestyleViewMode
import com.inversioncoach.app.pose.PoseSmoothingEngine
import com.inversioncoach.app.pose.PoseScaleMode
import com.inversioncoach.app.pose.PoseValidationAndCorrectionEngine
import com.inversioncoach.app.recording.MediaVerificationHelper
import com.inversioncoach.app.recording.MediaVerificationResult
import com.inversioncoach.app.recording.ReplayInspectionResult
import com.inversioncoach.app.recording.AnnotatedExportPipeline
import com.inversioncoach.app.recording.OverlayStabilizer
import com.inversioncoach.app.recording.OverlayTimelineJson
import com.inversioncoach.app.recording.OverlayTimelineRecorder
import com.inversioncoach.app.recording.toTimelineFrame
import com.inversioncoach.app.recording.VideoCompressionPipeline
import com.inversioncoach.app.storage.SessionBlobStorage
import com.inversioncoach.app.storage.repository.SessionRepository
import com.inversioncoach.app.summary.SummaryGenerator
import com.inversioncoach.app.calibration.CalibrationProfileProvider
import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.calibration.rep.SessionRepTemplateUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    val validationThresholdSeconds: Int,
)

class LiveCoachingViewModel(
    private val drillType: DrillType,
    private val metricsEngine: AlignmentMetricsEngine,
    private val cueEngine: CueEngine,
    private val repository: SessionRepository,
    private val calibrationProfileProvider: CalibrationProfileProvider,
    private val options: LiveSessionOptions,
    private val summaryGenerator: SummaryGenerator = SummaryGenerator(com.inversioncoach.app.summary.RecommendationEngine()),
    private val smoother: PoseSmoothingEngine = PoseSmoothingEngine(),
    private val correctionEngine: PoseValidationAndCorrectionEngine = PoseValidationAndCorrectionEngine(),
    private val motionPipeline: MotionAnalysisPipeline = MotionAnalysisPipeline(drillType),
    private val overlayStabilizer: OverlayStabilizer = OverlayStabilizer(),
    private val compressionPipeline: VideoCompressionPipeline = VideoCompressionPipeline(),
    private val annotatedExportPipeline: AnnotatedExportPipeline,
) : ViewModel() {
    private val repTemplateUpdater = SessionRepTemplateUpdater()

    enum class ExportLifecycleState {
        IDLE,
        PROCESSING,
        READY,
        FAILED,
    }

    private val sessionMode = drillType.sessionMode()
    private val drillRegistry = DrillRegistry()
    private val drillDefinition = drillRegistry.definitionFor(drillType)

    private val _uiState = MutableStateFlow(
        LiveSessionUiState(
            drillType = drillType,
            sessionMode = sessionMode,
            isRecording = false,
            startupState = SessionStartupState.IDLE,
            sessionCountdownRemainingSeconds = null,
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
    private var finalizationJob: Job? = null
    private var annotatedExportJob: Job? = null
    private var compressionJob: Job? = null
    private var cleanupJob: Job? = null
    private var startupJob: Job? = null
    private val overlayFrames = mutableListOf<com.inversioncoach.app.recording.AnnotatedOverlayFrame>()
    private var overlayTimelineRecorder: OverlayTimelineRecorder? = null
    private var overlayTimelineUri: String? = null
    private val freestyleOrientationClassifier = FreestyleOrientationClassifier()
    private val pendingStopCallbacks = mutableListOf<(SessionStopResult) -> Unit>()
    private var isSessionFinalizing = false
    private var finalizeOwnerSessionId: Long? = null
    private var recordingFinalizedCallbackCount: Int = 0
    private var acceptedFinalizedCallbackCount: Int = 0
    private var acceptedFinalizeRawUri: String? = null
    private var stopPressedAtMs: Long = 0L
    private var rawPersistAttemptCount: Int = 0
    private var exportLaunchAttemptCount: Int = 0
    private var overlayCaptureFrozen: Boolean = false
    private var frozenOverlayTimeline: com.inversioncoach.app.recording.OverlayTimeline? = null
    private var activeSettings: UserSettings = UserSettings()
    private var sessionHadAnyVideo = false
    private var activeMovementProfile: DrillMovementProfile? = null
    private var startupCancelled = false
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

    val activeSessionId: Long?
        get() = sessionId

    init {
        val movementPattern = runCatching { drillDefinition.movementType.name }.getOrDefault("GENERIC")
        SessionDiagnostics.record(
            sessionId = sessionId,
            stage = SessionDiagnostics.Stage.SESSION_START,
            status = SessionDiagnostics.Status.STARTED,
            message = "Session initialized",
            metrics = mapOf("drillType" to drillType.name),
        )
        SessionDiagnostics.logStructured(
            event = "session_started",
            sessionId = sessionId,
            drillType = drillType,
            rawUri = null,
            annotatedUri = null,
            overlayFrameCount = 0,
            failureReason = "analyzer=${metricsEngine::class.simpleName};movementPattern=$movementPattern",
        )
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
        recordingFinalizedCallbackCount += 1
        val finalizedUri = uri?.takeIf { it.isNotBlank() }
        val activeSessionId = sessionId ?: return
        SessionDiagnostics.logStructured(
            event = "recorder_finalized_callback",
            sessionId = activeSessionId,
            drillType = drillType,
            rawUri = finalizedUri,
            annotatedUri = null,
            overlayFrameCount = overlayFrames.size,
            failureReason = "count=$recordingFinalizedCallbackCount;acceptedCount=$acceptedFinalizedCallbackCount;acceptedRawUri=${acceptedFinalizeRawUri.orEmpty()}",
        )

        if (finalizedUri.isNullOrBlank()) {
            SessionDiagnostics.logStructured(
                event = "recorder_finalized_empty_uri_ignored",
                sessionId = activeSessionId,
                drillType = drillType,
                rawUri = null,
                annotatedUri = null,
                overlayFrameCount = overlayFrames.size,
                failureReason = "waiting_for_non_empty_uri",
            )
            return
        }
        val acceptance = evaluateFinalizeCallbackAcceptance(acceptedFinalizeRawUri, finalizedUri)
        acceptedFinalizeRawUri = acceptance.acceptedUri
        when (resolveRecorderFinalizeFlowOutcome(acceptance.action, finalizationJob?.isActive == true)) {
            RecorderFinalizeFlowOutcome.IGNORE_CALLBACK -> {
                SessionDiagnostics.logStructured(
                    event = "duplicate_finalize_ignored",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = finalizedUri,
                    annotatedUri = annotatedVideoUri,
                    overlayFrameCount = overlayFrames.size,
                    failureReason = "action=${acceptance.action};acceptedRawUri=${acceptance.acceptedUri};incomingRawUri=$finalizedUri",
                )
                return
            }

            RecorderFinalizeFlowOutcome.IGNORE_UPGRADE_INFLIGHT -> {
                SessionDiagnostics.logStructured(
                    event = "finalize_callback_upgraded_ignored_inflight",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = acceptance.acceptedUri,
                    annotatedUri = annotatedVideoUri,
                    overlayFrameCount = overlayFrames.size,
                    failureReason = "incomingRawUri=$finalizedUri;reason=finalization_already_inflight",
                )
                return
            }

            RecorderFinalizeFlowOutcome.START_FINALIZATION -> {
                when (acceptance.action) {
                    FinalizeCallbackAction.UPGRADED_TO_PERSISTED -> {
                        SessionDiagnostics.logStructured(
                            event = "finalize_callback_upgraded",
                            sessionId = activeSessionId,
                            drillType = drillType,
                            rawUri = acceptance.acceptedUri,
                            annotatedUri = annotatedVideoUri,
                            overlayFrameCount = overlayFrames.size,
                            failureReason = "incomingRawUri=$finalizedUri;action=launch_with_upgraded_uri",
                        )
                    }

                    FinalizeCallbackAction.ACCEPTED_FIRST -> {
                        SessionDiagnostics.logStructured(
                            event = "finalize_callback_accepted",
                            sessionId = activeSessionId,
                            drillType = drillType,
                            rawUri = acceptance.acceptedUri,
                            annotatedUri = annotatedVideoUri,
                            overlayFrameCount = overlayFrames.size,
                            failureReason = "incomingRawUri=$finalizedUri",
                        )
                    }

                    else -> Unit
                }
            }
        }
        if (!tryAcquireFinalizeOwner(activeSessionId)) return
        acceptedFinalizedCallbackCount += 1
        SessionDiagnostics.logStructured(
            event = "finalize_owner_accepted_callback_count",
            sessionId = activeSessionId,
            drillType = drillType,
            rawUri = acceptance.acceptedUri,
            annotatedUri = annotatedVideoUri,
            overlayFrameCount = overlayFrames.size,
            failureReason = "acceptedFinalizeOwnerCount=$acceptedFinalizedCallbackCount;totalFinalizeCallbacks=$recordingFinalizedCallbackCount",
        )
        sessionHadAnyVideo = true
        finalizationJob = viewModelScope.launch {
            runFinalizationPipeline(activeSessionId, acceptance.acceptedUri.orEmpty())
        }
    }


    fun onPoseFrame(frame: PoseFrame, settings: UserSettings) {
        activeSettings = settings
        val readiness = readinessEngine?.evaluate(frame)
        val sideForAnalysis = readiness?.actualSide ?: options.drillCameraSide
        val frameForSession = if (sessionMode == SessionMode.FREESTYLE) frame else frame.filterForDrillSide(sideForAnalysis)
        val bodyProfile = UserBodyProfile.decode(settings.userBodyProfileJson)
        val corrected = correctionEngine.process(frameForSession, bodyProfile)
        if (corrected.inversionDetected) {
            SessionDiagnostics.logStructured(
                event = "pose_inversion_detected",
                sessionId = sessionId,
                drillType = drillType,
                rawUri = null,
                annotatedUri = null,
                overlayFrameCount = overlayFrames.size,
                failureReason = "unreliable=${corrected.unreliableJointNames.joinToString(",")}",
            )
        }
        corrected.unreliableJointNames.forEach { jointName ->
            SessionDiagnostics.logStructured(
                event = "pose_joint_marked_unreliable",
                sessionId = sessionId,
                drillType = drillType,
                rawUri = null,
                annotatedUri = null,
                overlayFrameCount = overlayFrames.size,
                failureReason = "joint=$jointName",
            )
        }
        val smoothed = smoother.smooth(corrected.frame)
        val calibration = UserCalibrationSettings()
        val motionEligible = sessionMode == SessionMode.DRILL && (readiness?.timerEligible ?: false)
        val freestyleViewMode = if (sessionMode == SessionMode.FREESTYLE) freestyleOrientationClassifier.classify(smoothed.joints) else FreestyleViewMode.UNKNOWN
        val motion = if (motionEligible) {
            motionPipeline.analyze(
                frame = corrected.frame,
                calibration = calibration,
                movementProfile = activeMovementProfile,
            )
        } else {
            null
        }
        _smoothedFrame.value = smoothed
        if (!overlayCaptureFrozen && shouldCaptureOverlayFrame(smoothed.timestampMs)) {
            val overlayFrame = overlayStabilizer.stabilize(
                frame = smoothed,
                sessionMode = sessionMode,
                drillCameraSide = if (sessionMode == SessionMode.FREESTYLE) null else options.drillCameraSide,
                showIdealLine = options.showIdealLine,
                showSkeleton = options.showSkeletonOverlay,
                freestyleViewMode = freestyleViewMode,
                scaleMode = PoseScaleMode.FILL,
                unreliableJointNames = corrected.unreliableJointNames,
            )
            overlayFrames += overlayFrame
            if (overlayTimelineRecorder == null && sessionStartedAtMs > 0L) {
                overlayTimelineRecorder = OverlayTimelineRecorder(startedAtMs = sessionStartedAtMs, sampleIntervalMs = OVERLAY_TIMELINE_SAMPLE_INTERVAL_MS)
                SessionDiagnostics.log("overlay_timeline_recorder_start sampleIntervalMs=$OVERLAY_TIMELINE_SAMPLE_INTERVAL_MS")
            }
            sessionId?.let { activeSessionId ->
                overlayTimelineRecorder?.record(overlayFrame.toTimelineFrame(activeSessionId, sessionStartedAtMs))
            }
            lastOverlayCaptureTsMs = smoothed.timestampMs
            if (overlayFrames.size % OVERLAY_FRAME_LOG_INTERVAL == 0) {
                SessionDiagnostics.record(
                    sessionId = sessionId,
                    stage = SessionDiagnostics.Stage.OVERLAY_CAPTURE,
                    status = SessionDiagnostics.Status.PROGRESS,
                    message = "Overlay samples captured",
                    metrics = mapOf("overlayFrameCount" to overlayFrames.size.toString()),
                )
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
            unreliableJointNames = corrected.unreliableJointNames,
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

        if (cue != null && _uiState.value.startupState == SessionStartupState.ACTIVE) {
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

    fun beginStartupCountdown(countdownSeconds: Int): Boolean {
        if (_uiState.value.startupState != SessionStartupState.IDLE || startupJob?.isActive == true) return false
        startupCancelled = false
        _spokenCue.value = null
        _uiState.value = _uiState.value.copy(
            startupState = SessionStartupState.COUNTDOWN,
            sessionCountdownRemainingSeconds = countdownSeconds.coerceAtLeast(0),
            warningMessage = null,
        )
        return true
    }

    fun launchStartupCountdown(countdownSeconds: Int): Boolean {
        val started = beginStartupCountdown(countdownSeconds)
        if (!started) return false

        startupJob?.cancel()
        startupJob = viewModelScope.launch {
            try {
                val normalizedCountdown = countdownSeconds.coerceAtLeast(0)
                if (normalizedCountdown > 0) {
                    for (remaining in normalizedCountdown downTo 1) {
                        if (_uiState.value.startupState != SessionStartupState.COUNTDOWN || startupCancelled) return@launch
                        onSessionCountdownTick(remaining)
                        delay(1000L)
                    }
                }

                if (_uiState.value.startupState != SessionStartupState.COUNTDOWN || startupCancelled) return@launch
                onSessionCountdownTick(0)
                activateSessionIfStartupReady()
            } finally {
                startupJob = null
            }
        }
        return true
    }

    fun onSessionCountdownTick(remainingSeconds: Int) {
        if (_uiState.value.startupState != SessionStartupState.COUNTDOWN || startupCancelled) return
        val boundedRemaining = remainingSeconds.coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(
            sessionCountdownRemainingSeconds = boundedRemaining,
        )
    }

    fun activateSessionIfStartupReady(): Boolean {
        if (_uiState.value.startupState != SessionStartupState.COUNTDOWN || startupCancelled) return false
        val initiatedAtMs = System.currentTimeMillis()
        _spokenCue.value = null
        sessionStartedAtMs = initiatedAtMs
        overlayFrames.clear()
        overlayTimelineRecorder = OverlayTimelineRecorder(
            startedAtMs = initiatedAtMs,
            sampleIntervalMs = OVERLAY_TIMELINE_SAMPLE_INTERVAL_MS,
        )
        _uiState.value = _uiState.value.copy(
            startupState = SessionStartupState.ACTIVE,
            sessionCountdownRemainingSeconds = null,
            warningMessage = null,
        )
        startSession()
        return true
    }

    fun cancelStartup() {
        startupJob?.cancel()
        startupJob = null
        if (_uiState.value.startupState == SessionStartupState.ACTIVE) return
        startupCancelled = true
        _spokenCue.value = null
        _uiState.value = _uiState.value.copy(
            startupState = SessionStartupState.CANCELLED,
            sessionCountdownRemainingSeconds = null,
            warningMessage = null,
        )
    }

    fun stopSession(onSessionFinalized: (SessionStopResult) -> Unit) {
        viewModelScope.launch {
            startupJob?.cancel()
            startupJob = null
            val startupGuardSeconds = activeSettings.startupCountdownSeconds.coerceAtLeast(0)
            val postActivationMinimumSessionSeconds = 0
            if (_uiState.value.startupState != SessionStartupState.ACTIVE) {
                cancelStartup()
                val currentSessionId = sessionId
                if (currentSessionId != null) {
                    repository.deleteSession(currentSessionId)
                }
                onSessionFinalized(
                    SessionStopResult(
                        sessionId = currentSessionId ?: 0L,
                        wasDiscardedForShortDuration = true,
                        elapsedSessionMs = 0L,
                        validationThresholdSeconds = startupGuardSeconds,
                    ),
                )
                return@launch
            }
            pendingStopCallbacks += onSessionFinalized
            if (isSessionFinalizing) return@launch
            val activeSessionId = sessionId
            if (activeSessionId == null) {
                _uiState.value = _uiState.value.copy(
                    warningMessage = "Preparing session. Stopping as soon as initialization completes.",
                )
                return@launch
            }
            isSessionFinalizing = true
            stopPressedAtMs = System.currentTimeMillis()
            var stopResult: SessionStopResult? = null
            try {
                SessionDiagnostics.logStructured(
                    event = "stop_pressed",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = rawVideoUri,
                    annotatedUri = annotatedVideoUri,
                    overlayFrameCount = overlayFrames.size,
                    failureReason = "stopPressedAtMs=$stopPressedAtMs;sessionStartedAtMs=$sessionStartedAtMs;rawPersistStatus=$rawPersistStatus;annotatedStatus=$annotatedExportStatus",
                )
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
                val elapsedSessionMs = (completedAtMs - sessionStartedAtMs).coerceAtLeast(0L)
                val elapsedSessionSeconds = elapsedSessionMs / 1000.0
                SessionDiagnostics.log(
                    "session_finalize drill=$drillType validFrames=$validFrameCount invalidFrames=$invalidFrameCount invalidReasons={$invalidReasonSummary} " +
                        "aggregatedIssues=${aggregatedIssues.size} savedRaw=$rawVideoUri exportedAnnotated=$annotatedVideoUri elapsed=${"%.2f".format(elapsedSessionSeconds)}s " +
                        "postActivationMinSessionDuration=${postActivationMinimumSessionSeconds}s shouldDelete=false",
                )
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
                SessionDiagnostics.record(
                    sessionId = activeSessionId,
                    stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
                    status = SessionDiagnostics.Status.SUCCEEDED,
                    message = "Replay source resolved",
                    errorCode = if (finalVideos.annotatedVideoUri == null && finalVideos.rawVideoUri != null) AnnotatedExportFailureReason.REPLAY_SELECTION_FELL_BACK_TO_RAW.name else null,
                )
                if (finalVideos.annotatedVideoUri == null && finalVideos.rawVideoUri != null) {
                    SessionDiagnostics.record(
                        sessionId = activeSessionId,
                        stage = SessionDiagnostics.Stage.FALLBACK_DECISION,
                        status = SessionDiagnostics.Status.FALLBACK,
                        message = "Falling back to raw replay due to annotated unavailability",
                        errorCode = reconciledFailureReason,
                    )
                }
                val replaySelection = selectReplayAsset(
                    SessionRecord(
                        id = activeSessionId,
                        title = sessionTitle,
                        drillType = drillType,
                        startedAtMs = sessionStartedAtMs,
                        completedAtMs = completedAtMs,
                        overallScore = sessionComputation.score.overall,
                        strongestArea = latestScore.strongestArea,
                        limitingFactor = latestScore.limitingFactor,
                        issues = topIssues,
                        wins = wins,
                        metricsJson = calibrationMetadataJson(),
                        annotatedVideoUri = finalVideos.annotatedVideoUri,
                        rawVideoUri = finalVideos.rawVideoUri,
                        rawMasterUri = rawMasterUri,
                        annotatedMasterUri = annotatedMasterUri,
                        rawFinalUri = rawFinalUri,
                        annotatedFinalUri = annotatedFinalUri,
                        bestPlayableUri = finalVideos.bestPlayableUri,
                        rawPersistStatus = rawPersistStatus,
                        rawPersistFailureReason = rawPersistFailureReason,
                        annotatedExportStatus = finalVideos.annotatedExportStatus,
                        annotatedExportFailureReason = reconciledFailureReason,
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
                SessionDiagnostics.record(
                    sessionId = activeSessionId,
                    stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
                    status = SessionDiagnostics.Status.PROGRESS,
                    message = "Replay selection=${replaySelection.label}",
                    metrics = mapOf("replayUri" to replaySelection.uri.orEmpty()),
                )
                val learnedProfile = maybePersistLearnedRepTemplate(completedAtMs)
                val calibrationProfileForSession = learnedProfile ?: activeMovementProfile
                repository.saveSession(
                    SessionRecord(
                        id = activeSessionId,
                        title = sessionTitle,
                        drillType = drillType,
                        sessionSource = com.inversioncoach.app.model.SessionSource.LIVE_COACHING,
                        startedAtMs = sessionStartedAtMs,
                        completedAtMs = completedAtMs,
                        overallScore = sessionComputation.score.overall,
                        strongestArea = latestScore.strongestArea,
                        limitingFactor = latestScore.limitingFactor,
                        issues = topIssues,
                        wins = wins,
                        metricsJson = calibrationMetadataJson(),
                        annotatedVideoUri = finalVideos.annotatedVideoUri,
                        rawVideoUri = finalVideos.rawVideoUri,
                        rawMasterUri = rawMasterUri,
                        annotatedMasterUri = annotatedMasterUri,
                        rawFinalUri = rawFinalUri,
                        annotatedFinalUri = annotatedFinalUri,
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
                        overlayTimelineUri = overlayTimelineUri,
                        calibrationProfileVersion = calibrationProfileForSession?.profileVersion,
                        calibrationUpdatedAtMs = calibrationProfileForSession?.updatedAtMs,
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
                stopResult = SessionStopResult(
                    sessionId = activeSessionId,
                    wasDiscardedForShortDuration = false,
                    elapsedSessionMs = elapsedSessionMs,
                    validationThresholdSeconds = postActivationMinimumSessionSeconds,
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
            } catch (t: Throwable) {
                SessionDiagnostics.logStructured(
                    event = "session_finalize_failed",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = rawVideoUri,
                    annotatedUri = annotatedVideoUri,
                    overlayFrameCount = overlayFrames.size,
                    failureReason = t::class.simpleName ?: "UNKNOWN",
                )
                val elapsedSessionMs = (System.currentTimeMillis() - sessionStartedAtMs).coerceAtLeast(0L)
                stopResult = SessionStopResult(
                    sessionId = activeSessionId,
                    wasDiscardedForShortDuration = false,
                    elapsedSessionMs = elapsedSessionMs,
                    validationThresholdSeconds = postActivationMinimumSessionSeconds,
                )
            } finally {
                isSessionFinalizing = false
                val result = stopResult ?: SessionStopResult(
                    sessionId = activeSessionId,
                    wasDiscardedForShortDuration = false,
                    elapsedSessionMs = (System.currentTimeMillis() - sessionStartedAtMs).coerceAtLeast(0L),
                    validationThresholdSeconds = postActivationMinimumSessionSeconds,
                )
                completePendingStopCallbacks(result)
            }
        }
    }

    private fun completePendingStopCallbacks(result: SessionStopResult) {
        if (pendingStopCallbacks.isEmpty()) return
        val callbacks = pendingStopCallbacks.toList()
        pendingStopCallbacks.clear()
        callbacks.forEach { callback -> callback(result) }
    }

    fun finalizeSessionSilentlyIfActive() {
        if (_uiState.value.startupState != SessionStartupState.ACTIVE) {
            cancelStartup()
            val staleSessionId = sessionId
            if (staleSessionId != null) {
                viewModelScope.launch { repository.deleteSession(staleSessionId) }
            }
            return
        }
        stopSession { }
    }

    fun finalScore(): DrillScore = latestScore

    private fun startSession() {
        viewModelScope.launch {
            smoother.reset()
            correctionEngine.reset()
            activeMovementProfile = calibrationProfileProvider.resolve(drillType)
            motionPipeline.setRepTemplate(activeMovementProfile?.repTemplate)
            val now = System.currentTimeMillis()
            sessionStartedAtMs = now
            val newSessionId = repository.saveSession(
                SessionRecord(
                    title = sessionTitle,
                    drillType = drillType,
                    sessionSource = com.inversioncoach.app.model.SessionSource.LIVE_COACHING,
                    startedAtMs = now,
                    completedAtMs = 0L,
                    overallScore = 0,
                    strongestArea = "pending",
                    limitingFactor = "pending",
                    issues = "",
                    wins = "",
                    metricsJson = calibrationMetadataJson(),
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
                    overlayTimelineUri = null,
                    calibrationProfileVersion = activeMovementProfile?.profileVersion,
                    calibrationUpdatedAtMs = activeMovementProfile?.updatedAtMs,
                    notesUri = null,
                    bestFrameTimestampMs = null,
                    worstFrameTimestampMs = null,
                    topImprovementFocus = "pending",
                ),
            )
            SessionDiagnostics.logStructured(
                event = "recording_start_timestamp_captured",
                sessionId = newSessionId,
                drillType = drillType,
                rawUri = null,
                annotatedUri = null,
                overlayFrameCount = 0,
                failureReason = "sessionStartedAtMs=$now",
            )
            sessionId = newSessionId
            AnnotatedExportJobTracker.markFinished(newSessionId)
            if (pendingStopCallbacks.isNotEmpty()) {
                val callbacks = pendingStopCallbacks.toList()
                pendingStopCallbacks.clear()
                callbacks.forEach { callback -> stopSession(callback) }
            }
        }
    }


    private fun PoseFrame.filterForDrillSide(side: DrillCameraSide): PoseFrame {
        val prefix = if (side == DrillCameraSide.LEFT) "left_" else "right_"
        val filtered = joints.filter { it.name == "nose" || !it.name.contains("_") || it.name.startsWith(prefix) }
        return copy(joints = filtered, landmarksDetected = filtered.size)
    }

    private fun SmoothedPoseFrame.toPoseFrame(): PoseFrame = PoseFrame(
        timestampMs = timestampMs,
        joints = joints,
        confidence = confidence,
        analysisWidth = analysisWidth,
        analysisHeight = analysisHeight,
        analysisRotationDegrees = analysisRotationDegrees,
        mirrored = mirrored,
    )

    private fun calibrationMetadataJson(): String {
        val profile = activeMovementProfile ?: return ""
        return "calibrationProfileVersion:${profile.profileVersion};calibrationUpdatedAtMs:${profile.updatedAtMs}"
    }

    private suspend fun maybePersistLearnedRepTemplate(nowMs: Long): DrillMovementProfile? {
        if (sessionMode != SessionMode.DRILL) return null

        val currentProfile = activeMovementProfile ?: calibrationProfileProvider.resolve(drillType)
        val updatedProfile = repTemplateUpdater.updateProfile(
            profile = currentProfile,
            repWindows = motionPipeline.completedRepFrames(),
            minimumRepCount = 2,
            minimumFramesPerRep = 10,
            learnedWeight = 0.3f,
            nowMs = nowMs,
        ) ?: return null
        calibrationProfileProvider.save(updatedProfile)
        activeMovementProfile = updatedProfile
        motionPipeline.setRepTemplate(updatedProfile.repTemplate)
        return updatedProfile
    }

    private fun setRawPersistState(status: RawPersistStatus, failureReason: String?) {
        rawPersistStatus = status
        rawPersistFailureReason = failureReason
    }

    private fun setAnnotatedExportState(status: AnnotatedExportStatus, failureReason: String?) {
        annotatedExportStatus = status
        annotatedExportFailureReason = when (status) {
            AnnotatedExportStatus.VALIDATING_INPUT,
            AnnotatedExportStatus.PROCESSING,
            AnnotatedExportStatus.PROCESSING_SLOW,
            AnnotatedExportStatus.ANNOTATED_READY,
            AnnotatedExportStatus.SKIPPED,
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
        try {
            rawPersistAttemptCount += 1
            if (rawPersistStatus == RawPersistStatus.SUCCEEDED && !rawMasterUri.isNullOrBlank()) {
                SessionDiagnostics.logStructured(
                    event = "duplicate_raw_persist_ignored",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = rawMasterUri,
                    annotatedUri = annotatedVideoUri,
                    overlayFrameCount = overlayFrames.size,
                    failureReason = "rawPersistAttemptCount=$rawPersistAttemptCount",
                )
                return
            }
            setRawPersistState(RawPersistStatus.PROCESSING, null)
            repository.updateRawPersistStatus(activeSessionId, RawPersistStatus.PROCESSING)
            repository.updateRawPersistFailureReason(activeSessionId, null)
            SessionDiagnostics.logStructured("raw_persist_started", activeSessionId, drillType, finalizedUri, null, overlayFrames.size, "attempt=$rawPersistAttemptCount")
            val sourceReadiness = awaitFinalizeSourceReadiness(
                sessionId = activeSessionId,
                finalizedUri = finalizedUri,
            )
            val sourceReadyInspection = sourceReadiness?.inspection
            logRawInspection(
                event = "raw_source_finalize_probe",
                sessionId = activeSessionId,
                uri = finalizedUri,
                inspection = sourceReadyInspection,
                detail = "phase=pre_persist;stableSize=${sourceReadiness?.sizeStable == true};stableDuration=${sourceReadiness?.durationStable == true}",
            )
            if (sourceReadiness == null || !sourceReadiness.isReadyForPersist) {
                setRawPersistState(RawPersistStatus.FAILED, AnnotatedExportFailureReason.SOURCE_VIDEO_UNREADABLE.name)
                repository.updateRawPersistStatus(activeSessionId, RawPersistStatus.FAILED)
                repository.updateRawPersistFailureReason(activeSessionId, rawPersistFailureReason)
                SessionDiagnostics.logStructured(
                    event = "raw_persist_failed",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = finalizedUri,
                    annotatedUri = null,
                    overlayFrameCount = overlayFrames.size,
                    failureReason = "reason=source_not_ready;acceptedRawUri=${acceptedFinalizeRawUri.orEmpty()};totalFinalizeCallbacks=$recordingFinalizedCallbackCount;acceptedFinalizeCallbacks=$acceptedFinalizedCallbackCount",
                )
                persistAnnotatedExportFailed(activeSessionId, AnnotatedExportFailureReason.SOURCE_VIDEO_UNREADABLE.name)
                return
            }

            val rawPersistStartMs = System.currentTimeMillis()
            val sourcePrePersistSizeBytes = sourceReadyInspection?.fileSizeBytes ?: 0L
            val sourcePrePersistDurationMs = sourceReadyInspection?.durationMs ?: 0L
            val persistedRawCandidate = repository.saveRawVideoBlob(activeSessionId, finalizedUri)
            val expectedPersistedRaw = repository
                .sessionWorkingFile(activeSessionId, SessionBlobStorage.RAW_MASTER_FILE_NAME)
                .toURI()
                .toString()
            val rawVerification = verifyPersistedRawVideoUris(
                candidateUris = listOf(persistedRawCandidate, expectedPersistedRaw),
                metadataVerifier = { MediaVerificationHelper.verify(it) },
                replayInspector = { MediaVerificationHelper.inspectReplay(it) },
                requireReadableMetadataForPersistence = true,
            )
            rawMasterUri = rawVerification.persistedUri
            rawVideoUri = rawVerification.persistedUri
            rawFinalUri = rawVerification.persistedUri
            bestPlayableUri = if (rawVerification.isReplayPlayable) rawVerification.persistedUri else null
            logRawInspection(
                event = "raw_persisted_probe",
                sessionId = activeSessionId,
                uri = rawVerification.persistedUri,
                inspection = rawVerification.inspection,
                detail = "phase=post_persist;sourceReady=${sourceReadiness.isReadyForPersist};sourcePrePersistSizeBytes=$sourcePrePersistSizeBytes;sourcePrePersistDurationMs=$sourcePrePersistDurationMs",
            )
            SessionDiagnostics.log("raw_persist_duration_ms=${System.currentTimeMillis() - rawPersistStartMs}")

            if (!rawVerification.isPersisted) {
                setRawPersistState(RawPersistStatus.FAILED, AnnotatedExportFailureReason.RAW_SAVE_FAILED.name)
                repository.updateRawPersistStatus(activeSessionId, RawPersistStatus.FAILED)
                repository.updateRawPersistFailureReason(activeSessionId, rawPersistFailureReason)
                SessionDiagnostics.logStructured(
                    event = "raw_persist_failed",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = finalizedUri,
                    annotatedUri = null,
                    overlayFrameCount = overlayFrames.size,
                    failureReason = AnnotatedExportFailureReason.RAW_SAVE_FAILED.name,
                )
                persistAnnotatedExportFailed(activeSessionId, AnnotatedExportFailureReason.RAW_SAVE_FAILED.name)
                return
            }

            val snapshot = createExportSnapshot(activeSessionId)

            val rawReplayFailureReason = classifyRawReplayFailure(rawVerification)
            setRawPersistState(RawPersistStatus.SUCCEEDED, rawReplayFailureReason)
            repository.updateMediaPipelineState(activeSessionId) { session ->
                session.copy(
                    rawPersistStatus = RawPersistStatus.SUCCEEDED,
                    rawPersistFailureReason = rawReplayFailureReason,
                    rawVideoUri = rawMasterUri,
                    rawFinalUri = rawMasterUri,
                    rawMasterUri = rawMasterUri,
                    bestPlayableUri = if (rawVerification.isReplayPlayable) rawMasterUri else null,
                    completedAtMs = snapshot.stopTimestampMs,
                )
            }
            SessionDiagnostics.logStructured(
                event = "raw_persist_succeeded",
                sessionId = activeSessionId,
                drillType = drillType,
                rawUri = rawMasterUri,
                annotatedUri = null,
                overlayFrameCount = snapshot.overlayFrameCount,
                failureReason = "rawDurationMs=${snapshot.rawDurationMs};replayPlayable=${rawVerification.isReplayPlayable};rawPersistFailureReason=${rawReplayFailureReason.orEmpty()};totalFinalizeCallbacks=$recordingFinalizedCallbackCount;acceptedFinalizeCallbacks=$acceptedFinalizedCallbackCount;acceptedRawUri=${acceptedFinalizeRawUri.orEmpty()};sourcePrePersistSizeBytes=$sourcePrePersistSizeBytes;persistedSizeBytes=${rawVerification.inspection?.fileSizeBytes};sourcePrePersistDurationMs=$sourcePrePersistDurationMs;persistedDurationMs=${rawVerification.inspection?.durationMs};captureSpanMs=${captureSpanMs(sessionStartedAtMs, stopPressedAtMs)};captureVsPersistedDurationDeltaMs=${captureSpanMs(sessionStartedAtMs, stopPressedAtMs) - (rawVerification.inspection?.durationMs ?: 0L)};inspection=${rawVerification.inspectionSummary()}",
            )
            if (!rawVerification.isReplayPlayable) {
                persistAnnotatedExportFailed(
                    activeSessionId,
                    rawReplayFailureReason ?: AnnotatedExportFailureReason.SOURCE_VIDEO_UNREADABLE.name,
                )
                return
            }

            if (snapshot.overlayTimeline.frames.isEmpty()) {
                setAnnotatedExportState(AnnotatedExportStatus.SKIPPED, AnnotatedExportFailureReason.OVERLAY_CAPTURE_EMPTY.name)
                exportLifecycleState = ExportLifecycleState.IDLE
                annotatedExportStage = AnnotatedExportStage.COMPLETED
                annotatedExportPercent = 100
                annotatedExportEtaSeconds = null
                annotatedExportElapsedMs = 0L
                annotatedExportLastUpdatedAt = System.currentTimeMillis()
                repository.updateAnnotatedExportStatus(activeSessionId, AnnotatedExportStatus.SKIPPED)
                repository.updateAnnotatedExportFailureReason(activeSessionId, AnnotatedExportFailureReason.OVERLAY_CAPTURE_EMPTY.name)
                SessionDiagnostics.logStructured(
                    event = "annotated_export_skipped_no_overlay",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = rawVideoUri,
                    annotatedUri = null,
                    overlayFrameCount = 0,
                    failureReason = AnnotatedExportFailureReason.OVERLAY_CAPTURE_EMPTY.name,
                )
                return
            }

            setAnnotatedExportState(AnnotatedExportStatus.VALIDATING_INPUT, null)
            repository.updateAnnotatedExportStatus(activeSessionId, AnnotatedExportStatus.VALIDATING_INPUT)
            val preflight = validateExportSnapshot(snapshot)
            if (preflight.fatalReason != null) {
                persistAnnotatedExportFailed(activeSessionId, preflight.fatalReason)
                return
            }
            var exportSnapshot = preflight.snapshot
            val frozenExportSnapshot = annotatedExportPipeline.freezeSnapshotForExport(
                overlayTimeline = exportSnapshot.overlayTimeline,
                rawDurationMsHint = exportSnapshot.rawDurationMs,
            )
            if (frozenExportSnapshot.usableOverlayFrameCount != exportSnapshot.overlayFrameCount) {
                exportSnapshot = exportSnapshot.copy(
                    overlayTimeline = frozenExportSnapshot.overlayTimeline,
                    overlayFrameCount = frozenExportSnapshot.usableOverlayFrameCount,
                )
                repository.updateMediaPipelineState(activeSessionId) { session ->
                    session.copy(overlayFrameCount = exportSnapshot.overlayFrameCount)
                }
            }
            if (exportSnapshot.overlayTimeline.frames.isEmpty()) {
                val reason = if (snapshot.overlayFrameCount > 0) {
                    AnnotatedExportFailureReason.EXPORT_INPUT_CORRUPTED_AFTER_FREEZE.name
                } else {
                    AnnotatedExportFailureReason.OVERLAY_TIMELINE_EMPTY.name
                }
                persistAnnotatedExportFailed(activeSessionId, reason)
                SessionDiagnostics.logStructured(
                    event = "annotated_export_skipped_empty_overlay_after_preflight",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = exportSnapshot.rawUri,
                    annotatedUri = null,
                    overlayFrameCount = 0,
                    failureReason = reason,
                )
                return
            }

            exportLaunchAttemptCount += 1
            if (AnnotatedExportJobTracker.isActive(activeSessionId) || annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY) {
                SessionDiagnostics.logStructured(
                    event = "duplicate_export_launch_ignored",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = rawMasterUri,
                    annotatedUri = annotatedVideoUri,
                    overlayFrameCount = exportSnapshot.overlayFrameCount,
                    failureReason = "exportLaunchAttemptCount=$exportLaunchAttemptCount",
                )
                return
            }
            SessionDiagnostics.logStructured(
                event = "export_launch_started",
                sessionId = activeSessionId,
                drillType = drillType,
                rawUri = exportSnapshot.rawUri,
                annotatedUri = null,
                overlayFrameCount = exportSnapshot.overlayFrameCount,
                failureReason = "exportLaunchAttemptCount=$exportLaunchAttemptCount",
            )

            exportLifecycleState = ExportLifecycleState.PROCESSING
            setAnnotatedExportState(AnnotatedExportStatus.PROCESSING, null)
            annotatedExportStage = AnnotatedExportStage.PREPARING
            annotatedExportPercent = 20
            annotatedExportEtaSeconds = null
            annotatedExportElapsedMs = 0L
            annotatedExportLastUpdatedAt = System.currentTimeMillis()
            repository.updateAnnotatedExportStatus(activeSessionId, AnnotatedExportStatus.PROCESSING)
            repository.updateAnnotatedExportFailureReason(activeSessionId, null)
            repository.updateAnnotatedExportProgress(activeSessionId, AnnotatedExportStage.PREPARING, 20, null, 0L)
            AnnotatedExportJobTracker.markStarted(activeSessionId)
            SessionDiagnostics.logStructured(
                event = "annotated_export_launched",
                sessionId = activeSessionId,
                drillType = drillType,
                rawUri = exportSnapshot.rawUri,
                annotatedUri = null,
                overlayFrameCount = exportSnapshot.overlayFrameCount,
            )

            val exportStartMs = System.currentTimeMillis()
            try {
                val exportResult = annotatedExportPipeline.export(
                    sessionId = activeSessionId,
                    rawVideoUri = exportSnapshot.rawUri,
                    drillType = drillType,
                    drillCameraSide = options.drillCameraSide,
                    overlayTimeline = exportSnapshot.overlayTimeline,
                    rawDurationMsHint = exportSnapshot.rawDurationMs,
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
                if (!exportResult.started) {
                    annotatedExportPercent = 0
                    annotatedExportEtaSeconds = null
                }
                val persistedUri = exportResult.persistedUri
                if (persistedUri.isNullOrBlank() || exportResult.verificationStatus != AnnotatedExportPipeline.VerificationStatus.PASSED) {
                    persistAnnotatedExportFailed(
                        activeSessionId,
                        exportResult.failureReason ?: AnnotatedExportFailureReason.ANNOTATED_EXPORT_FAILED.name,
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
                repository.updateMediaPipelineState(activeSessionId) { session ->
                    session.copy(
                        annotatedVideoUri = persistedUri,
                        annotatedFinalUri = persistedUri,
                        annotatedMasterUri = persistedUri,
                        bestPlayableUri = persistedUri,
                        retainedAssetType = RetainedAssetType.ANNOTATED_FINAL,
                        annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_READY,
                        annotatedExportFailureReason = null,
                        annotatedExportFailureDetail = null,
                        annotatedExportStage = AnnotatedExportStage.COMPLETED,
                        annotatedExportPercent = 100,
                        annotatedExportEtaSeconds = 0,
                        annotatedExportElapsedMs = annotatedExportElapsedMs,
                        annotatedExportLastUpdatedAt = annotatedExportLastUpdatedAt,
                    )
                }
                SessionDiagnostics.logStructured(
                    event = "annotated_export_succeeded",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = rawVideoUri,
                    annotatedUri = persistedUri,
                    overlayFrameCount = snapshot.overlayFrameCount,
                )
                AnnotatedExportJobTracker.updateProgress(activeSessionId, ExportProgressStage.COMPLETED, 100, 0L)
            } catch (_: CancellationException) {
                persistAnnotatedExportFailed(activeSessionId, "EXPORT_CANCELLED")
            } catch (t: Throwable) {
                persistAnnotatedExportFailed(activeSessionId, "EXCEPTION_${t::class.simpleName ?: "UNKNOWN"}")
            } finally {
                if (bestPlayableUri.isNullOrBlank()) {
                    bestPlayableUri = rawVideoUri
                    retainedAssetType = if (rawVideoUri.isNullOrBlank()) RetainedAssetType.NONE else RetainedAssetType.RAW_FINAL
                }
                SessionDiagnostics.log("post_stop_total_duration_ms=${System.currentTimeMillis() - pipelineStartMs}")
                AnnotatedExportJobTracker.markFinished(activeSessionId)
            }
        } finally {
            finalizeOwnerSessionId = null
            finalizationJob = null
            SessionDiagnostics.logStructured(
                event = "finalize_owner_released",
                sessionId = activeSessionId,
                drillType = drillType,
                rawUri = rawVideoUri,
                annotatedUri = annotatedVideoUri,
                overlayFrameCount = overlayFrames.size,
            )
        }
    }

    private fun tryAcquireFinalizeOwner(activeSessionId: Long): Boolean {
        val existingOwner = finalizeOwnerSessionId
        if (existingOwner == null) {
            finalizeOwnerSessionId = activeSessionId
            SessionDiagnostics.logStructured(
                event = "finalize_owner_acquired",
                sessionId = activeSessionId,
                drillType = drillType,
                rawUri = rawVideoUri,
                annotatedUri = annotatedVideoUri,
                overlayFrameCount = overlayFrames.size,
            )
            return true
        }
        SessionDiagnostics.logStructured(
            event = "duplicate_finalize_ignored",
            sessionId = activeSessionId,
            drillType = drillType,
            rawUri = rawVideoUri,
            annotatedUri = annotatedVideoUri,
            overlayFrameCount = overlayFrames.size,
            failureReason = "owner=$existingOwner",
        )
        return false
    }

    private suspend fun createExportSnapshot(activeSessionId: Long): ExportSnapshot {
        val stopTimestamp = System.currentTimeMillis()
        val liveOverlayFrameCountAtFreeze = overlayFrames.size
        overlayCaptureFrozen = true
        val frozenSource = frozenOverlayTimeline ?: (overlayTimelineRecorder?.snapshot()
            ?: com.inversioncoach.app.recording.OverlayTimeline(
                startedAtMs = sessionStartedAtMs,
                sampleIntervalMs = OVERLAY_TIMELINE_SAMPLE_INTERVAL_MS,
                frames = emptyList(),
            ))
        val timeline = deepCopyOverlayTimelineForExport(frozenSource)
        val normalizedTimeline = normalizeFrozenOverlayTimelineToRelative(timeline)
        frozenOverlayTimeline = normalizedTimeline
        overlayTimelineUri = repository.saveOverlayTimeline(activeSessionId, OverlayTimelineJson.encode(normalizedTimeline))
        repository.updateMediaPipelineState(activeSessionId) { session ->
            session.copy(
                overlayFrameCount = normalizedTimeline.frames.size,
                overlayTimelineUri = overlayTimelineUri,
            )
        }
        val rawFile = resolveReadableFile(rawMasterUri)
        val maxOverlayTimestampMs = normalizedTimeline.frames.lastOrNull()?.relativeTimestampMs ?: 0L
        val firstLiveTs = overlayFrames.firstOrNull()?.timestampMs
        val lastLiveTs = overlayFrames.lastOrNull()?.timestampMs
        val firstFrozenTs = timeline.frames.firstOrNull()?.timestampMs
        val lastFrozenTs = timeline.frames.lastOrNull()?.timestampMs
        val firstNormalizedFrozenTs = normalizedTimeline.frames.firstOrNull()?.timestampMs
        val lastNormalizedFrozenTs = normalizedTimeline.frames.lastOrNull()?.timestampMs
        val durationResolution = resolveRawDurationWithRetries(
            rawUri = rawMasterUri,
            fileExists = rawFile?.exists() == true,
            fileSizeBytes = rawFile?.length() ?: 0L,
            sessionElapsedMs = (stopTimestamp - sessionStartedAtMs).coerceAtLeast(0L),
            overlayMaxTimestampMs = maxOverlayTimestampMs,
            onAttempt = { attempt ->
                SessionDiagnostics.logStructured(
                    event = "raw_duration_read_attempt",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = rawMasterUri,
                    annotatedUri = null,
                    overlayFrameCount = normalizedTimeline.frames.size,
                    failureReason = "attempt=${attempt.attemptIndex};uri=${attempt.uri};fileExists=${attempt.fileExists};fileSize=${attempt.fileSizeBytes};source=${attempt.source};durationMs=${attempt.durationMs}",
                )
            },
            onRetry = { retry, waitMs ->
                SessionDiagnostics.logStructured(
                    event = "raw_duration_read_retry",
                    sessionId = activeSessionId,
                    drillType = drillType,
                    rawUri = rawMasterUri,
                    annotatedUri = null,
                    overlayFrameCount = normalizedTimeline.frames.size,
                    failureReason = "retry=$retry;waitMs=$waitMs",
                )
            },
        )
        val rawDurationMs = durationResolution.durationMs
        SessionDiagnostics.logStructured(
            event = "raw_duration_source_selected",
            sessionId = activeSessionId,
            drillType = drillType,
            rawUri = rawMasterUri,
            annotatedUri = null,
            overlayFrameCount = normalizedTimeline.frames.size,
            failureReason = "source=${durationResolution.source};durationMs=$rawDurationMs",
        )
        val overlayFramesIgnoredAfterFreeze = (liveOverlayFrameCountAtFreeze - normalizedTimeline.frames.size).coerceAtLeast(0)
        SessionDiagnostics.logStructured(
            event = "overlay_freeze_snapshot_created",
            sessionId = activeSessionId,
            drillType = drillType,
            rawUri = rawMasterUri,
            annotatedUri = null,
            overlayFrameCount = normalizedTimeline.frames.size,
            failureReason = "sessionStartTs=$sessionStartedAtMs;overlayFreezeTs=$stopTimestamp;rawDurationMs=$rawDurationMs;source=${durationResolution.source};firstLiveOverlayTs=$firstLiveTs;lastLiveOverlayTs=$lastLiveTs;firstFrozenOverlayTs=$firstFrozenTs;lastFrozenOverlayTs=$lastFrozenTs;firstFrozenOverlayTsAfterNormalization=$firstNormalizedFrozenTs;lastFrozenOverlayTsAfterNormalization=$lastNormalizedFrozenTs;liveOverlayFrameCountAtFreeze=$liveOverlayFrameCountAtFreeze;frozenOverlayFrameCount=${normalizedTimeline.frames.size};overlayFramesIgnoredAfterFreeze=$overlayFramesIgnoredAfterFreeze",
        )
        return ExportSnapshot(
            sessionId = activeSessionId,
            stopTimestampMs = stopTimestamp,
            rawUri = rawMasterUri.orEmpty(),
            rawDurationMs = rawDurationMs,
            rawDurationSource = durationResolution.source,
            overlayTimeline = normalizedTimeline,
            overlayTimelineUri = overlayTimelineUri,
            overlayFrameCount = normalizedTimeline.frames.size,
        )
    }

    private fun validateExportSnapshot(snapshot: ExportSnapshot): ExportPreflightResult {
        val rawFile = resolveReadableFile(snapshot.rawUri)
        val hasReadableRaw = rawFile != null && rawFile.length() > 0L
        val liveOverlayFrameCountAtFreeze = overlayFrames.size
        val preflight = prepareExportSnapshotInputs(
            snapshot = snapshot,
            overlayCaptureFrozen = overlayCaptureFrozen,
            hasReadableRaw = hasReadableRaw,
            toleranceMs = EXPORT_SNAPSHOT_DURATION_TOLERANCE_MS,
            liveOverlayFrameCountAtFreeze = liveOverlayFrameCountAtFreeze,
        )
        SessionDiagnostics.logStructured(
            event = "export_input_validation",
            sessionId = snapshot.sessionId,
            drillType = drillType,
            rawUri = preflight.snapshot.rawUri,
            annotatedUri = null,
            overlayFrameCount = preflight.snapshot.overlayFrameCount,
            failureReason = "fatalReason=${preflight.fatalReason.orEmpty()};sessionStartTs=$sessionStartedAtMs;freezeTs=${snapshot.stopTimestampMs};durationMs=${preflight.snapshot.rawDurationMs};durationSource=${preflight.snapshot.rawDurationSource};durationMismatchMs=${preflight.durationMismatchMs};clampApplied=${preflight.clampApplied};countBeforeTrim=${preflight.countBeforeTrim};countAfterTrim=${preflight.countAfterTrim};firstFrozenTsBeforeNormalization=${preflight.firstFrozenTsBeforeNormalization};lastFrozenTsBeforeNormalization=${preflight.lastFrozenTsBeforeNormalization};firstFrozenTsAfterNormalization=${preflight.firstFrozenTsAfterNormalization};lastFrozenTsAfterNormalization=${preflight.lastFrozenTsAfterNormalization};liveOverlayFrameCountAtFreeze=${preflight.liveOverlayFrameCountAtFreeze};frozenOverlayFrameCount=${preflight.frozenOverlayFrameCount};overlayFramesIgnoredAfterFreeze=${preflight.overlayFramesIgnoredAfterFreeze}",
        )
        if (preflight.clampApplied && preflight.durationMismatchMs > 0L) {
            SessionDiagnostics.logStructured(
                event = "raw_truncation_detected_before_export",
                sessionId = snapshot.sessionId,
                drillType = drillType,
                rawUri = snapshot.rawUri,
                annotatedUri = null,
                overlayFrameCount = preflight.snapshot.overlayFrameCount,
                failureReason = "rawDurationMs=${preflight.snapshot.rawDurationMs};durationMismatchMs=${preflight.durationMismatchMs};captureSpanMs=${captureSpanMs(sessionStartedAtMs, snapshot.stopTimestampMs)}",
            )
        }
        return preflight
    }
    private suspend fun persistAnnotatedExportFailed(activeSessionId: Long, reason: String) {
        val previousStage = annotatedExportStage
        annotatedExportStage = AnnotatedExportStage.FAILED
        annotatedExportStageAtFailure = previousStage.name
        annotatedExportFailureReason = reason
        annotatedExportFailureDetail = "Export failed during ${previousStage.name.lowercase()}"
        annotatedExportLastUpdatedAt = System.currentTimeMillis()
        annotatedExportEtaSeconds = null
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
        SessionDiagnostics.record(
            sessionId = activeSessionId,
            stage = SessionDiagnostics.Stage.ANNOTATED_EXPORT_VERIFY,
            status = SessionDiagnostics.Status.FAILED,
            message = "Annotated export marked failed",
            errorCode = reason,
        )
        SessionDiagnostics.logStructured(
            event = "annotated_export_failed",
            sessionId = activeSessionId,
            drillType = drillType,
            rawUri = rawVideoUri,
            annotatedUri = null,
            overlayFrameCount = overlayFrames.size,
            failureReason = reason,
        )
        AnnotatedExportJobTracker.updateProgress(activeSessionId, ExportProgressStage.FAILED, 100, null)
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
        setAnnotatedExportState(AnnotatedExportStatus.ANNOTATED_FAILED, "EXPORT_JOB_LOST")
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

    private suspend fun awaitFinalizeSourceReadiness(
        sessionId: Long,
        finalizedUri: String,
        maxAttempts: Int = 5,
    ): FinalizeSourceReadiness? {
        var latestInspection: ReplayInspectionResult? = null
        var previousSizeBytes: Long? = null
        var previousDurationMs: Long? = null
        var previousDecodable: Boolean = false
        var stableSizeStreak = 0
        var stableDurationStreak = 0
        for (attempt in 1..maxAttempts.coerceAtLeast(1)) {
            val inspection = MediaVerificationHelper.inspectReplay(finalizedUri)
            latestInspection = inspection
            val currentSizeBytes = inspection.fileSizeBytes
            val currentDurationMs = inspection.durationMs ?: 0L
            stableSizeStreak = if (previousDecodable && previousSizeBytes != null && currentSizeBytes == previousSizeBytes) stableSizeStreak + 1 else 0
            stableDurationStreak = if (previousDecodable && previousDurationMs != null && currentDurationMs > 0L && currentDurationMs == previousDurationMs) stableDurationStreak + 1 else 0
            logRawInspection(
                event = "raw_source_readiness_probe",
                sessionId = sessionId,
                uri = finalizedUri,
                inspection = inspection,
                detail = "attempt=$attempt;sizeStableStreak=$stableSizeStreak;durationStableStreak=$stableDurationStreak",
            )
            val ready = inspection.isDecodable && stableSizeStreak >= 1 && stableDurationStreak >= 1
            if (ready) {
                return FinalizeSourceReadiness(
                    inspection = inspection,
                    sizeStable = true,
                    durationStable = true,
                )
            }
            previousSizeBytes = currentSizeBytes
            previousDurationMs = currentDurationMs
            previousDecodable = inspection.isDecodable
            if (attempt < maxAttempts) {
                val waitMs = RAW_FINALIZE_READINESS_BACKOFF_MS.getOrElse(attempt - 1) {
                    RAW_FINALIZE_READINESS_BACKOFF_MS.lastOrNull() ?: 300L
                }
                delay(waitMs)
            }
        }
        return latestInspection?.let {
            FinalizeSourceReadiness(
                inspection = it,
                sizeStable = false,
                durationStable = false,
            )
        }
    }

    private fun logRawInspection(
        event: String,
        sessionId: Long,
        uri: String?,
        inspection: ReplayInspectionResult?,
        detail: String = "",
    ) {
        val details = inspection
        val payload = buildString {
            if (detail.isNotBlank()) {
                append(detail)
                append(';')
            }
            append("exists=${details?.fileExists};")
            append("size=${details?.fileSizeBytes};")
            append("lastModifiedMs=${details?.lastModifiedEpochMs};")
            append("durationMs=${details?.durationMs};")
            append("trackCount=${details?.trackCount};")
            append("hasVideoTrack=${details?.hasVideoTrack};")
            append("firstFrameDecoded=${details?.firstFrameDecoded};")
            append("error=${details?.errorDetail.orEmpty()}")
        }
        SessionDiagnostics.logStructured(
            event = event,
            sessionId = sessionId,
            drillType = drillType,
            rawUri = uri,
            annotatedUri = null,
            overlayFrameCount = overlayFrames.size,
            failureReason = payload,
        )
    }


    override fun onCleared() {
        super.onCleared()
        startupJob?.cancel()
        startupJob = null
        smoother.reset()
        correctionEngine.reset()
        finalizeSessionSilentlyIfActive()
    }

    companion object {
        private const val FRAME_PERSIST_INTERVAL_MS = 250L
        private const val MAX_EXPORT_FRAME_INTERVAL_MS = 50L
        private const val OVERLAY_TIMELINE_SAMPLE_INTERVAL_MS = 80L
        private const val OVERLAY_FRAME_LOG_INTERVAL = 60
        private const val PROCESSING_SLOW_THRESHOLD_MS = 90_000L
        private const val EXPORT_SNAPSHOT_DURATION_TOLERANCE_MS = 600L
        private val RAW_FINALIZE_READINESS_BACKOFF_MS = listOf(200L, 350L, 550L, 800L)
    }
}




internal data class ExportPreflightResult(
    val snapshot: ExportSnapshot,
    val fatalReason: String?,
    val clampApplied: Boolean,
    val durationMismatchMs: Long,
    val liveOverlayFrameCountAtFreeze: Int,
    val frozenOverlayFrameCount: Int,
    val overlayFramesIgnoredAfterFreeze: Int,
    val countBeforeTrim: Int,
    val countAfterTrim: Int,
    val firstFrozenTsBeforeNormalization: Long?,
    val lastFrozenTsBeforeNormalization: Long?,
    val firstFrozenTsAfterNormalization: Long?,
    val lastFrozenTsAfterNormalization: Long?,
)

internal fun deepCopyOverlayTimelineForExport(
    timeline: com.inversioncoach.app.recording.OverlayTimeline,
): com.inversioncoach.app.recording.OverlayTimeline = timeline.copy(
    frames = timeline.frames.map { frame ->
        frame.copy(
            landmarks = frame.landmarks.toList(),
            smoothedLandmarks = frame.smoothedLandmarks.toList(),
            skeletonLines = frame.skeletonLines.toList(),
            alignmentAngles = frame.alignmentAngles.toMap(),
            visibilityFlags = frame.visibilityFlags.toMap(),
        )
    },
)

private const val RELATIVE_TIMESTAMP_FALLBACK_GAP_MS = 60_000L

internal fun normalizeFrozenOverlayTimelineToRelative(
    timeline: com.inversioncoach.app.recording.OverlayTimeline,
): com.inversioncoach.app.recording.OverlayTimeline {
    val normalizedFrames = timeline.frames.map { frame ->
        val rawRelativeTs = frame.relativeTimestampMs.coerceAtLeast(0L)
        val derivedRelativeTs = (frame.timestampMs - timeline.startedAtMs).coerceAtLeast(0L)
        val relativeDelta = kotlin.math.abs(rawRelativeTs - derivedRelativeTs)
        val shouldFallbackToDerivedRelative = timeline.startedAtMs > 0L &&
            frame.timestampMs >= timeline.startedAtMs &&
            relativeDelta >= RELATIVE_TIMESTAMP_FALLBACK_GAP_MS
        val relativeTs = if (shouldFallbackToDerivedRelative) derivedRelativeTs else rawRelativeTs
        frame.copy(
            relativeTimestampMs = relativeTs,
            timestampMs = relativeTs,
            absoluteVideoPtsUs = frame.absoluteVideoPtsUs ?: (relativeTs * 1_000L),
        )
    }.sortedBy { it.relativeTimestampMs }
    return timeline.copy(
        startedAtMs = 0L,
        frames = normalizedFrames,
    )
}

internal fun clampOverlayTimelineToDuration(
    timeline: com.inversioncoach.app.recording.OverlayTimeline,
    maxDurationMs: Long,
): com.inversioncoach.app.recording.OverlayTimeline {
    if (maxDurationMs <= 0L) return timeline
    if (timeline.frames.isEmpty()) return timeline
    val clampedFrames = timeline.frames.filter { it.relativeTimestampMs <= maxDurationMs }
    return if (clampedFrames.size == timeline.frames.size) timeline else timeline.copy(frames = clampedFrames)
}


private fun com.inversioncoach.app.recording.OverlayTimeline.isRelativeNormalized(): Boolean {
    if (startedAtMs != 0L) return false
    for (frame in frames) {
        if (frame.relativeTimestampMs < 0L) return false
        if (frame.timestampMs != frame.relativeTimestampMs) return false
    }
    return true
}

internal fun prepareExportSnapshotInputs(
    snapshot: ExportSnapshot,
    overlayCaptureFrozen: Boolean,
    hasReadableRaw: Boolean,
    toleranceMs: Long,
    liveOverlayFrameCountAtFreeze: Int,
): ExportPreflightResult {
    fun result(
        outSnapshot: ExportSnapshot,
        fatalReason: String?,
        clampApplied: Boolean,
        durationMismatchMs: Long,
    ) = ExportPreflightResult(
        snapshot = outSnapshot,
        fatalReason = fatalReason,
        clampApplied = clampApplied,
        durationMismatchMs = durationMismatchMs,
        liveOverlayFrameCountAtFreeze = liveOverlayFrameCountAtFreeze,
        frozenOverlayFrameCount = outSnapshot.overlayFrameCount,
        overlayFramesIgnoredAfterFreeze = (liveOverlayFrameCountAtFreeze - outSnapshot.overlayFrameCount).coerceAtLeast(0),
        countBeforeTrim = snapshot.overlayTimeline.frames.size,
        countAfterTrim = outSnapshot.overlayFrameCount,
        firstFrozenTsBeforeNormalization = snapshot.overlayTimeline.frames.firstOrNull()?.timestampMs,
        lastFrozenTsBeforeNormalization = snapshot.overlayTimeline.frames.lastOrNull()?.timestampMs,
        firstFrozenTsAfterNormalization = outSnapshot.overlayTimeline.frames.firstOrNull()?.timestampMs,
        lastFrozenTsAfterNormalization = outSnapshot.overlayTimeline.frames.lastOrNull()?.timestampMs,
    )

    if (snapshot.rawUri.isBlank()) return result(snapshot, "EXPORT_INPUT_VALIDATION_FAILED_RAW_URI_EMPTY", false, 0L)
    if (!overlayCaptureFrozen) return result(snapshot, "EXPORT_INPUT_VALIDATION_FAILED_SNAPSHOT_NOT_FROZEN", false, 0L)
    if (!snapshot.overlayTimeline.isMonotonic()) return result(snapshot, "EXPORT_INPUT_VALIDATION_FAILED_TIMESTAMPS", false, 0L)

    if (!snapshot.overlayTimeline.isRelativeNormalized()) {
        return result(snapshot, "EXPORT_INPUT_VALIDATION_FAILED_TIMESTAMPS_NOT_NORMALIZED", false, 0L)
    }

    val overlayEndTimestamp = snapshot.overlayTimeline.frames.lastOrNull()?.relativeTimestampMs ?: 0L
    val usableDurationMs = when {
        snapshot.rawDurationMs > 0L -> snapshot.rawDurationMs
        overlayEndTimestamp > 0L -> overlayEndTimestamp
        else -> 0L
    }
    if (usableDurationMs <= 0L) {
        return if (hasReadableRaw) {
            result(snapshot, "EXPORT_INPUT_VALIDATION_FAILED_DURATION_UNAVAILABLE_READABLE_RAW", false, 0L)
        } else {
            result(snapshot, "EXPORT_INPUT_VALIDATION_FAILED_RAW_MISSING_AND_DURATION_UNAVAILABLE", false, 0L)
        }
    }

    val durationSource = when {
        snapshot.rawDurationMs > 0L -> snapshot.rawDurationSource
        overlayEndTimestamp > 0L -> "overlay_timeline_fallback"
        else -> snapshot.rawDurationSource
    }
    val durationMismatchMs = if (usableDurationMs > 0L) {
        (overlayEndTimestamp - (usableDurationMs + toleranceMs)).coerceAtLeast(0L)
    } else {
        0L
    }
    val clampedTimeline = if (durationMismatchMs > 0L && usableDurationMs > 0L) {
        clampOverlayTimelineToDuration(snapshot.overlayTimeline, usableDurationMs)
    } else {
        snapshot.overlayTimeline
    }
    if (snapshot.overlayTimeline.frames.isNotEmpty() && clampedTimeline.frames.isEmpty()) {
        return result(snapshot, "EXPORT_INPUT_VALIDATION_FAILED_ALL_FRAMES_OUT_OF_RANGE_AFTER_NORMALIZATION", true, durationMismatchMs)
    }

    return result(
        outSnapshot = snapshot.copy(
            rawDurationMs = usableDurationMs,
            rawDurationSource = durationSource,
            overlayTimeline = clampedTimeline,
            overlayFrameCount = clampedTimeline.frames.size,
        ),
        fatalReason = null,
        clampApplied = durationMismatchMs > 0L,
        durationMismatchMs = durationMismatchMs,
    )
}

internal data class ExportSnapshot(
    val sessionId: Long,
    val stopTimestampMs: Long,
    val rawUri: String,
    val rawDurationMs: Long,
    val rawDurationSource: String,
    val overlayTimeline: com.inversioncoach.app.recording.OverlayTimeline,
    val overlayTimelineUri: String?,
    val overlayFrameCount: Int,
)

internal data class RawDurationAttempt(
    val attemptIndex: Int,
    val uri: String?,
    val fileExists: Boolean,
    val fileSizeBytes: Long,
    val source: String,
    val durationMs: Long,
)

internal enum class FinalizeCallbackAction {
    IGNORED_BLANK,
    ACCEPTED_FIRST,
    UPGRADED_TO_PERSISTED,
    IGNORED_DUPLICATE,
    IGNORED_REDUNDANT,
}

internal data class FinalizeCallbackAcceptance(
    val action: FinalizeCallbackAction,
    val acceptedUri: String?,
)

internal fun evaluateFinalizeCallbackAcceptance(existingAcceptedUri: String?, incomingUri: String?): FinalizeCallbackAcceptance {
    val normalizedExisting = existingAcceptedUri?.takeIf { it.isNotBlank() }
    val normalizedIncoming = incomingUri?.takeIf { it.isNotBlank() }
    if (normalizedIncoming.isNullOrBlank()) {
        return FinalizeCallbackAcceptance(FinalizeCallbackAction.IGNORED_BLANK, normalizedExisting)
    }
    if (normalizedExisting.isNullOrBlank()) {
        return FinalizeCallbackAcceptance(FinalizeCallbackAction.ACCEPTED_FIRST, normalizedIncoming)
    }
    if (normalizedIncoming == normalizedExisting) {
        return FinalizeCallbackAcceptance(FinalizeCallbackAction.IGNORED_DUPLICATE, normalizedExisting)
    }
    if (!isPersistedSessionRawBlobUri(normalizedExisting) && isPersistedSessionRawBlobUri(normalizedIncoming)) {
        return FinalizeCallbackAcceptance(FinalizeCallbackAction.UPGRADED_TO_PERSISTED, normalizedIncoming)
    }
    return FinalizeCallbackAcceptance(FinalizeCallbackAction.IGNORED_REDUNDANT, normalizedExisting)
}

internal fun isPersistedSessionRawBlobUri(uri: String): Boolean {
    val path = runCatching { android.net.Uri.parse(uri).path }.getOrNull().orEmpty()
    return path.endsWith("/${SessionBlobStorage.RAW_MASTER_FILE_NAME}") || path.endsWith("/${SessionBlobStorage.RAW_FINAL_FILE_NAME}")
}

internal enum class RecorderFinalizeFlowOutcome {
    START_FINALIZATION,
    IGNORE_CALLBACK,
    IGNORE_UPGRADE_INFLIGHT,
}

internal fun resolveRecorderFinalizeFlowOutcome(
    action: FinalizeCallbackAction,
    isFinalizationInFlight: Boolean,
): RecorderFinalizeFlowOutcome = when (action) {
    FinalizeCallbackAction.ACCEPTED_FIRST -> RecorderFinalizeFlowOutcome.START_FINALIZATION
    FinalizeCallbackAction.UPGRADED_TO_PERSISTED -> if (isFinalizationInFlight) RecorderFinalizeFlowOutcome.IGNORE_UPGRADE_INFLIGHT else RecorderFinalizeFlowOutcome.START_FINALIZATION
    FinalizeCallbackAction.IGNORED_BLANK,
    FinalizeCallbackAction.IGNORED_DUPLICATE,
    FinalizeCallbackAction.IGNORED_REDUNDANT,
    -> RecorderFinalizeFlowOutcome.IGNORE_CALLBACK
}

internal data class RawDurationResolution(
    val durationMs: Long,
    val source: String,
)

internal val RAW_DURATION_RETRY_BACKOFF_MS = listOf(200L, 350L, 550L, 800L, 1_100L)

internal fun resolveReadableFile(uri: String?): java.io.File? {
    val path = runCatching { android.net.Uri.parse(uri).path }.getOrNull() ?: return null
    val file = java.io.File(path)
    return if (file.exists() && file.canRead()) file else null
}

internal suspend fun resolveRawDurationWithRetries(
    rawUri: String?,
    fileExists: Boolean,
    fileSizeBytes: Long,
    sessionElapsedMs: Long,
    overlayMaxTimestampMs: Long,
    maxRetries: Int = 3,
    readMetadataDuration: (String?) -> Long = { uri -> MediaVerificationHelper.verify(uri).durationMs ?: 0L },
    readExtractorDuration: (String?) -> Long = ::readDurationViaExtractor,
    onAttempt: (RawDurationAttempt) -> Unit = {},
    onRetry: suspend (retryIndex: Int, waitMs: Long) -> Unit = { _, _ -> },
): RawDurationResolution {
    val totalAttempts = (maxRetries + 1).coerceAtLeast(1)
    for (attempt in 1..totalAttempts) {
        val metadataDuration = readMetadataDuration(rawUri).coerceAtLeast(0L)
        onAttempt(RawDurationAttempt(attempt, rawUri, fileExists, fileSizeBytes, "metadata_retriever", metadataDuration))
        if (metadataDuration > 0L) return RawDurationResolution(metadataDuration, "metadata_retriever")

        val extractorDuration = readExtractorDuration(rawUri).coerceAtLeast(0L)
        onAttempt(RawDurationAttempt(attempt, rawUri, fileExists, fileSizeBytes, "media_extractor", extractorDuration))
        if (extractorDuration > 0L) return RawDurationResolution(extractorDuration, "media_extractor")

        if (attempt < totalAttempts) {
            val waitMs = RAW_DURATION_RETRY_BACKOFF_MS.getOrElse(attempt - 1) {
                RAW_DURATION_RETRY_BACKOFF_MS.lastOrNull() ?: 250L
            }
            onRetry(attempt, waitMs)
            delay(waitMs)
        }
    }

    val sessionDuration = sessionElapsedMs.coerceAtLeast(0L)
    onAttempt(RawDurationAttempt(totalAttempts + 1, rawUri, fileExists, fileSizeBytes, "session_elapsed", sessionDuration))
    if (sessionDuration > 0L) return RawDurationResolution(sessionDuration, "session_elapsed")

    val overlayDuration = overlayMaxTimestampMs.coerceAtLeast(0L)
    onAttempt(RawDurationAttempt(totalAttempts + 1, rawUri, fileExists, fileSizeBytes, "overlay_timeline", overlayDuration))
    if (overlayDuration > 0L) return RawDurationResolution(overlayDuration, "overlay_timeline")

    return RawDurationResolution(durationMs = 0L, source = "none")
}

internal fun readDurationViaExtractor(uri: String?): Long {
    val file = resolveReadableFile(uri) ?: return 0L
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(file.absolutePath)
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!mime.startsWith("video/")) continue
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                return (durationUs / 1000L).coerceAtLeast(0L)
            }
        }
        0L
    } catch (_: Throwable) {
        0L
    } finally {
        runCatching { extractor.release() }
    }
}

private fun com.inversioncoach.app.recording.OverlayTimeline.isMonotonic(): Boolean {
    var last = Long.MIN_VALUE
    for (frame in frames) {
        if (frame.timestampMs < last) return false
        last = frame.timestampMs
    }
    return true
}
internal data class RawPersistVerification(
    val isPersisted: Boolean,
    val persistedUri: String?,
    val isReplayPlayable: Boolean,
    val inspection: ReplayInspectionResult?,
)

internal data class FinalizeSourceReadiness(
    val inspection: ReplayInspectionResult,
    val sizeStable: Boolean,
    val durationStable: Boolean,
) {
    val isReadyForPersist: Boolean
        get() = inspection.isDecodable && sizeStable && durationStable
}

internal fun captureSpanMs(startMs: Long, stopMs: Long): Long = (stopMs - startMs).coerceAtLeast(0L)

internal fun persistedDurationWithinTolerance(
    captureSpanMs: Long,
    persistedDurationMs: Long,
    toleranceMs: Long,
): Boolean = kotlin.math.abs(captureSpanMs.coerceAtLeast(0L) - persistedDurationMs.coerceAtLeast(0L)) <= toleranceMs.coerceAtLeast(0L)

internal fun RawPersistVerification.inspectionSummary(): String {
    val details = inspection ?: return "none"
    return "exists=${details.fileExists},size=${details.fileSizeBytes},lastModifiedMs=${details.lastModifiedEpochMs ?: -1},durationMs=${details.durationMs ?: -1},trackCount=${details.trackCount},width=${details.width ?: -1},height=${details.height ?: -1},hasVideoTrack=${details.hasVideoTrack},firstFrameDecoded=${details.firstFrameDecoded},error=${details.errorDetail.orEmpty()}"
}

internal fun classifyRawReplayFailure(verification: RawPersistVerification): String? {
    if (verification.isReplayPlayable) return null
    if (verification.persistedUri.isNullOrBlank()) return AnnotatedExportFailureReason.RAW_SAVE_FAILED.name
    val inspection = verification.inspection
    if (inspection?.hasVideoTrack == false) return AnnotatedExportFailureReason.RAW_MEDIA_CORRUPT.name
    if (inspection?.durationMs == null || inspection.durationMs <= 0L || inspection.firstFrameDecoded == false) {
        return AnnotatedExportFailureReason.SOURCE_VIDEO_UNREADABLE.name
    }
    return AnnotatedExportFailureReason.RAW_REPLAY_INVALID.name
}

internal fun verifyPersistedRawVideoUris(
    candidateUris: List<String?>,
    metadataVerifier: (String) -> MediaVerificationResult,
    replayInspector: (String) -> ReplayInspectionResult = { MediaVerificationHelper.inspectReplay(it) },
    requireReadableMetadataForPersistence: Boolean = false,
): RawPersistVerification {
    val normalizedCandidates = candidateUris
        .mapNotNull { it?.takeIf(String::isNotBlank) }
        .distinct()

    for (candidate in normalizedCandidates) {
        val path = runCatching { android.net.Uri.parse(candidate).path }.getOrNull() ?: continue
        val file = java.io.File(path)
        if (!file.exists() || !file.canRead() || file.length() <= 0L) continue

        val verification = metadataVerifier(candidate)
        val inspection = replayInspector(candidate)
        if (!verification.isValid || !inspection.isDecodable) {
            SessionDiagnostics.log(
                "raw_persist_replay_probe_failed uri=$candidate failure=${verification.failureReason};exists=${inspection.fileExists};size=${inspection.fileSizeBytes};lastModifiedMs=${inspection.lastModifiedEpochMs};durationMs=${inspection.durationMs};trackCount=${inspection.trackCount};width=${inspection.width};height=${inspection.height};hasVideoTrack=${inspection.hasVideoTrack};firstFrameDecoded=${inspection.firstFrameDecoded};detail=${inspection.errorDetail.orEmpty()}",
            )
            if (requireReadableMetadataForPersistence) continue
        }
        return RawPersistVerification(
            isPersisted = true,
            persistedUri = candidate,
            isReplayPlayable = verification.isValid && inspection.isDecodable,
            inspection = inspection,
        )
    }
    return RawPersistVerification(isPersisted = false, persistedUri = null, isReplayPlayable = false, inspection = null)
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
