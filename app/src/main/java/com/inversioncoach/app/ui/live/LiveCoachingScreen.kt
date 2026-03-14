package com.inversioncoach.app.ui.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.inversioncoach.app.camera.CameraSessionManager
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.overlay.OverlayRenderer
import com.inversioncoach.app.pose.PoseAnalyzer
import com.inversioncoach.app.storage.ServiceLocator
import java.util.concurrent.Executors

@Composable
fun LiveCoachingScreen(drillType: DrillType, onStop: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }

    val vm = remember {
        LiveCoachingViewModel(
            drillType = drillType,
            metricsEngine = ServiceLocator.metricsEngine(),
            cueEngine = ServiceLocator.cueEngine(),
            repository = repository,
        )
    }
    val uiState by vm.uiState.collectAsState()
    val smoothed by vm.smoothedFrame.collectAsState()
    val settings by repository.observeSettings().collectAsState(initial = UserSettings())
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val cameraManager = remember { CameraSessionManager(context) }
    val analyzer = remember {
        PoseAnalyzer(
            context = context,
            onPoseFrame = { vm.onPoseFrame(it, settings) },
            onAnalyzerWarning = vm::onAnalyzerWarning,
            backgroundExecutor = Executors.newSingleThreadExecutor(),
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
        onDispose { cameraManager.release() }
    }

    Box(Modifier.fillMaxSize()) {
        if (uiState.cameraPermissionGranted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        post {
                            cameraManager.bind(lifecycleOwner, this, analyzer) { ready, error ->
                                vm.onCameraReady(ready, error)
                            }
                        }
                    }
                },
            )
            OverlayRenderer(
                frame = smoothed,
                modifier = Modifier.fillMaxSize(),
                showIdealLine = uiState.showIdealLine,
                problematicJointName = "left_shoulder",
            )
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
            if (!uiState.cameraPermissionGranted) {
                Text("Camera permission required for live coaching.", color = Color.Yellow)
            }
            if (uiState.cameraPermissionGranted && !uiState.cameraReady) {
                Text("Starting camera...", color = Color.Yellow)
            }
            uiState.warningMessage?.let { Text(it, color = Color.Yellow) }
            uiState.errorMessage?.let { Text(it, color = Color.Red) }
        }

        if (uiState.showDebugOverlay) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)
                    .background(Color(0xAA101820)).padding(8.dp).verticalScroll(rememberScrollState()),
            ) {
                Text("DEBUG", color = Color.Cyan)
                uiState.debugMetrics.forEach { Text("${it.key}: ${it.score}", color = Color.White) }
                uiState.debugAngles.forEach { Text("${it.key}: ${"%.1f".format(it.degrees)}°", color = Color.White) }
                Text("conf: ${(uiState.confidence * 100).toInt()}%", color = Color.White)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(
                enabled = uiState.cameraPermissionGranted && uiState.cameraReady,
                onClick = vm::toggleRecording,
            ) { Text(if (uiState.isRecording) "Stop Rec" else "Record") }
            Button(onClick = { vm.stopSession(onStop) }) { Text("Stop") }
        }
    }
}
