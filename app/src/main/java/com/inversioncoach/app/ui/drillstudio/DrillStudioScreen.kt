package com.inversioncoach.app.ui.drillstudio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.CatalogNormalizationBasis
import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.drills.catalog.DrillCatalogRepository
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhaseBoundaryGuides
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.DropdownOption
import com.inversioncoach.app.ui.components.MultiSelectChipsField
import com.inversioncoach.app.ui.components.OverlaySkeletonPreview
import com.inversioncoach.app.ui.components.ReliableDropdownField
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.components.SeededSkeletonPreview
import com.inversioncoach.app.ui.components.SkeletonPreviewPolicies
import com.inversioncoach.app.ui.components.SkeletonRenderContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val drillStudioSkeletonPolicy = SkeletonPreviewPolicies.poseAuthoring
private val drillStudioAuthoringPolicy = drillStudioSkeletonPolicy.copy(contentPaddingFraction = 0.04f)
private const val CAMERA_CAPTURE_TAG = "DrillStudioCameraCapture"

@Composable
fun DrillStudioScreen(
    onBack: () -> Unit,
    initRequest: DrillStudioInitRequest,
    onSaveSuccess: () -> Unit = onBack,
) {
    val context = LocalContext.current
    val vm = remember {
        DrillStudioViewModel(
            repository = DrillCatalogRepository(context),
            sessionRepository = ServiceLocator.repository(context),
        )
    }
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(initRequest.mode, initRequest.drillId, initRequest.templateId) {
        vm.initialize(initRequest)
    }
    ScaffoldedScreen(title = "Drill Studio", onBack = onBack) { padding ->
        when (val state = uiState) {
            DrillStudioUiState.Loading -> DrillStudioLoading(padding)
            is DrillStudioUiState.Error -> DrillStudioError(
                padding = padding,
                message = state.message,
                onRetry = { vm.initialize(initRequest) },
                onBack = onBack,
            )
            is DrillStudioUiState.Ready -> DrillStudioEditor(
                padding = padding,
                draft = state.draft,
                sourceSeedId = state.sourceSeedId,
                validationErrors = state.validationErrors,
                statusMessage = state.statusMessage,
                onUpdateDraft = vm::updateDraft,
                onAddPhase = vm::addPhase,
                onDuplicatePhase = vm::duplicatePhase,
                onDeletePhase = vm::deletePhase,
                onRenamePhase = vm::renamePhase,
                onMovePhase = vm::movePhase,
                onUpdatePhaseDurations = vm::updatePhaseDurations,
                onCopyPreviousPose = vm::copyPreviousPose,
                onMirrorPose = vm::mirrorPose,
                onResetPose = vm::resetPose,
                onApplyPreset = vm::applyPosePreset,
                onUpdatePhasePoseJoint = vm::updatePhasePoseJoint,
                onAttachReferenceImage = vm::attachReferenceImage,
                onClearReferenceImage = vm::clearReferenceImage,
                onApplyDetectedPose = vm::applyDetectedImagePose,
                onResetImageDetection = vm::resetImageDetection,
                onResetJointCorrection = vm::resetJointCorrection,
                onResetAllCorrections = vm::resetAllCorrections,
                onUpdatePhaseGuides = vm::updatePhaseGuides,
                onSavePhasePose = vm::savePhasePose,
                onSave = { onComplete -> vm.save(onComplete) },
                onSaveSuccess = onSaveSuccess,
                bodyProfile = null,
            )
        }
    }
}

@Composable
private fun DrillStudioLoading(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Initializing editor…", style = MaterialTheme.typography.titleMedium)
        Text("Loading draft and validating drill state.")
    }
}

