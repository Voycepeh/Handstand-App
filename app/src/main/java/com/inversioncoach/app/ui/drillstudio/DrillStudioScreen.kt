package com.inversioncoach.app.ui.drillstudio

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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.StickFigureAnimator
import com.inversioncoach.app.overlay.OverlaySkeletonSpec
import com.inversioncoach.app.overlay.jointStyle
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.DropdownOption
import com.inversioncoach.app.ui.components.MultiSelectChipsField
import com.inversioncoach.app.ui.components.ReliableDropdownField
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.isActive

@Composable
fun DrillStudioScreen(
    onBack: () -> Unit,
    initRequest: DrillStudioInitRequest,
) {
    val context = LocalContext.current
    val vm = remember {
        DrillStudioViewModel(
            repository = DrillCatalogRepository(context),
            sessionRepository = ServiceLocator.repository(context),
        )
    }
    var bodyProfile by remember { mutableStateOf<UserBodyProfile?>(null) }
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(initRequest.mode, initRequest.drillId, initRequest.templateId) {
        vm.initialize(initRequest)
    }
    LaunchedEffect(Unit) {
        bodyProfile = runCatching { ServiceLocator.repository(context).getUserBodyProfile() }.getOrNull()
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
                editingDrillId = state.editingDrillId,
                editingTemplateId = state.editingTemplateId,
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
                onSaveDraft = vm::saveDraft,
                onSaveAndMarkReady = vm::saveAndMarkReady,
                onSaveTemplate = vm::saveTemplate,
                onSaveAsNewTemplate = vm::saveAsNewTemplate,
                bodyProfile = bodyProfile,
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
    editingDrillId: String?,
    editingTemplateId: String?,
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
    onSaveDraft: () -> Unit,
    onSaveAndMarkReady: () -> Unit,
    onSaveTemplate: (Boolean) -> Unit,
    onSaveAsNewTemplate: (Boolean) -> Unit,
    bodyProfile: UserBodyProfile?,
) {
    val phasePoses = draft.skeletonTemplate.phasePoses
    var selectedPhaseId by remember(draft.id, phasePoses) {
        mutableStateOf(phasePoses.firstOrNull()?.phaseId ?: draft.phases.first().id)
    }
    var previewProgress by remember(draft.id) { mutableFloatStateOf(0f) }
    var autoPlay by remember(draft.id) { mutableStateOf(true) }
    var advancedMode by remember(draft.id) { mutableStateOf(false) }
    var setAsBaseline by remember(draft.id) { mutableStateOf(false) }
    var selectedJoint by remember(draft.id, selectedPhaseId) {
        mutableStateOf(phasePoses.firstOrNull { it.phaseId == selectedPhaseId }?.joints?.keys?.firstOrNull())
    }
    val orderedPhases = draft.phases.sortedBy { it.order }
    val selectedPhaseTemplate = orderedPhases.firstOrNull { it.id == selectedPhaseId }
    val hasPersistedDrillId = !editingDrillId.isNullOrBlank()

    LaunchedEffect(orderedPhases.map { it.id }, selectedPhaseId) {
        selectedPhaseId = DrillStudioPhaseEditor.recoverSelectionAfterDelete(
            remainingPhaseIds = orderedPhases.map { it.id },
            availablePosePhaseIds = phasePoses.map { it.phaseId },
            previousSelectedPhaseId = selectedPhaseId,
        ) ?: selectedPhaseId
    }

    LaunchedEffect(autoPlay, draft.id, draft.skeletonTemplate.framesPerSecond) {
        if (!autoPlay) return@LaunchedEffect
        while (isActive) {
            val fps = draft.skeletonTemplate.framesPerSecond.coerceAtLeast(1)
            previewProgress += 1f / fps.toFloat()
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
                PoseCanvas(
                    phasePose = currentPose,
                    bodyProfile = bodyProfile,
                    onJointMoved = { joint, point -> onUpdatePhasePoseJoint(currentPose.phaseId, joint, point) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCopyPreviousPose(currentPose.phaseId) }) { Text("Copy previous") }
                    Button(onClick = { onMirrorPose(currentPose.phaseId) }) { Text("Mirror") }
                    Button(onClick = { onResetPose(currentPose.phaseId) }) { Text("Reset") }
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
                Button(onClick = onSaveDraft) { Text("Save Draft") }
                Button(onClick = onSaveAndMarkReady) { Text("Validate and Save") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (editingTemplateId != null) {
                    Button(onClick = { onSaveTemplate(setAsBaseline) }, enabled = hasPersistedDrillId) { Text("Save Template") }
                }
                Button(onClick = { onSaveAsNewTemplate(setAsBaseline) }, enabled = hasPersistedDrillId) { Text("Save as New Template") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set as baseline")
                Switch(
                    checked = setAsBaseline,
                    onCheckedChange = { setAsBaseline = it },
                    enabled = hasPersistedDrillId,
                )
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
    onJointMoved: (String, JointPoint) -> Unit,
) {
    var activeJoint by remember(phasePose.phaseId) { mutableStateOf<String?>(null) }
    val baseJointColor = Color(0xFF7CF0A9)
    Box(modifier = Modifier.fillMaxWidth().height(STUDIO_STAGE_HEIGHT).background(MaterialTheme.colorScheme.surface)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
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
                    )
                },
        ) {
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
private fun PreviewCard(drill: DrillTemplate, progress: Float) {
    val pose = remember(drill.id, progress, drill.skeletonTemplate.keyframes) {
        DrillStudioPoseUtils.normalizeJointNames(StickFigureAnimator.poseAt(drill.skeletonTemplate, progress))
    }
    val baseJointColor = Color(0xFF7CF0A9)
    Canvas(modifier = Modifier.fillMaxWidth().height(STUDIO_STAGE_HEIGHT).background(MaterialTheme.colorScheme.surface)) {
        canonicalStudioBones().forEach { (start, end) ->
            val a = pose[start]
            val b = pose[end]
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
        pose.forEach { (name, point) ->
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

private val STUDIO_STAGE_HEIGHT = 260.dp

private fun canonicalStudioBones(): List<Pair<String, String>> =
    OverlaySkeletonSpec.sideConnections("left") +
        OverlaySkeletonSpec.sideConnections("right") +
        OverlaySkeletonSpec.bilateralConnectors
