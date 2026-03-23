package com.inversioncoach.app.ui.live

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.ScaleType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.inversioncoach.app.camera.CameraSessionManager
import com.inversioncoach.app.coaching.VoiceCoach
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.model.SessionStartupState
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.motion.DrillCatalog
import com.inversioncoach.app.motion.RepMode
import com.inversioncoach.app.overlay.FreestyleViewMode
import com.inversioncoach.app.overlay.OverlayRenderer
import com.inversioncoach.app.pose.PoseAnalyzer
import com.inversioncoach.app.recording.SessionRecorder
import com.inversioncoach.app.recording.AnnotatedExportPipeline
import com.inversioncoach.app.recording.AnnotatedVideoCompositor
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import java.util.concurrent.Executors

private const val TAG = "LiveCoachingScreen"
private val overlayPanelShape = RoundedCornerShape(14.dp)

@Composable
fun LiveCoachingScreen(drillType: DrillType, options: LiveSessionOptions, onStop: (SessionStopResult) -> Unit) {
    val context = LocalContext.current
    val isDebuggableApp = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    val hostView = LocalView.current
    val repository = remember { ServiceLocator.repository(context) }

    val trackingMode = remember(drillType) { DrillCatalog.byType(drillType).repMode }

    val vm = remember {
        LiveCoachingViewModel(
            drillType = drillType,
            metricsEngine = ServiceLocator.metricsEngine(),
            cueEngine = ServiceLocator.cueEngine(),
            repository = repository,
            options = options,
            annotatedExportPipeline = AnnotatedExportPipeline(
                repository = repository,
                compositor = AnnotatedVideoCompositor(context.applicationContext),
            ),
        )
    }
    val uiState by vm.uiState.collectAsState()
    val spokenCue by vm.spokenCue.collectAsState()
    val smoothed by vm.smoothedFrame.collectAsState()
    val settings by repository.observeSettings().collectAsState(initial = UserSettings())
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val voiceCoach = remember(context) { VoiceCoach(context) }
    val sessionRecorder = remember(context) { SessionRecorder(context) }
    val currentSessionTitle by rememberUpdatedState(newValue = vm.sessionTitle)
    val currentUiState by rememberUpdatedState(newValue = uiState)
    val showDetailedStats = rememberSaveable { mutableStateOf(false) }
    val diagnosticsExpanded = rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val freestyleViewLabel = remember(smoothed, uiState.sessionMode) {
        if (uiState.sessionMode != SessionMode.FREESTYLE) {
            null
        } else {
            val joints = smoothed?.joints.orEmpty()
            if (joints.isEmpty()) {
                "Detecting View"
            } else {
                when (uiState.freestyleViewMode) {
                    FreestyleViewMode.UNKNOWN -> "Detecting View"
                    FreestyleViewMode.FRONT -> "Front View"
                    FreestyleViewMode.BACK -> "Back View"
                    FreestyleViewMode.LEFT_PROFILE -> "Left Profile"
                    FreestyleViewMode.RIGHT_PROFILE -> "Right Profile"
                }
            }
        }
    }

    fun stopRecordingsAndPersist() {
        sessionRecorder.stopRecording()
        vm.onRecordingFinalized(sessionRecorder.fallbackOutputUri())
    }

    val cameraManager = remember { CameraSessionManager(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentSettings by rememberUpdatedState(newValue = settings)
    val sessionDurationMs by produceState(initialValue = 0L, uiState.isRecording) {
        while (uiState.isRecording) {
            value = computeSessionDurationMs(vm.sessionStartTimestampMs, System.currentTimeMillis())
            kotlinx.coroutines.delay(1000L)
        }
        value = computeSessionDurationMs(vm.sessionStartTimestampMs, System.currentTimeMillis())
    }
    val analyzer = remember {
        PoseAnalyzer(
            onPoseFrame = { vm.onPoseFrame(it, currentSettings) },
            onAnalyzerWarning = vm::onAnalyzerWarning,
            backgroundExecutor = analyzerExecutor,
        )
    }

    DisposableEffect(hostView) {
        val previousKeepScreenOn = hostView.keepScreenOn
        hostView.keepScreenOn = true

        onDispose {
            hostView.keepScreenOn = previousKeepScreenOn
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onCameraPermissionChanged(granted)
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        vm.onCameraPermissionChanged(granted)
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (currentUiState.isRecording) {
                stopRecordingsAndPersist()
                vm.setRecording(false)
            }
            vm.cancelStartup()
            vm.finalizeSessionSilentlyIfActive()
            analyzer.close()
            analyzerExecutor.shutdown()
            cameraManager.release()
            voiceCoach.shutdown()
        }
    }

    LaunchedEffect(spokenCue?.generatedAtMs, settings.audioVolume, options.voiceEnabled) {
        val cue = spokenCue ?: return@LaunchedEffect
        if (!options.voiceEnabled) return@LaunchedEffect
        if (settings.audioVolume <= 0f) return@LaunchedEffect
        if (cue.generatedAtMs != uiState.currentCueGeneratedAtMs || cue.id != uiState.currentCueId) return@LaunchedEffect
        voiceCoach.speak(cue, volume = settings.audioVolume)
    }

    LaunchedEffect(uiState.cameraPermissionGranted, uiState.cameraReady, settings.startupCountdownSeconds) {
        if (!uiState.cameraPermissionGranted || !uiState.cameraReady) return@LaunchedEffect
        if (uiState.startupState != SessionStartupState.IDLE) return@LaunchedEffect
        vm.launchStartupCountdown(settings.startupCountdownSeconds)
    }

    LaunchedEffect(uiState.startupState, uiState.sessionCountdownRemainingSeconds, options.voiceEnabled, settings.audioVolume) {
        if (!options.voiceEnabled || settings.audioVolume <= 0f) return@LaunchedEffect
        if (uiState.startupState != SessionStartupState.COUNTDOWN) return@LaunchedEffect
        val remaining = uiState.sessionCountdownRemainingSeconds ?: return@LaunchedEffect
        if (remaining <= 0) return@LaunchedEffect
        voiceCoach.speak(
            cue = com.inversioncoach.app.model.CoachingCue(
                id = "session_countdown_$remaining",
                text = remaining.toString(),
                severity = 0,
                generatedAtMs = System.currentTimeMillis(),
            ),
            volume = settings.audioVolume,
        )
    }

    LaunchedEffect(uiState.startupState, options.voiceEnabled, settings.audioVolume) {
        if (uiState.startupState != SessionStartupState.ACTIVE) return@LaunchedEffect
        if (!options.voiceEnabled || settings.audioVolume <= 0f) return@LaunchedEffect
        voiceCoach.speak(
            cue = com.inversioncoach.app.model.CoachingCue(
                id = "session_initiated",
                text = "Session initiated.",
                severity = 0,
                generatedAtMs = System.currentTimeMillis(),
            ),
            volume = settings.audioVolume,
        )
    }

    LaunchedEffect(
        options.recordingEnabled,
        uiState.cameraPermissionGranted,
        uiState.cameraReady,
        uiState.isRecording,
        uiState.startupState,
    ) {
        if (!options.recordingEnabled) return@LaunchedEffect
        if (!uiState.cameraPermissionGranted || !uiState.cameraReady || uiState.isRecording) return@LaunchedEffect
        if (uiState.startupState != SessionStartupState.ACTIVE) return@LaunchedEffect

        val capture = cameraManager.videoCapture()
        if (capture == null) {
            vm.onAnalyzerWarning("Recording unavailable until camera is ready")
            return@LaunchedEffect
        }
        sessionRecorder.startRecording(
            capture = capture,
            title = currentSessionTitle,
            onEvent = { event: VideoRecordEvent ->
                if (event is VideoRecordEvent.Finalize) {
                    if (event.hasError()) {
                        Log.e(TAG, "Recording finalize error code=${event.error}")
                        vm.onAnalyzerWarning("Recording failed to finalize (code ${event.error})")
                        vm.onRecordingFinalized(sessionRecorder.fallbackOutputUri())
                    } else {
                        vm.onRecordingFinalized(event.outputResults.outputUri.toString())
                    }
                }
            },
        )
        vm.setRecording(true)
    }

    Box(Modifier.fillMaxSize()) {
        if (uiState.cameraPermissionGranted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = ScaleType.FIT_CENTER
                        post {
                            cameraManager.bind(lifecycleOwner, this, analyzer, options.zoomOutCamera) { ready, error ->
                                vm.onCameraReady(ready, error)
                            }
                        }
                    }
                },
            )
            if (options.showSkeletonOverlay) {
                OverlayRenderer(
                    frame = smoothed,
                    drillType = drillType,
                    sessionMode = uiState.sessionMode,
                    modifier = Modifier.fillMaxSize(),
                    showIdealLine = options.showIdealLine,
                    showDebugOverlay = uiState.showDebugOverlay,
                    debugMetrics = uiState.debugMetrics,
                    debugAngles = uiState.debugAngles,
                    currentPhase = uiState.currentPhase,
                    activeFault = uiState.activeFault,
                    cueText = if (uiState.sessionMode == SessionMode.FREESTYLE) "" else uiState.currentCue,
                    drillCameraSide = options.drillCameraSide,
                    freestyleViewMode = uiState.freestyleViewMode,
                )
            }
        }

        TopHud(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            title = "${freestyleViewLabel ?: "Side View"} • ${drillType.displayName}",
            sessionDurationMs = sessionDurationMs,
            trackingLabel = if (uiState.cameraReady) "Tracking ${uiState.smoothedAlignmentScore}%" else "Calibrating",
            phase = uiState.currentPhase.uppercase(),
            countdownRemainingSeconds = uiState.sessionCountdownRemainingSeconds,
            warningMessage = when {
                !uiState.cameraPermissionGranted -> "Camera permission required for live coaching."
                uiState.cameraPermissionGranted && !uiState.cameraReady -> "Starting camera..."
                else -> uiState.warningMessage
            },
            errorMessage = uiState.errorMessage,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            vm.activeSessionId?.let { sid ->
                val events = SessionDiagnostics.eventsForSession(sid)
                val last = events.lastOrNull()
                DiagnosticsPanel(
                    diagnosticsExpanded = diagnosticsExpanded.value,
                    onToggleExpanded = { diagnosticsExpanded.value = !diagnosticsExpanded.value },
                    onToggleDetailedStats = { showDetailedStats.value = !showDetailedStats.value },
                    showDetailedStats = showDetailedStats.value,
                    stageLabel = "${last?.stage?.name ?: "SESSION_START"} • ${last?.status?.name ?: "STARTED"}",
                    eventMessage = last?.message ?: "No diagnostics yet",
                    startedAt = formatSessionDateTime(vm.sessionStartTimestampMs),
                    cameraSideLabel = options.drillCameraSide.name.lowercase().replaceFirstChar { it.uppercase() },
                    cueText = uiState.currentCue.ifBlank { "Awaiting stable frame..." },
                    confidenceText = "${(uiState.confidence * 100).toInt()}% • Raw ${uiState.alignmentScore}",
                    holdSummary = "Hold ${formatSessionDuration(uiState.totalAlignedDurationMs)} (${(uiState.alignmentRate * 100).toInt()}%)",
                    streakSummary = "Streak ${formatSessionDuration(uiState.currentAlignedStreakMs)} • Best ${formatSessionDuration(uiState.bestAlignedStreakMs)}",
                    holdMetrics = "Avg align ${uiState.averageAlignmentScore} • Stability ${uiState.stabilityScore}",
                    repsSummary = "Reps ✅${uiState.acceptedReps} ❌${uiState.rejectedReps} • Raw ${uiState.rawRepCount}",
                    repQualitySummary = "Rep quality avg ${uiState.averageRepQuality} • Best ${uiState.bestRepScore} • Last ${uiState.lastRepScore}",
                    activeFault = uiState.activeFault,
                    showHoldBasedMetrics = uiState.sessionMode == SessionMode.FREESTYLE || trackingMode == RepMode.HOLD_BASED,
                    showDrillSpecificDetails = uiState.sessionMode != SessionMode.FREESTYLE,
                    onCopyDiagnostics = {
                        val report = SessionDiagnostics.buildReport(session = null, sessionId = sid)
                        clipboardManager.setText(AnnotatedString(report))
                    },
                    diagnosticsReport = if (diagnosticsExpanded.value) SessionDiagnostics.buildReport(session = null, sessionId = sid) else null,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                FilledTonalButton(onClick = {
                    if (uiState.isRecording) {
                        stopRecordingsAndPersist()
                        vm.setRecording(false)
                        vm.stopSession(onStop)
                    } else {
                        vm.cancelStartup()
                        vm.stopSession(onStop)
                    }
                }) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun TopHud(
    modifier: Modifier,
    title: String,
    sessionDurationMs: Long,
    trackingLabel: String,
    phase: String,
    countdownRemainingSeconds: Int?,
    warningMessage: String?,
    errorMessage: String?,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f), overlayPanelShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatSessionDuration(sessionDurationMs), color = Color.White, fontSize = 13.sp)
            }
            Text(
                "$trackingLabel • $phase",
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(min = 96.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        countdownRemainingSeconds?.takeIf { it > 0 }?.let { remaining ->
            Text("Session starts in ${remaining}s", color = Color.Yellow, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        warningMessage?.let { Text(it, color = Color.Yellow, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        errorMessage?.let { Text(it, color = Color.Red, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun DiagnosticsPanel(
    diagnosticsExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleDetailedStats: () -> Unit,
    showDetailedStats: Boolean,
    stageLabel: String,
    eventMessage: String,
    startedAt: String,
    cameraSideLabel: String,
    cueText: String,
    confidenceText: String,
    holdSummary: String,
    streakSummary: String,
    holdMetrics: String,
    repsSummary: String,
    repQualitySummary: String,
    activeFault: String,
    showHoldBasedMetrics: Boolean,
    showDrillSpecificDetails: Boolean,
    onCopyDiagnostics: () -> Unit,
    diagnosticsReport: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.54f), overlayPanelShape)
            .clickable { onToggleExpanded() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Diagnostics • $stageLabel", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!diagnosticsExpanded) {
            Text(
                eventMessage,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            return
        }

        Text("Stage: $stageLabel", color = Color.White, fontSize = 12.sp)
        Text("Event: $eventMessage", color = Color.White, fontSize = 12.sp)
        Text("Started: $startedAt", color = Color.White, fontSize = 12.sp)
        if (showDrillSpecificDetails) {
            Text("Camera Side: $cameraSideLabel", color = Color.White, fontSize = 12.sp)
            Text("Cue: $cueText", color = Color.White, fontSize = 12.sp)
        }
        Text("Confidence: $confidenceText", color = Color.White, fontSize = 12.sp)
        if (showHoldBasedMetrics) {
            Text(holdSummary, color = Color.White, fontSize = 12.sp)
            Text(streakSummary, color = Color.White, fontSize = 12.sp)
            Text(holdMetrics, color = Color.White, fontSize = 12.sp)
        } else {
            Text(repsSummary, color = Color.White, fontSize = 12.sp)
            Text(repQualitySummary, color = Color.White, fontSize = 12.sp)
        }
        if (showDrillSpecificDetails && activeFault.isNotBlank()) {
            Text("Active fault: $activeFault", color = Color.Yellow, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onToggleDetailedStats) {
                Text(if (showDetailedStats) "Hide metrics" else "Show metrics", color = Color.White, fontSize = 12.sp)
            }
            TextButton(onClick = onCopyDiagnostics) {
                Text("Export technical log", color = Color.White, fontSize = 12.sp)
            }
            TextButton(onClick = onToggleExpanded) {
                Text("Collapse", color = Color.White, fontSize = 12.sp)
            }
        }
        if (showDetailedStats) {
            diagnosticsReport?.let { report ->
                Text(
                    report,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
