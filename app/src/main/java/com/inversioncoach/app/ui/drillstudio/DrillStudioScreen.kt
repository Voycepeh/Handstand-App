package com.inversioncoach.app.ui.drillstudio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.catalog.AnalysisPlane
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.drills.catalog.DrillCatalogRepository
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.StickFigureAnimator
import com.inversioncoach.app.ui.components.DropdownOption
import com.inversioncoach.app.ui.components.MultiSelectChipsField
import com.inversioncoach.app.ui.components.ReliableDropdownField
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.storage.ServiceLocator
import kotlinx.coroutines.isActive

@Composable
fun DrillStudioScreen(
    onBack: () -> Unit,
    initRequest: DrillStudioInitRequest,
) {
    val context = LocalContext.current
    val vm = remember { DrillStudioViewModel(repository = DrillCatalogRepository(context)) }
    var bodyProfile by remember { mutableStateOf<UserBodyProfile?>(null) }
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(initRequest.mode, initRequest.drillId) {
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
                onUpdateDraft = vm::updateDraft,
                onAddPhase = vm::addPhase,
                onDuplicatePhase = vm::duplicatePhase,
                onDeletePhase = vm::deletePhase,
                onRenamePhase = vm::renamePhase,
                onCopyPreviousPose = vm::copyPreviousPose,
                onMirrorPose = vm::mirrorPose,
                onResetPose = vm::resetPose,
                onUpdatePhasePoseJoint = vm::updatePhasePoseJoint,
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
    onUpdateDraft: ((DrillTemplate) -> DrillTemplate) -> Unit,
    onAddPhase: () -> Unit,
    onDuplicatePhase: (String) -> Unit,
    onDeletePhase: (String) -> Unit,
    onRenamePhase: (String, String) -> Unit,
    onCopyPreviousPose: (String) -> Unit,
    onMirrorPose: (String) -> Unit,
    onResetPose: (String) -> Unit,
    onUpdatePhasePoseJoint: (String, String, JointPoint) -> Unit,
    bodyProfile: UserBodyProfile?,
) {
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
    val orderedPhases = draft.phases.sortedBy { it.order }
    val phaseNameDrafts = remember(orderedPhases.map { it.id to it.label }) {
        orderedPhases.associate { it.id to it.label }.toMutableMap()
    }
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
    val planeOptions = remember { AnalysisPlane.entries.map { DropdownOption(it, it.name.pretty()) } }
    val movementOptions = remember { CatalogMovementType.entries.map { DropdownOption(it, it.name.pretty()) } }
    val comparisonOptions = remember { ComparisonMode.entries.map { DropdownOption(it, it.name.pretty()) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Drill info") {
                Text("Editing: ${draft.title}")
                Text(if (sourceSeedId == null) "Custom draft" else "Seeded source: $sourceSeedId")
                ReliableDropdownField(
                    label = "Movement type",
                    selected = movementOptions.firstOrNull { it.value == draft.movementType } ?: movementOptions.first(),
                    options = movementOptions,
                    onOptionSelected = { option -> onUpdateDraft { it.copy(movementType = option.value) } },
                )
                ReliableDropdownField(
                    label = "Comparison mode",
                    selected = comparisonOptions.firstOrNull { it.value == draft.comparisonMode } ?: comparisonOptions.first(),
                    options = comparisonOptions,
                    onOptionSelected = { option -> onUpdateDraft { it.copy(comparisonMode = option.value) } },
                )
            }
        }

        item {
            SectionCard(title = "Phase strip") {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    orderedPhases.forEach { phase ->
                        val selected = phase.id == selectedPhaseId
                        val phasePose = phasePoses.firstOrNull { it.phaseId == phase.id }
                        Card(
                            modifier = Modifier.width(190.dp).border(
                                width = if (selected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp),
                            ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val text = phaseNameDrafts[phase.id] ?: phase.label
                                BasicTextField(
                                    value = text,
                                    onValueChange = {
                                        phaseNameDrafts[phase.id] = it
                                    },
                                )
                                Text("hold: ${phasePose?.holdDurationMs ?: 0} ms")
                                Text("transition: ${phasePose?.transitionDurationMs ?: 700} ms")
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(onClick = { onRenamePhase(phase.id, phaseNameDrafts[phase.id] ?: phase.label) }) { Text("Save") }
                                    Button(onClick = { selectedPhaseId = phase.id }) { Text("Edit") }
                                    Button(onClick = { onDuplicatePhase(phase.id) }) { Text("Dup") }
                                    Button(onClick = { onDeletePhase(phase.id) }, enabled = draft.phases.size > 1) { Text("Del") }
                                }
                            }
                        }
                    }
                    Button(onClick = onAddPhase) { Text("+ Phase") }
                }
            }
        }

        item {
            SectionCard(title = "Pose canvas") {
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
                    AxisEditor(
                        label = "X",
                        value = current.x,
                        onUpdate = { x -> onUpdatePhasePoseJoint(currentPose.phaseId, selected.value, current.copy(x = x)) },
                    )
                    AxisEditor(
                        label = "Y",
                        value = current.y,
                        onUpdate = { y -> onUpdatePhasePoseJoint(currentPose.phaseId, selected.value, current.copy(y = y)) },
                    )
                }
            }
        }

        item {
            SectionCard(title = "View config") {
                MultiSelectChipsField(
                    label = "Supported views",
                    options = cameraOptions,
                    selectedValues = draft.supportedViews.toSet(),
                    onToggle = { view ->
                        onUpdateDraft { current ->
                            val next = if (view in current.supportedViews) current.supportedViews - view else current.supportedViews + view
                            current.copy(supportedViews = next.ifEmpty { listOf(current.cameraView) }.distinct())
                        }
                    },
                )
                ReliableDropdownField(
                    label = "Primary/default view",
                    selected = cameraOptions.firstOrNull { it.value == draft.cameraView } ?: cameraOptions.first(),
                    options = cameraOptions.filter { it.value in draft.supportedViews },
                    onOptionSelected = { option -> onUpdateDraft { it.copy(cameraView = option.value) } },
                )
                ReliableDropdownField(
                    label = "Analysis plane",
                    selected = planeOptions.firstOrNull { it.value == draft.analysisPlane } ?: planeOptions.first(),
                    options = planeOptions,
                    onOptionSelected = { option -> onUpdateDraft { it.copy(analysisPlane = option.value) } },
                )
            }
        }
    }
}

