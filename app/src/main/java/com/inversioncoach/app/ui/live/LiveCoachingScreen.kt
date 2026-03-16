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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.inversioncoach.app.camera.CameraSessionManager
import com.inversioncoach.app.coaching.VoiceCoach
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.motion.DrillCatalog
import com.inversioncoach.app.motion.RepMode
import com.inversioncoach.app.overlay.FreestyleOrientationClassifier
import com.inversioncoach.app.overlay.FreestyleViewMode
import com.inversioncoach.app.overlay.OverlayRenderer
import com.inversioncoach.app.pose.PoseAnalyzer
import com.inversioncoach.app.recording.AnnotatedExportPipeline
import com.inversioncoach.app.recording.AnnotatedVideoCompositor
import com.inversioncoach.app.recording.SessionRecorder
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import java.util.concurrent.Executors

private const val TAG = "LiveCoachingScreen"

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
                compositor = AnnotatedVideoCompositor(context),
                debugValidationEnabled = isDebuggableApp,
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
    val showDetailedStats = rememberSaveable { mutableStateOf(false) }

    val freestyleOrientationClassifier = remember { FreestyleOrientationClassifier() }
    val freestyleViewLabel = remember(smoothed, uiState.sessionMode) {
        if (uiState.sessionMode != SessionMode.FREESTYLE) {
            null
        } else {
            val joints = smoothed?.joints.orEmpty()
            if (joints.isEmpty()) {
                "Detecting View"
            } else {
                when (freestyleOrientationClassifier.classify(joints)) {
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
            if (uiState.isRecording) {
                stopRecordingsAndPersist()
                vm.setRecording(false)
            }
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

    LaunchedEffect(options.recordingEnabled, uiState.cameraPermissionGranted, uiState.cameraReady, uiState.isRecording) {
        if (!options.recordingEnabled) return@LaunchedEffect
        if (!uiState.cameraPermissionGranted || !uiState.cameraReady || uiState.isRecording) return@LaunchedEffect

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
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.58f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${freestyleViewLabel ?: "Side View"} • ${drillType.displayName}", color = Color.White, fontSize = 18.sp)
                TextButton(onClick = { showDetailedStats.value = !showDetailedStats.value }) {
                    Text(if (showDetailedStats.value) "Less" else "More", color = Color.White, fontSize = 12.sp)
                }
            }
            Text("Time ${formatSessionDuration(sessionDurationMs)}", color = Color.White, fontSize = 14.sp)
            Text("Align ${uiState.smoothedAlignmentScore}% • ${uiState.currentPhase.uppercase()}", color = Color.White, fontSize = 14.sp)

            if (uiState.sessionMode != SessionMode.FREESTYLE) {
                Text("Camera Side: ${options.drillCameraSide.name.lowercase().replaceFirstChar { it.uppercase() }}", color = Color.White, fontSize = 13.sp)
            }

            if (showDetailedStats.value) {
                Text("Started: ${formatSessionDateTime(vm.sessionStartTimestampMs)}", color = Color.White, fontSize = 13.sp)
                if (uiState.sessionMode != SessionMode.FREESTYLE) {
                    Text("Cue: ${uiState.currentCue.ifBlank { "Awaiting stable frame..." }}", color = Color.White, fontSize = 13.sp)
                }
                Text("Confidence: ${(uiState.confidence * 100).toInt()}% • Raw ${uiState.alignmentScore}", color = Color.White, fontSize = 13.sp)
                if (uiState.sessionMode == SessionMode.FREESTYLE || trackingMode == RepMode.HOLD_BASED) {
                    Text("Hold ${formatSessionDuration(uiState.totalAlignedDurationMs)} (${(uiState.alignmentRate * 100).toInt()}%)", color = Color.White, fontSize = 13.sp)
                    Text("Streak ${formatSessionDuration(uiState.currentAlignedStreakMs)} • Best ${formatSessionDuration(uiState.bestAlignedStreakMs)}", color = Color.White, fontSize = 13.sp)
                    Text("Avg align ${uiState.averageAlignmentScore} • Stability ${uiState.stabilityScore}", color = Color.White, fontSize = 13.sp)
                } else {
                    Text("Reps ✅${uiState.acceptedReps} ❌${uiState.rejectedReps} • Raw ${uiState.rawRepCount}", color = Color.White, fontSize = 13.sp)
                    Text("Rep quality avg ${uiState.averageRepQuality} • Best ${uiState.bestRepScore} • Last ${uiState.lastRepScore}", color = Color.White, fontSize = 13.sp)
                }
                if (uiState.sessionMode != SessionMode.FREESTYLE && uiState.activeFault.isNotBlank()) Text("Active fault: ${uiState.activeFault}", color = Color.Yellow, fontSize = 13.sp)
            }
            if (!uiState.cameraPermissionGranted) {
                Text("Camera permission required for live coaching.", color = Color.Yellow, fontSize = 13.sp)
            }
            if (uiState.cameraPermissionGranted && !uiState.cameraReady) {
                Text("Starting camera...", color = Color.Yellow, fontSize = 13.sp)
            }
            uiState.warningMessage?.let { Text(it, color = Color.Yellow, fontSize = 13.sp) }
            uiState.errorMessage?.let { Text(it, color = Color.Red, fontSize = 13.sp) }
        }



        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            FilledTonalButton(onClick = {
                if (uiState.isRecording) {
                    stopRecordingsAndPersist()
                    vm.setRecording(false)
                    vm.stopSession(onStop)
                } else {
                    vm.stopSession(onStop)
                }
            }) { Text("Stop") }
        }
    }
}
