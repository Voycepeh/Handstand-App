package com.inversioncoach.app.ui.live

import android.Manifest
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.inversioncoach.app.camera.CameraSessionManager
import com.inversioncoach.app.coaching.VoiceCoach
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.overlay.OverlayRenderer
import com.inversioncoach.app.pose.PoseAnalyzer
import com.inversioncoach.app.recording.SessionRecorder
import com.inversioncoach.app.storage.ServiceLocator
import java.util.concurrent.Executors

private const val TAG = "LiveCoachingScreen"

@Composable
fun LiveCoachingScreen(drillType: DrillType, options: LiveSessionOptions, onStop: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }

    val vm = remember {
        LiveCoachingViewModel(
            drillType = drillType,
            metricsEngine = ServiceLocator.metricsEngine(),
            cueEngine = ServiceLocator.cueEngine(),
            repository = repository,
            options = options,
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
    var pendingStopAfterRecordingFinalize by remember { mutableStateOf(false) }

    val cameraManager = remember { CameraSessionManager(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentSettings by rememberUpdatedState(newValue = settings)
    val analyzer = remember {
        PoseAnalyzer(
            onPoseFrame = { vm.onPoseFrame(it, currentSettings) },
            onAnalyzerWarning = vm::onAnalyzerWarning,
            backgroundExecutor = analyzerExecutor,
        )
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
                sessionRecorder.stopRecording()
            }
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
                    } else {
                        vm.onRecordingFinalized(event.outputResults.outputUri.toString())
                    }
                    if (pendingStopAfterRecordingFinalize) {
                        pendingStopAfterRecordingFinalize = false
                        vm.stopSession(onStop)
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
                    modifier = Modifier.fillMaxSize(),
                    showIdealLine = options.showIdealLine,
                    problematicJointName = null,
                    showDebugOverlay = uiState.showDebugOverlay,
                    debugMetrics = uiState.debugMetrics,
                    debugAngles = uiState.debugAngles,
                    currentPhase = uiState.currentPhase,
                    activeFault = uiState.activeFault,
                    cueText = uiState.currentCue,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.58f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Side-view mode • ${drillType.name}", color = Color.White)
            Text("Score: ${uiState.score}", color = Color.White)
            Text("Cue: ${uiState.currentCue.ifBlank { "Awaiting stable frame..." }}", color = Color.White)
            Text("Confidence: ${(uiState.confidence * 100).toInt()}%", color = Color.White)
            Text("Phase: ${uiState.currentPhase}", color = Color.White)
            Text("Reps: ${uiState.repCount}", color = Color.White)
            if (uiState.activeFault.isNotBlank()) Text("Active fault: ${uiState.activeFault}", color = Color.Yellow)
            if (!uiState.cameraPermissionGranted) {
                Text("Camera permission required for live coaching.", color = Color.Yellow)
            }
            if (uiState.cameraPermissionGranted && !uiState.cameraReady) {
                Text("Starting camera...", color = Color.Yellow)
            }
            uiState.warningMessage?.let { Text(it, color = Color.Yellow) }
            uiState.errorMessage?.let { Text(it, color = Color.Red) }
        }



        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            FilledTonalButton(onClick = {
                if (uiState.isRecording) {
                    pendingStopAfterRecordingFinalize = true
                    sessionRecorder.stopRecording()
                    vm.setRecording(false)
                } else {
                    vm.stopSession(onStop)
                }
            }) { Text("Stop") }
        }
    }
}
