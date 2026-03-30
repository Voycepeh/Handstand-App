package com.inversioncoach.app.ui.calibration

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.inversioncoach.app.camera.CameraSessionManager
import com.inversioncoach.app.calibration.CalibrationStep
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.FreestyleViewMode
import com.inversioncoach.app.overlay.OverlayRenderer
import com.inversioncoach.app.pose.PoseAnalyzer
import com.inversioncoach.app.pose.PoseCoordinateMapper
import com.inversioncoach.app.pose.PoseProjectionInput
import com.inversioncoach.app.pose.PoseScaleMode
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import java.util.concurrent.Executors

@Composable
fun CalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Structural calibration is profile-based. This reference drill remains an internal implementation
    // detail so we can reuse current readiness/overlay/template logic until calibration-specific logic
    // is fully separated from drill-tied analysis.
    val referenceDrillType = DrillType.FREE_HANDSTAND
    val vm = remember {
        CalibrationViewModel(
            referenceDrillType = referenceDrillType,
            calibrationProfileProvider = ServiceLocator.calibrationProfileProvider(context),
            drillMovementProfileRepository = ServiceLocator.drillMovementProfileRepository(context),
            repository = ServiceLocator.repository(context),
        )
    }
    val state by vm.state.collectAsState()
    val cameraManager = remember { CameraSessionManager(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        PoseAnalyzer(
            onPoseFrame = vm::onPoseFrame,
            onAnalyzerWarning = { },
            backgroundExecutor = analyzerExecutor,
        )
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        cameraPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            analyzerExecutor.shutdown()
            cameraManager.release()
        }
    }

    ScaffoldedScreen(title = "Structural Calibration", onBack = onBack) { padding ->
        when (state.phase) {
            CalibrationPhase.INTRO -> CalibrationIntroScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                onStart = vm::beginCalibration,
            )

            CalibrationPhase.CAPTURING -> CalibrationCaptureContent(
                state = state,
                referenceDrillType = referenceDrillType,
                lifecycleOwner = lifecycleOwner,
                cameraManager = cameraManager,
                analyzer = analyzer,
                vm = vm,
                cameraPermissionGranted = cameraPermissionGranted,
                onRequestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                onExit = onBack,
            )

            CalibrationPhase.COMPLETED -> CalibrationCompleteScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                profileSummary = state.savedProfileSummary,
                savedAtMs = state.savedAtMs,
                onDone = onBack,
            )
        }
    }
}

@Composable
private fun CalibrationCaptureContent(
    state: CalibrationUiState,
    referenceDrillType: DrillType,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraManager: CameraSessionManager,
    analyzer: PoseAnalyzer,
    vm: CalibrationViewModel,
    cameraPermissionGranted: Boolean,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier,
    onExit: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Step ${state.stepIndex}/${state.totalSteps}: ${state.title}", style = MaterialTheme.typography.titleMedium)
        Text(state.instruction)
        Text("Camera setup: ${state.cameraPlacement}")
        CalibrationStepProgressRow(state = state)
        Icon(Icons.Default.Accessibility, contentDescription = null)

        LinearProgressIndicator(
            progress = { state.acceptedFrames / state.requiredFrames.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            strokeCap = StrokeCap.Round,
        )
        Text(if (state.hasCapturedFrame) "Captured frame confirmed" else "Waiting for capture")

        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.hasCapturedFrame) {
                    Text("Captured")
                    Text("Review this captured frame. Retake or continue.")
                } else {
                    Text("Readiness: ${if (state.isReady) "Ready" else "Adjust position"}")
                    Text(state.readinessMessage)
                }
                if (!state.hasCapturedFrame && state.missingRequiredJoints.isNotEmpty()) {
                    Text("Adjust these landmarks: ${state.missingRequiredJoints.joinToString { it.toDisplayLabel() }}")
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = if (state.hasCapturedFrame) 0.82f else 1f },
        ) {
            if (cameraPermissionGranted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            post {
                                cameraManager.bind(
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = this,
                                    analyzer = analyzer,
                                    zoomOutCamera = true,
                                ) { _, _ -> }
                            }
                        }
                    },
                )

            OverlayRenderer(
                frame = state.reviewFrame,
                drillType = referenceDrillType,
                sessionMode = SessionMode.DRILL,
                modifier = Modifier.fillMaxSize(),
                scaleMode = PoseScaleMode.FILL,
                showIdealLine = false,
                showDebugOverlay = false,
                drillCameraSide = DrillCameraSide.LEFT,
                freestyleViewMode = FreestyleViewMode.UNKNOWN,
            )

                CalibrationGuideOverlay(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                )

                if (state.hasCapturedFrame) {
                    Text(
                        text = "Captured frame",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(Color(0xCC1B5E20), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Text(
                            text = "Camera access is required for calibration",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(onClick = onRequestCameraPermission) {
                            Text("Grant camera access")
                        }
                    }
                }
            }
        }

        state.stepResultMessage?.let { Text(it) }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = vm::captureStep,
                modifier = Modifier.weight(1f),
                enabled = cameraPermissionGranted && state.isReady && !state.hasCapturedFrame,
            ) {
                Text("Capture")
            }
            Button(
                onClick = vm::retakeStep,
                modifier = Modifier.weight(1f),
                enabled = cameraPermissionGranted && state.hasCapturedFrame,
            ) {
                Text("Retake")
            }
            Button(
                onClick = vm::continueToNextStep,
                modifier = Modifier.weight(1f),
                enabled = cameraPermissionGranted && state.hasCapturedFrame,
            ) {
                Text("Continue")
            }
        }
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("Exit calibration")
        }
    }
}