@Composable
private fun DrillStudioError(
    padding: PaddingValues,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Could not open Drill Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text("Retry") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun DrillStudioEditor(
    padding: PaddingValues,
    draft: DrillTemplate,
    sourceSeedId: String?,
    validationErrors: List<String>,
    statusMessage: String?,
    onUpdateDraft: ((DrillTemplate) -> DrillTemplate) -> Unit,
    onAddPhase: () -> Unit,
    onDuplicatePhase: (String) -> Unit,
    onDeletePhase: (String) -> Unit,
    onRenamePhase: (String, String) -> Unit,
    onMovePhase: (String, Int) -> Unit,
    onUpdatePhaseDurations: (String, Int?, Int) -> Unit,
    onCopyPreviousPose: (String) -> Unit,
    onMirrorPose: (String) -> Unit,
    onResetPose: (String) -> Unit,
    onApplyPreset: (String, String) -> Unit,
    onUpdatePhasePoseJoint: (String, String, JointPoint) -> Unit,
    onAttachReferenceImage: (String, String) -> Unit,
    onClearReferenceImage: (String) -> Unit,
    onApplyDetectedPose: (String, Map<String, JointPoint>, Map<String, Float>, Float) -> Unit,
    onResetImageDetection: (String) -> Unit,
    onResetJointCorrection: (String, String) -> Unit,
    onResetAllCorrections: (String) -> Unit,
    onUpdatePhaseGuides: (String, PhaseBoundaryGuides) -> Unit,
    onSavePhasePose: (String) -> Unit,
    onSave: ((Boolean) -> Unit) -> Unit,
    onSaveSuccess: () -> Unit,
    bodyProfile: UserBodyProfile?,
) {
    val context = LocalContext.current
    val phasePoses = draft.skeletonTemplate.phasePoses
    var selectedPhaseId by remember(draft.id) {
        mutableStateOf(phasePoses.firstOrNull()?.phaseId ?: draft.phases.first().id)
    }
    var previewProgress by remember(draft.id) { mutableFloatStateOf(0f) }
    var autoPlay by remember(draft.id) { mutableStateOf(true) }
    var advancedMode by remember(draft.id) { mutableStateOf(false) }
    var selectedJoint by remember(draft.id, selectedPhaseId) {
        mutableStateOf(phasePoses.firstOrNull { it.phaseId == selectedPhaseId }?.joints?.keys?.firstOrNull())
    }
    var detectionStatus by remember(selectedPhaseId) { mutableStateOf<String?>(null) }
    var isDetecting by remember(selectedPhaseId) { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val detector = remember { DrillStudioImagePoseDetector() }
    val imageStore = remember { DrillStudioImageStore(context.applicationContext) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { imageStore.persistPickedImage(uri) }
                .onSuccess { stableUri ->
                    onAttachReferenceImage(selectedPhaseId, stableUri)
                    detectionStatus = "Image added"
                }
                .onFailure { detectionStatus = "Failed to persist image: ${it.message}" }
        }
    }
    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        Log.d(CAMERA_CAPTURE_TAG, "TakePicture result success=$success pendingUri=$pendingCameraUri")
        val capturedUri = pendingCameraUri
        pendingCameraUri = null
        if (!success || capturedUri == null) {
            detectionStatus = "Photo capture canceled. You can still choose an image from your device."
            Log.d(CAMERA_CAPTURE_TAG, "Capture cancelled or missing pending URI")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching { imageStore.persistCapturedImage(capturedUri) }
                .onSuccess { stableUri ->
                    Log.d(CAMERA_CAPTURE_TAG, "Capture persisted and attached for phase=$selectedPhaseId uri=$stableUri")
                    onAttachReferenceImage(selectedPhaseId, stableUri)
                    detectionStatus = "Photo added"
                }
                .onFailure {
                    Log.e(CAMERA_CAPTURE_TAG, "Failed to persist captured image", it)
                    detectionStatus = "Failed to persist photo: ${it.message}"
                }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.d(CAMERA_CAPTURE_TAG, "Camera permission granted=$granted")
        if (!granted) {
            detectionStatus = "Camera permission denied. Use Choose from device."
            return@rememberLauncherForActivityResult
        }
        launchCameraCapture(
            createOutputUri = { createCameraCaptureUri(context) },
            onPrepared = { pendingCameraUri = it },
            launch = { takePhotoLauncher.launch(it) },
            onFailure = { detectionStatus = it },
        )
    }
    fun launchImagePicker() {
        runCatching { imagePicker.launch(arrayOf("image/*")) }
            .onFailure {
                Log.e(CAMERA_CAPTURE_TAG, "Image picker launch failed", it)
                detectionStatus = "Image picker unavailable"
            }
    }
    fun requestCameraCapture() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture(
                createOutputUri = { createCameraCaptureUri(context) },
                onPrepared = { pendingCameraUri = it },
                launch = { takePhotoLauncher.launch(it) },
                onFailure = { detectionStatus = it },
            )
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val orderedPhases = draft.phases.sortedBy { it.order }
    val selectedPhaseTemplate = orderedPhases.firstOrNull { it.id == selectedPhaseId }
    var previousOrderedPhaseIds by remember(draft.id) {
        mutableStateOf(orderedPhases.map { it.id })
    }

    LaunchedEffect(orderedPhases.map { it.id }, selectedPhaseId) {
        val orderedPhaseIds = orderedPhases.map { it.id }
        selectedPhaseId = stabilizeSelectedPhaseSelection(
            previousOrderedPhaseIds = previousOrderedPhaseIds,
            nextOrderedPhaseIds = orderedPhaseIds,
            currentSelectedPhaseId = selectedPhaseId,
        ) ?: selectedPhaseId
        previousOrderedPhaseIds = orderedPhaseIds
        val hasPoseForSelection = phasePoses.any { it.phaseId == selectedPhaseId }
        Log.d(
            "DrillStudioHydration",
            "phaseSelection selectedPhaseId=$selectedPhaseId hasPose=$hasPoseForSelection " +
                "available=${phasePoses.map { it.phaseId }}",
        )
    }

    LaunchedEffect(autoPlay, draft.id, draft.skeletonTemplate.framesPerSecond) {
        if (!autoPlay) return@LaunchedEffect
        val frameCount = draft.skeletonTemplate.keyframes.size.coerceAtLeast(1)
        val progressStep = 1f / frameCount.toFloat()
        while (isActive) {
            val fps = draft.skeletonTemplate.framesPerSecond.coerceAtLeast(1)
            previewProgress += progressStep
            if (previewProgress > 1f) previewProgress -= 1f
            kotlinx.coroutines.delay((1000L / fps).coerceAtLeast(16L))
        }
    }

    val currentPose = phasePoses.firstOrNull { it.phaseId == selectedPhaseId } ?: phasePoses.first()
    val cameraOptions = remember { CameraView.entries.map { DropdownOption(it, it.name.pretty()) } }
    val movementOptions = remember { CatalogMovementType.entries.map { DropdownOption(it, it.name.pretty()) } }
    val comparisonModeOptions = remember { ComparisonMode.entries.map { DropdownOption(it, it.name.pretty()) } }
    val normalizationOptions = remember { CatalogNormalizationBasis.entries.map { DropdownOption(it, it.name.pretty()) } }
    val keyJointOptions = remember {
        listOf(
            "head", "shoulder_left", "shoulder_right", "elbow_left", "elbow_right",
            "wrist_left", "wrist_right", "hip_left", "hip_right", "knee_left",
            "knee_right", "ankle_left", "ankle_right",
        ).map { DropdownOption(it, it.pretty()) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Drill details") {
                Text(if (sourceSeedId == null) "New drill" else "Editing seeded source: $sourceSeedId")
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { value -> onUpdateDraft { it.copy(title = value) } },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { value -> onUpdateDraft { it.copy(description = value) } },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ReliableDropdownField(
                    label = "Movement mode",
                    selected = movementOptions.firstOrNull { it.value == draft.movementType } ?: movementOptions.first(),
                    options = movementOptions,
                    onOptionSelected = { option -> onUpdateDraft { it.copy(movementType = option.value) } },
                )
                Text("Choose whether the drill is evaluated as a hold or a rep sequence.", style = MaterialTheme.typography.bodySmall)
                ReliableDropdownField(
                    label = "Camera view",
                    selected = cameraOptions.firstOrNull { it.value == draft.cameraView } ?: cameraOptions.first(),
                    options = cameraOptions,
                    onOptionSelected = { option ->
                        onUpdateDraft {
                            it.copy(
                                cameraView = option.value,
                                supportedViews = (it.supportedViews + option.value).distinct(),
                                analysisPlane = analysisPlaneForPrimaryView(option.value),
                            )
                        }
                    },
                )
                Text("Defines which viewing angle the drill expects for analysis.", style = MaterialTheme.typography.bodySmall)
                ReliableDropdownField(
                    label = "Comparison mode",
                    selected = comparisonModeOptions.firstOrNull { it.value == draft.comparisonMode } ?: comparisonModeOptions.first(),
                    options = comparisonModeOptions,
                    onOptionSelected = { option -> onUpdateDraft { it.copy(comparisonMode = option.value) } },
                )
                Text("Controls how poses are compared during drill analysis.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            SectionCard(title = "Phases") {
                Text("Key stages of the drill used for sequencing, timing, and pose comparison.", style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    orderedPhases.forEach { phase ->
                        FilterChip(
                            selected = phase.id == selectedPhaseId,
                            onClick = { selectedPhaseId = phase.id },
                            label = { Text(phase.label) },
                        )
                    }
                    Button(onClick = onAddPhase) { Text("+ Add phase") }
                }
                selectedPhaseTemplate?.let { phase ->
                    OutlinedTextField(
                        value = phase.label,
                        onValueChange = { value -> onRenamePhase(phase.id, value) },
                        label = { Text("Phase name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val phasePose = phasePoses.firstOrNull { it.phaseId == phase.id }
                    AxisEditor(
                        label = "Hold duration (ms)",
                        value = (phasePose?.holdDurationMs ?: 0).toFloat(),
                        valueRange = 0f..5000f,
                    ) { next -> onUpdatePhaseDurations(phase.id, next.toInt(), phasePose?.transitionDurationMs ?: 700) }
                    AxisEditor(
                        label = "Transition duration (ms)",
                        value = (phasePose?.transitionDurationMs ?: 700).toFloat(),
                        valueRange = 100f..5000f,
                    ) { next -> onUpdatePhaseDurations(phase.id, phasePose?.holdDurationMs, next.toInt()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onMovePhase(phase.id, -1) }) { Text("Move up") }
                        Button(onClick = { onMovePhase(phase.id, 1) }) { Text("Move down") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onDuplicatePhase(phase.id) }) { Text("Duplicate") }
                        Button(onClick = { onDeletePhase(phase.id) }, enabled = draft.phases.size > 1) { Text("Delete") }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Pose authoring") {
                Text("Editing pose for: ${selectedPhaseTemplate?.label ?: currentPose.name}")
                val guides = currentPose.authoring?.guides ?: PhaseBoundaryGuides()
                val imageUri = currentPose.authoring?.sourceImageUri?.let(Uri::parse)
                val referenceImage = rememberReferenceImageBitmap(imageUri)
                PoseAuthoringViewport(
                    phasePose = currentPose,
                    bodyProfile = bodyProfile,
                    referenceImage = referenceImage,
                    guides = guides,
                    onJointMoved = { joint, point ->
                        onUpdatePhasePoseJoint(currentPose.phaseId, joint, point)
                    },
                )
                Text(authoringStatusSummary(currentPose, imageUri != null && referenceImage != null, isDetecting), style = MaterialTheme.typography.bodySmall)
                detectionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                PoseActionGroup(title = "Image + detection") {
                    PoseActionRow(maxItemsInEachRow = 2) {
                        Button(onClick = { showImageSourceDialog = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Add reference image") }
                        OutlinedButton(
                            onClick = {
                                onClearReferenceImage(currentPose.phaseId)
                                isDetecting = false
                                detectionStatus = "Image and detected pose cleared"
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) { Text("Clear image") }
                    }
                    PoseActionRow(maxItemsInEachRow = 2) {
                        Button(onClick = {
                            val uri = imageUri ?: return@Button
                            scope.launch {
                                isDetecting = true
                                detectionStatus = "Detecting pose…"
                                runCatching { detector.detect(context, uri) }
                                    .onSuccess {
                                        isDetecting = false
                                        onApplyDetectedPose(currentPose.phaseId, it.normalizedJoints, it.jointConfidence, it.qualityScore)
                                        detectionStatus = if (it.normalizedJoints.isEmpty()) "No pose detected in image" else "Detected pose applied"
                                    }
                                    .onFailure {
                                        isDetecting = false
                                        detectionStatus = "Pose detection failed: ${it.message}"
                                    }
                            }
                        }, enabled = imageUri != null, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Detect pose") }
                        OutlinedButton(onClick = {
                            onResetImageDetection(currentPose.phaseId)
                            isDetecting = false
                            detectionStatus = "Detected pose cleared"
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Reset detection") }
                    }
                }
                PoseActionGroup(title = "Edit pose") {
                    PoseActionRow(maxItemsInEachRow = 3) {
                        OutlinedButton(onClick = { onCopyPreviousPose(currentPose.phaseId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Copy previous") }
                        OutlinedButton(onClick = { onMirrorPose(currentPose.phaseId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Mirror") }
                        OutlinedButton(onClick = {
                            onResetPose(currentPose.phaseId)
                            detectionStatus = "Pose reset to neutral preset"
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Reset") }
                    }
                }
                PoseActionGroup(title = "Advanced editing") {
                    PoseActionRow(maxItemsInEachRow = 2) {
                        OutlinedButton(
                            onClick = { selectedJoint?.let { onResetJointCorrection(currentPose.phaseId, it) } },
                            enabled = selectedJoint != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) {
                            Text("Reset selected joint")
                        }
                        OutlinedButton(
                            onClick = { onResetAllCorrections(currentPose.phaseId) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) { Text("Reset all corrections") }
                    }
                }
                PoseActionGroup(title = "Save") {
                    Button(onClick = { onSavePhasePose(currentPose.phaseId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Save phase pose") }
                }
                PoseGuideToggles(guides = guides) { updated -> onUpdatePhaseGuides(currentPose.phaseId, updated) }
                if (showImageSourceDialog) {
                    AlertDialog(
                        onDismissRequest = { showImageSourceDialog = false },
                        title = { Text("Add reference image") },
                        text = { Text("Choose how to add a phase reference image.") },
                        confirmButton = {
                            Button(onClick = {
                                showImageSourceDialog = false
                                requestCameraCapture()
                            }) {
                                Text("Take photo")
                            }
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    showImageSourceDialog = false
                                    launchImagePicker()
                                }) {
                                    Text("Choose from device")
                                }
                                TextButton(onClick = { showImageSourceDialog = false }) { Text("Cancel") }
                            }
                        },
                    )
                }
            }
        }

        item {
            SectionCard(title = "Drill analysis settings") {
                MultiSelectChipsField(
                    label = "Key joints",
                    options = keyJointOptions,
                    selectedValues = draft.keyJoints.toSet(),
                    onToggle = { joint ->
                        onUpdateDraft { current ->
                            val next = if (joint in current.keyJoints) current.keyJoints - joint else current.keyJoints + joint
                            current.copy(keyJoints = next.distinct())
                        }
                    },
                )
                Text("The body joints most important for evaluating this drill.", style = MaterialTheme.typography.bodySmall)
                ReliableDropdownField(
                    label = "Normalization basis",
                    selected = normalizationOptions.firstOrNull { it.value == draft.normalizationBasis } ?: normalizationOptions.first(),
                    options = normalizationOptions,
                    onOptionSelected = { option -> onUpdateDraft { it.copy(normalizationBasis = option.value) } },
                )
                Text("Reference used to scale pose comparisons consistently.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            SectionCard(title = "Motion preview") {
                PreviewCard(drill = draft, progress = previewProgress)
                Slider(value = previewProgress, onValueChange = { autoPlay = false; previewProgress = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { autoPlay = !autoPlay }) { Text(if (autoPlay) "Pause" else "Play") }
                }
            }
        }

        item {
            SectionCard(title = "Advanced mode") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Show keyframe controls")
                    Switch(checked = advancedMode, onCheckedChange = { advancedMode = it })
                }
                if (advancedMode) {
                    val jointOptions = currentPose.joints.keys.sorted().map { DropdownOption(it, it) }
                    val selected = jointOptions.firstOrNull { it.value == selectedJoint } ?: jointOptions.first()
                    ReliableDropdownField(
                        label = "Joint",
                        selected = selected,
                        options = jointOptions,
                        onOptionSelected = { selectedJoint = it.value },
                    )
                    val current = currentPose.joints[selectedJoint] ?: JointPoint(0.5f, 0.5f)
                    AxisEditor(label = "X", value = current.x, valueRange = 0f..1f) { x ->
                        onUpdatePhasePoseJoint(currentPose.phaseId, selected.value, current.copy(x = x))
                    }
                    AxisEditor(label = "Y", value = current.y, valueRange = 0f..1f) { y ->
                        onUpdatePhasePoseJoint(currentPose.phaseId, selected.value, current.copy(y = y))
                    }
                }
            }
        }

        item {
            statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            validationErrors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    onSave { success ->
                        if (success) onSaveSuccess()
                    }
                }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun AxisEditor(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onUpdate: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${"%.0f".format(value)}")
        Slider(value = value.coerceIn(valueRange.start, valueRange.endInclusive), onValueChange = onUpdate, valueRange = valueRange)
    }
}

@Composable
private fun PoseAuthoringViewport(
    phasePose: PhasePoseTemplate,
    bodyProfile: UserBodyProfile?,
    referenceImage: ImageBitmap?,
    guides: PhaseBoundaryGuides,
    onJointMoved: (String, JointPoint) -> Unit,
) {
    var activeJoint by remember(phasePose.phaseId) { mutableStateOf<String?>(null) }
    val renderPose = remember(phasePose.joints) {
        DrillStudioPoseUtils.renderPoseWithFallback(
            joints = phasePose.joints,
            fallback = DrillStudioPosePresets.neutralUpright.joints,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(drillStudioAuthoringPolicy.aspectRatio)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(phasePose.phaseId, phasePose.joints, referenceImage, drillStudioAuthoringPolicy) {
                detectDragGestures(
                    onDragStart = { start ->
                        val imageBounds = resolveImageBounds(size.toSize(), referenceImage)
                        val touch = toNormalizedPoint(start, imageBounds)
                        activeJoint = DrillStudioPoseUtils.nearestJointWithinRadius(
                            joints = renderPose,
                            touch = touch,
                            hitRadius = 0.08f,
                        )
                    },
                    onDrag = { change, _ ->
                        val joint = activeJoint ?: return@detectDragGestures
                        val imageBounds = resolveImageBounds(size.toSize(), referenceImage)
                        val normalized = toNormalizedPoint(change.position, imageBounds)
                        val constrained = DrillStudioPoseUtils.applyAnatomicalGuardrails(
                            pose = renderPose,
                            joint = joint,
                            target = normalized,
                            bodyProfile = bodyProfile,
                        )
                        onJointMoved(joint, constrained)
                    },
                    onDragEnd = { activeJoint = null },
                    onDragCancel = { activeJoint = null },
                )
            },
    ) {
        OverlaySkeletonPreview(
            joints = renderPose,
            policy = drillStudioAuthoringPolicy,
            resolveOverlayBounds = { canvasSize ->
                resolveImageBounds(canvasSize, referenceImage).toRect()
            },
            highlightedJoint = activeJoint,
            showBackground = false,
        ) {
            val imageBounds = resolveImageBounds(size, referenceImage)
            if (referenceImage != null) {
                drawImage(
                    image = referenceImage,
                    dstOffset = Offset(imageBounds.left, imageBounds.top).toIntOffset(),
                    dstSize = IntSize(imageBounds.width.roundToInt(), imageBounds.height.roundToInt()),
                )
            } else {
                drawRect(
                    color = Color(0x141E88E5),
                    topLeft = Offset(imageBounds.left, imageBounds.top),
                    size = Size(imageBounds.width, imageBounds.height),
                )
            }
            clipRect(
                left = imageBounds.left,
                top = imageBounds.top,
                right = imageBounds.left + imageBounds.width,
                bottom = imageBounds.top + imageBounds.height,
            ) {
                if (guides.showFrameGuides) {
                    drawRect(
                        color = Color(0x44FFFFFF),
                        topLeft = Offset(imageBounds.left, imageBounds.top),
                        size = Size(imageBounds.width, imageBounds.height),
                    )
                    val corner = 24f
                    val left = imageBounds.left
                    val right = imageBounds.left + imageBounds.width
                    val top = imageBounds.top
                    val bottom = imageBounds.top + imageBounds.height
                    drawLine(Color(0x99FFFFFF), Offset(left, top), Offset(left + corner, top), 2f)
                    drawLine(Color(0x99FFFFFF), Offset(left, top), Offset(left, top + corner), 2f)
                    drawLine(Color(0x99FFFFFF), Offset(right, top), Offset(right - corner, top), 2f)
                    drawLine(Color(0x99FFFFFF), Offset(right, top), Offset(right, top + corner), 2f)
                    drawLine(Color(0x99FFFFFF), Offset(left, bottom), Offset(left + corner, bottom), 2f)
                    drawLine(Color(0x99FFFFFF), Offset(left, bottom), Offset(left, bottom - corner), 2f)
                    drawLine(Color(0x99FFFFFF), Offset(right, bottom), Offset(right - corner, bottom), 2f)
                    drawLine(Color(0x99FFFFFF), Offset(right, bottom), Offset(right, bottom - corner), 2f)
                }
                if (guides.showFloorLine) {
                    val y = imageBounds.top + (guides.floorLineY.coerceIn(0f, 1f) * imageBounds.height)
                    drawLine(
                        Color(0xAA4FC3F7),
                        Offset(imageBounds.left, y),
                        Offset(imageBounds.left + imageBounds.width, y),
                        strokeWidth = 2f,
                    )
                }
                if (guides.showWallLine) {
                    val x = imageBounds.left + (guides.wallLineX.coerceIn(0f, 1f) * imageBounds.width)
                    drawLine(
                        Color(0xAAF48FB1),
                        Offset(x, imageBounds.top),
                        Offset(x, imageBounds.top + imageBounds.height),
                        strokeWidth = 2f,
                    )
                }
                if (guides.showBarLine) {
                    val y = imageBounds.top + (guides.barLineY.coerceIn(0f, 1f) * imageBounds.height)
                    drawLine(
                        Color(0xAAFFE082),
                        Offset(imageBounds.left, y),
                        Offset(imageBounds.left + imageBounds.width, y),
                        strokeWidth = 2f,
                    )
                }
            }
        }
        if (referenceImage == null) {
            Text(
                text = "No reference image loaded",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PoseActionRow(
    maxItemsInEachRow: Int = 2,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = maxItemsInEachRow,
    )
    {
        content()
    }
}

@Composable
private fun PoseActionGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}

@Composable
private fun PoseGuideToggles(
    guides: PhaseBoundaryGuides,
    onGuidesUpdated: (PhaseBoundaryGuides) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Boundary guides", style = MaterialTheme.typography.bodySmall)
        PoseActionRow {
            PoseToggle("Frame", guides.showFrameGuides) { onGuidesUpdated(guides.copy(showFrameGuides = it)) }
            PoseToggle("Floor", guides.showFloorLine) { onGuidesUpdated(guides.copy(showFloorLine = it)) }
        }
        PoseActionRow {
            PoseToggle("Wall", guides.showWallLine) { onGuidesUpdated(guides.copy(showWallLine = it)) }
            PoseToggle("Bar", guides.showBarLine) { onGuidesUpdated(guides.copy(showBarLine = it)) }
        }
    }
}

@Composable
private fun PoseToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun rememberReferenceImageBitmap(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = uri?.let {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(it)?.use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }
        }
    }
    return bitmap
}

@Composable
private fun PreviewCard(drill: DrillTemplate, progress: Float) {
    SeededSkeletonPreview(
        template = drill.skeletonTemplate,
        progress = progress,
        policy = SkeletonPreviewPolicies.motionPreview,
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private data class ImageBounds(val left: Float, val top: Float, val width: Float, val height: Float)

private fun ImageBounds.toRect(): Rect = Rect(
    left = left,
    top = top,
    right = left + width,
    bottom = top + height,
)

private fun resolveImageBounds(canvasSize: Size, referenceImage: ImageBitmap?): ImageBounds {
    val rect = SkeletonRenderContract.displayedImageBounds(
        canvasSize = canvasSize,
        imageWidth = referenceImage?.width,
        imageHeight = referenceImage?.height,
        policy = drillStudioAuthoringPolicy,
    )
    return ImageBounds(rect.left, rect.top, rect.width, rect.height)
}

private fun toNormalizedPoint(position: Offset, bounds: ImageBounds): JointPoint {
    val normalizedX = ((position.x - bounds.left) / bounds.width).coerceIn(0f, 1f)
    val normalizedY = ((position.y - bounds.top) / bounds.height).coerceIn(0f, 1f)
    return JointPoint(normalizedX, normalizedY)
}

private fun Offset.toIntOffset(): IntOffset = IntOffset(x.roundToInt(), y.roundToInt())

private fun IntSize.toSize(): Size = Size(width.toFloat(), height.toFloat())

private fun createCameraCaptureUri(context: Context): Uri? {
    return runCatching {
        val captureDirectory = context.cacheDir.resolve("recordings").apply { mkdirs() }
        val file = kotlin.io.path.createTempFile(
            directory = captureDirectory.toPath(),
            prefix = "drill_studio_capture_",
            suffix = ".jpg",
        ).toFile()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

private fun launchCameraCapture(
    createOutputUri: () -> Uri?,
    onPrepared: (Uri) -> Unit,
    launch: (Uri) -> Unit,
    onFailure: (String) -> Unit,
) {
    val outputUri = createOutputUri()
    if (outputUri == null) {
        Log.e(CAMERA_CAPTURE_TAG, "Failed to create camera output URI")
        onFailure("Camera unavailable")
        return
    }
    runCatching {
        onPrepared(outputUri)
        Log.d(CAMERA_CAPTURE_TAG, "Launching camera capture uri=$outputUri")
        launch(outputUri)
    }.onFailure {
        Log.e(CAMERA_CAPTURE_TAG, "Camera launch failed", it)
        onFailure("Camera capture unavailable on this device. Use Choose from device.")
    }
}

internal fun authoringStatusSummary(
    pose: PhasePoseTemplate,
    imageLoaded: Boolean,
    isDetecting: Boolean,
): String {
    if (!imageLoaded) return "Canvas mode: manual only (no image loaded)"
    if (isDetecting) return "Image loaded • detecting pose…"
    val hasDetected = pose.authoring?.detectedJoints?.isNotEmpty() == true
    val corrections = pose.authoring?.manualOffsets?.size ?: 0
    val source = when {
        !hasDetected -> "manual"
        corrections > 0 -> "detected + adjusted"
        else -> "detected"
    }
    val quality = ((pose.authoring?.qualityScore ?: 0f) * 100).toInt().coerceIn(0, 100)
    return "Image loaded • source: $source • corrected joints: $corrections • quality: $quality%"
}

private fun String.pretty(): String = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

internal fun stabilizeSelectedPhaseSelection(
    previousOrderedPhaseIds: List<String>,
    nextOrderedPhaseIds: List<String>,
    currentSelectedPhaseId: String?,
): String? {
    if (nextOrderedPhaseIds.isEmpty()) return null
    val selected = currentSelectedPhaseId.orEmpty()
    if (selected.isNotBlank() && selected in nextOrderedPhaseIds) return selected
    if (previousOrderedPhaseIds.isEmpty()) return nextOrderedPhaseIds.first()

    val previousIndex = previousOrderedPhaseIds.indexOf(selected)
    val fallbackIndex = if (previousIndex < 0) 0 else previousIndex.coerceAtMost(nextOrderedPhaseIds.lastIndex)
    return nextOrderedPhaseIds.getOrNull(fallbackIndex)
        ?: nextOrderedPhaseIds.getOrNull((fallbackIndex - 1).coerceAtLeast(0))
        ?: nextOrderedPhaseIds.first()
}