@Composable
private fun AxisEditor(label: String, value: Float, onUpdate: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${"%.2f".format(value)}")
        Slider(value = value, onValueChange = onUpdate, valueRange = 0f..1f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onUpdate((value - 0.01f).coerceIn(0f, 1f)) }) { Text("- nudge") }
            Button(onClick = { onUpdate((value + 0.01f).coerceIn(0f, 1f)) }) { Text("+ nudge") }
        }
    }
}

@Composable
private fun PoseCanvas(
    phasePose: PhasePoseTemplate,
    bodyProfile: UserBodyProfile?,
    onJointMoved: (String, JointPoint) -> Unit,
) {
    var activeJoint by remember(phasePose.phaseId) { mutableStateOf<String?>(null) }
    Box(modifier = Modifier.fillMaxWidth().height(260.dp).background(MaterialTheme.colorScheme.surface)) {
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
            StickFigureAnimator.canonicalBones.forEach { (start, end) ->
                val a = phasePose.joints[start]
                val b = phasePose.joints[end]
                if (a != null && b != null) {
                    drawLine(
                        color = MaterialTheme.colorScheme.primary,
                        start = Offset(a.x * size.width, a.y * size.height),
                        end = Offset(b.x * size.width, b.y * size.height),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            phasePose.joints.values.forEach { point ->
                drawCircle(
                    color = MaterialTheme.colorScheme.secondary,
                    radius = 7f,
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
        StickFigureAnimator.poseAt(drill.skeletonTemplate, progress)
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.surface)) {
        StickFigureAnimator.canonicalBones.forEach { (start, end) ->
            val a = pose[start]
            val b = pose[end]
            if (a != null && b != null) {
                drawLine(
                    color = MaterialTheme.colorScheme.primary,
                    start = Offset(a.x * size.width, a.y * size.height),
                    end = Offset(b.x * size.width, b.y * size.height),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
        }
        pose.values.forEach { point ->
            drawCircle(
                color = MaterialTheme.colorScheme.secondary,
                radius = 6f,
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
import com.inversioncoach.app.calibration.UserBodyProfile