@Composable
private fun CalibrationStepProgressRow(state: CalibrationUiState) {
    val stepOrder = listOf(
        CalibrationStep.FRONT_NEUTRAL,
        CalibrationStep.SIDE_NEUTRAL,
        CalibrationStep.ARMS_OVERHEAD,
        CalibrationStep.CONTROLLED_HOLD,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        stepOrder.forEachIndexed { index, step ->
            val done = state.completedSteps.contains(step)
            val current = state.currentStep == step
            val label = when {
                done -> "✅ ${index + 1}"
                current -> "▶ ${index + 1}"
                else -> "○ ${index + 1}"
            }
            Text(
                text = label,
                modifier = Modifier.wrapContentWidth(),
                color = if (done || current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalibrationGuideOverlay(modifier: Modifier, state: CalibrationUiState) {
    val mapper = remember { PoseCoordinateMapper() }
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = state.reviewFrame
            val projectionInput = PoseProjectionInput(
                sourceWidth = frame?.analysisWidth?.takeIf { it > 0 } ?: size.width.toInt().coerceAtLeast(1),
                sourceHeight = frame?.analysisHeight?.takeIf { it > 0 } ?: size.height.toInt().coerceAtLeast(1),
                previewWidth = size.width,
                previewHeight = size.height,
                rotationDegrees = frame?.analysisRotationDegrees ?: 0,
                mirrored = frame?.mirrored ?: false,
                scaleMode = PoseScaleMode.FILL,
            )
            val projectedPreviewRect = mapper.diagnostics(projectionInput).contentRect
            val visiblePreviewRect = projectedPreviewRect.intersectWithin(size.width, size.height)
            val guideRect = androidx.compose.ui.geometry.Rect(
                left = visiblePreviewRect.left,
                top = visiblePreviewRect.top,
                right = visiblePreviewRect.right,
                bottom = visiblePreviewRect.bottom,
            )

            drawRect(
                color = if (state.isReady) Color(0xFF4CAF50) else Color(0xFFFFA000),
                topLeft = guideRect.topLeft,
                size = guideRect.size,
                style = Stroke(width = 4f),
            )

            val jointsByName = frame?.joints?.associateBy { it.name }.orEmpty()
            val missing = state.missingRequiredJoints.toSet()
            state.requiredJointNames.forEach { name ->
                val p = jointsByName[name] ?: return@forEach
                val mapped = mapper.map(p.x, p.y, projectionInput)
                drawCircle(
                    color = if (missing.contains(name)) Color.Red else Color(0xFF00E676),
                    radius = 8f,
                    center = Offset(mapped.x, mapped.y),
                )
            }
        }

        Text(
            text = if (state.isReady) "Ready" else "Adjust",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun String.toDisplayLabel(): String =
    replace('_', ' ')
        .split(' ')
        .joinToString(" ") { token -> token.replaceFirstChar { c -> c.titlecase() } }

private fun androidx.compose.ui.geometry.Rect.intersectWithin(width: Float, height: Float): androidx.compose.ui.geometry.Rect {
    val bounded = androidx.compose.ui.geometry.Rect(
        left = left.coerceIn(0f, width),
        top = top.coerceIn(0f, height),
        right = right.coerceIn(0f, width),
        bottom = bottom.coerceIn(0f, height),
    )
    if (bounded.width <= 0f || bounded.height <= 0f) {
        return androidx.compose.ui.geometry.Rect(0f, 0f, width, height)
    }
    return bounded
}
