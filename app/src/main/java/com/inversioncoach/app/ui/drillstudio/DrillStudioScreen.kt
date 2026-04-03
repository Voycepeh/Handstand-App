package com.inversioncoach.app.ui.drillstudio

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.inversioncoach.app.ui.components.OverlaySkeletonPreviewStyle
import com.inversioncoach.app.ui.components.ReliableDropdownField
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.components.SeededSkeletonPreview
import com.inversioncoach.app.ui.components.SeededSkeletonPreviewDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var selectedPhaseId by remember(draft.id, phasePoses) {
        mutableStateOf(phasePoses.firstOrNull()?.phaseId ?: draft.phases.first().id)
    }
    var previewProgress by remember(draft.id) { mutableFloatStateOf(0f) }
    var autoPlay by remember(draft.id) { mutableStateOf(true) }
    var advancedMode by remember(draft.id) { mutableStateOf(false) }
    var selectedJoint by remember(draft.id, selectedPhaseId) {
        mutableStateOf(phasePoses.firstOrNull { it.phaseId == selectedPhaseId }?.joints?.keys?.firstOrNull())
    }
    var detectionStatus by remember(selectedPhaseId) { mutableStateOf<String?>(null) }
    var workingImageUri by remember(selectedPhaseId) { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    val detector = remember { DrillStudioImagePoseDetector() }
    val imageStore = remember { DrillStudioImageStore(context.applicationContext) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { imageStore.persistPickedImage(uri) }
                .onSuccess { stableUri ->
                    workingImageUri = Uri.parse(stableUri)
                    onAttachReferenceImage(selectedPhaseId, stableUri)
                    detectionStatus = "Reference image attached"
                }
                .onFailure { detectionStatus = "Failed to persist image: ${it.message}" }
        }
    }
    val orderedPhases = draft.phases.sortedBy { it.order }
    val selectedPhaseTemplate = orderedPhases.firstOrNull { it.id == selectedPhaseId }

    LaunchedEffect(orderedPhases.map { it.id }, selectedPhaseId) {
        selectedPhaseId = DrillStudioPhaseEditor.recoverSelectionAfterDelete(
            remainingPhaseIds = orderedPhases.map { it.id },
            availablePosePhaseIds = phasePoses.map { it.phaseId },
            previousSelectedPhaseId = selectedPhaseId,
        ) ?: selectedPhaseId
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
                val imageUri = workingImageUri ?: currentPose.authoring?.sourceImageUri?.let(Uri::parse)
                val referenceImage = rememberReferenceImageBitmap(imageUri)
                PoseCanvas(
                    phasePose = currentPose,
                    bodyProfile = bodyProfile,
                    referenceImage = referenceImage,
                    guides = guides,
                    onJointMoved = { joint, point -> onUpdatePhasePoseJoint(currentPose.phaseId, joint, point) },
                )
                Text(
                    "Image: ${if (imageUri != null && referenceImage != null) "attached" else "none"} • " +
                        "Detected: ${if (currentPose.authoring?.detectedJoints?.isNotEmpty() == true) "yes" else "no"} • " +
                        "Corrected joints: ${currentPose.authoring?.manualOffsets?.size ?: 0} • " +
                        "Quality: ${((currentPose.authoring?.qualityScore ?: 0f) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
                detectionStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("Upload image") }
                    Button(onClick = {
                        val uri = imageUri ?: return@Button
                        scope.launch {
                            detectionStatus = "Running pose detection…"
                            runCatching { detector.detect(context, uri) }
                                .onSuccess {
                                    onApplyDetectedPose(currentPose.phaseId, it.normalizedJoints, it.jointConfidence, it.qualityScore)
                                    detectionStatus = "Pose detected"
                                }
                                .onFailure { detectionStatus = "Pose detection failed: ${it.message}" }
                        }
                    }, enabled = imageUri != null) { Text("Detect pose") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCopyPreviousPose(currentPose.phaseId) }) { Text("Copy previous") }
                    Button(onClick = { onMirrorPose(currentPose.phaseId) }) { Text("Mirror") }
                    Button(onClick = { onResetPose(currentPose.phaseId) }) { Text("Reset") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onResetImageDetection(currentPose.phaseId) }) { Text("Reset detection") }
                    Button(onClick = { onClearReferenceImage(currentPose.phaseId); workingImageUri = null }) { Text("Clear image") }
                    Button(onClick = { onSavePhasePose(currentPose.phaseId) }) { Text("Save phase pose") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { selectedJoint?.let { onResetJointCorrection(currentPose.phaseId, it) } }, enabled = selectedJoint != null) {
                        Text("Reset selected joint")
                    }
                    Button(onClick = { onResetAllCorrections(currentPose.phaseId) }) { Text("Reset all corrections") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Frame")
                    Switch(checked = guides.showFrameGuides, onCheckedChange = {
                        onUpdatePhaseGuides(currentPose.phaseId, guides.copy(showFrameGuides = it))
                    })
                    Text("Floor")
                    Switch(checked = guides.showFloorLine, onCheckedChange = {
                        onUpdatePhaseGuides(currentPose.phaseId, guides.copy(showFloorLine = it))
                    })
                    Text("Wall")
                    Switch(checked = guides.showWallLine, onCheckedChange = {
                        onUpdatePhaseGuides(currentPose.phaseId, guides.copy(showWallLine = it))
                    })
                    Text("Bar")
                    Switch(checked = guides.showBarLine, onCheckedChange = {
                        onUpdatePhaseGuides(currentPose.phaseId, guides.copy(showBarLine = it))
                    })
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
private fun PoseCanvas(
    phasePose: PhasePoseTemplate,
    bodyProfile: UserBodyProfile?,
    referenceImage: ImageBitmap?,
    guides: PhaseBoundaryGuides,
    onJointMoved: (String, JointPoint) -> Unit,
) {
    var activeJoint by remember(phasePose.phaseId) { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        OverlaySkeletonPreview(
            joints = phasePose.joints,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(phasePose.phaseId, phasePose.joints) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val touch = JointPoint(start.x / size.width, start.y / size.height)
                            activeJoint = DrillStudioPoseUtils.nearestJointWithinRadius(
                                joints = phasePose.joints,
                                touch = touch,
                                hitRadius = 0.08f,
                            )
                        },
                        onDrag = { change, _ ->
                            val joint = activeJoint ?: return@detectDragGestures
                            val x = (change.position.x / size.width).coerceIn(0f, 1f)
                            val y = (change.position.y / size.height).coerceIn(0f, 1f)
                            val constrained = DrillStudioPoseUtils.applyAnatomicalGuardrails(
                                pose = phasePose.joints,
                                joint = joint,
                                target = JointPoint(x, y),
                                bodyProfile = bodyProfile,
                            )
                            onJointMoved(joint, constrained)
                        },
                        onDragEnd = { activeJoint = null },
                        onDragCancel = { activeJoint = null },
                    )
                },
            style = OverlaySkeletonPreviewStyle(
                aspectRatio = SeededSkeletonPreviewDefaults.PORTRAIT_ASPECT_RATIO,
                contentPaddingFraction = 0f,
                styleScaleMultiplier = 1f,
            ),
            highlightedJoint = activeJoint,
            showBackground = false,
        )
        ) {
            referenceImage?.let { image ->
                drawImage(image)
            }
            if (guides.showFrameGuides) {
                drawRect(color = Color(0x44FFFFFF), style = Stroke(width = 2f))
                val corner = 24f
                drawLine(Color(0x99FFFFFF), Offset(0f, 0f), Offset(corner, 0f), 2f)
                drawLine(Color(0x99FFFFFF), Offset(0f, 0f), Offset(0f, corner), 2f)
                drawLine(Color(0x99FFFFFF), Offset(size.width, 0f), Offset(size.width - corner, 0f), 2f)
                drawLine(Color(0x99FFFFFF), Offset(size.width, 0f), Offset(size.width, corner), 2f)
                drawLine(Color(0x99FFFFFF), Offset(0f, size.height), Offset(corner, size.height), 2f)
                drawLine(Color(0x99FFFFFF), Offset(0f, size.height), Offset(0f, size.height - corner), 2f)
                drawLine(Color(0x99FFFFFF), Offset(size.width, size.height), Offset(size.width - corner, size.height), 2f)
                drawLine(Color(0x99FFFFFF), Offset(size.width, size.height), Offset(size.width, size.height - corner), 2f)
            }
            if (guides.showFloorLine) {
                val y = guides.floorLineY.coerceIn(0f, 1f) * size.height
                drawLine(Color(0xAA4FC3F7), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            }
            if (guides.showWallLine) {
                val x = guides.wallLineX.coerceIn(0f, 1f) * size.width
                drawLine(Color(0xAAF48FB1), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            }
            if (guides.showBarLine) {
                val y = guides.barLineY.coerceIn(0f, 1f) * size.height
                drawLine(Color(0xAAFFE082), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            }
            canonicalStudioBones().forEach { (start, end) ->
                val a = phasePose.joints[start]
                val b = phasePose.joints[end]
                if (a != null && b != null) {
                    drawLine(
                        color = baseJointColor,
                        start = Offset(a.x * size.width, a.y * size.height),
                        end = Offset(b.x * size.width, b.y * size.height),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            phasePose.joints.forEach { (name, point) ->
                val style = jointStyle(name, baseJointColor, 6f)
                drawCircle(
                    color = style.color,
                    radius = style.radius,
                    center = Offset(point.x * size.width, point.y * size.height),
                    style = Stroke(width = 3f),
                )
            }
        }
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

private fun String.pretty(): String = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
