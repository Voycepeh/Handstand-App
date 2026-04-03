package com.inversioncoach.app.ui.drills

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.inversioncoach.app.drills.catalog.CatalogAnalysisPlane
import com.inversioncoach.app.drills.catalog.CatalogCameraView
import com.inversioncoach.app.drills.catalog.CatalogComparisonMode
import com.inversioncoach.app.drills.studio.DrillStudioDocument
import com.inversioncoach.app.drills.studio.DrillStudioPhase
import com.inversioncoach.app.drills.studio.DrillStudioThresholdRegistry
import com.inversioncoach.app.motion.BodyJoint
import com.inversioncoach.app.motion.NormalizedPoint
import com.inversioncoach.app.ui.components.DrillPreviewAnimation
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillStudioScreen(onBack: () -> Unit, initialDrillId: String? = null) {
    val context = LocalContext.current
    val vm: DrillStudioEditorViewModel = viewModel { DrillStudioEditorViewModel(context) }
    val editorState by vm.state.collectAsState()
    val drills = editorState.drills
    val selectedDrillId = editorState.selectedDrillId
    val document = editorState.working
    val hasUnsavedChanges = editorState.hasUnsavedChanges
    val selectedMeta = editorState.selectedMeta

    var selectedPhaseId by remember { mutableStateOf("") }
    var selectedJoint by remember { mutableStateOf(BodyJoint.HEAD) }
    var mirroredPreview by remember { mutableStateOf(false) }
    var showAdvancedMetadata by remember(selectedDrillId) { mutableStateOf(false) }

    LaunchedEffect(initialDrillId) {
        vm.initialize(initialDrillId)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.importDraft(uri)
    }

    ScaffoldedScreen(title = "Drill Studio", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            editorState.status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text(
                buildString {
                    append(if (selectedMeta?.seeded == true) "Seeded drill" else "Local drill")
                    append(" • Draft: ")
                    append(if (selectedMeta?.hasDraft == true) "yes" else "no")
                    append(" • Unsaved changes: ")
                    append(if (hasUnsavedChanges) "yes" else "no")
                },
            )

            DrillDropdown(
                label = "Drill",
                selected = selectedDrillId,
                options = drills.associate { it.id to it.name },
            ) { newId ->
                vm.selectDrill(newId)
                selectedPhaseId = ""
            }

            document?.let { draft ->
                LaunchedEffect(draft.phases, selectedPhaseId) {
                    selectedPhaseId = coerceSelectedPhaseId(draft, selectedPhaseId)
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Live Preview", style = MaterialTheme.typography.titleMedium)
                        DrillPreviewAnimation(
                            animationSpec = draft.animationSpec,
                            mirrored = mirroredPreview,
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Mirror preview")
                            Switch(checked = mirroredPreview, onCheckedChange = { mirroredPreview = it })
                        }
                    }
                }

                PhaseRail(
                    draft = draft,
                    selectedPhaseId = selectedPhaseId,
                    onSelectPhase = { phaseId -> selectedPhaseId = phaseId },
                )

                val phase = resolveSelectedPhase(draft, selectedPhaseId)
                if (phase != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Phase: ${phase.label} (#${phase.order})")
                            Text("Progress window: ${"%.2f".format(phase.progressWindow.start)} - ${"%.2f".format(phase.progressWindow.end)}")
                            Slider(
                                value = phase.progressWindow.start,
                                onValueChange = { value ->
                                    val updatedPhases = draft.phases.map { existing ->
                                        if (existing.id == phase.id) {
                                            existing.copy(progressWindow = phase.progressWindow.copy(start = min(value, phase.progressWindow.end)))
                                        } else {
                                            existing
                                        }
                                    }
                                    vm.updateWorking(draft.copy(phases = updatedPhases))
                                },
                                valueRange = 0f..1f,
                            )
                            Slider(
                                value = phase.progressWindow.end,
                                onValueChange = { value ->
                                    val updatedPhases = draft.phases.map { existing ->
                                        if (existing.id == phase.id) {
                                            existing.copy(progressWindow = phase.progressWindow.copy(end = max(value, phase.progressWindow.start)))
                                        } else {
                                            existing
                                        }
                                    }
                                    vm.updateWorking(draft.copy(phases = updatedPhases))
                                },
                                valueRange = 0f..1f,
                            )
                        }
                    }
                }

                Button(
                    onClick = { showAdvancedMetadata = !showAdvancedMetadata },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showAdvancedMetadata) "Hide Advanced Metadata" else "Show Advanced Metadata")
                }
                if (showAdvancedMetadata) {
                    OutlinedTextField(draft.displayName, { value -> vm.updateWorking(draft.copy(displayName = value)) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
                    DrillDropdown(
                        label = "Camera view",
                        selected = draft.cameraView.name,
                        options = CatalogCameraView.entries.associate { it.name to it.name },
                    ) { value ->
                        val view = CatalogCameraView.valueOf(value)
                        val updatedSupported = if (draft.supportedViews.contains(view)) draft.supportedViews else (draft.supportedViews + view)
                        vm.updateWorking(draft.copy(cameraView = view, supportedViews = updatedSupported))
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Supported views")
                            CatalogCameraView.entries.forEach { view ->
                                val checked = draft.supportedViews.contains(view)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(checked = checked, onCheckedChange = { isChecked ->
                                        val next = if (isChecked) {
                                            (draft.supportedViews + view).distinct()
                                        } else {
                                            draft.supportedViews.filterNot { it == view }
                                        }
                                        if (next.isNotEmpty()) {
                                            val nextCamera = if (next.contains(draft.cameraView)) draft.cameraView else next.first()
                                            vm.updateWorking(draft.copy(supportedViews = next, cameraView = nextCamera))
                                        }
                                    })
                                    Text(view.name)
                                }
                            }
                        }
                    }
                    DrillDropdown(
                        label = "Analysis plane",
                        selected = draft.analysisPlane.name,
                        options = CatalogAnalysisPlane.entries.associate { it.name to it.name },
                    ) { value -> vm.updateWorking(draft.copy(analysisPlane = CatalogAnalysisPlane.valueOf(value))) }
                    DrillDropdown(
                        label = "Comparison mode",
                        selected = draft.comparisonMode.name,
                        options = CatalogComparisonMode.entries.associate { it.name to it.name },
                    ) { value -> vm.updateWorking(draft.copy(comparisonMode = CatalogComparisonMode.valueOf(value))) }
                    ThresholdEditor(draft = draft) { updated -> vm.updateWorking(updated) }
                }
                JointEditor(
                    draft = draft,
                    selectedPhaseId = selectedPhaseId,
                    selectedJoint = selectedJoint,
                    onJointSelected = { selectedJoint = it },
                    onUpdated = { updated -> vm.updateWorking(updated) },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = vm::saveDraft) { Text("Save draft") }

                    Button(onClick = vm::resetDraft) { Text("Reset") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = vm::duplicateSelected) { Text("Duplicate") }
                    Button(onClick = { picker.launch(arrayOf("application/json")) }) { Text("Import JSON") }
                }

                Button(onClick = {
                    vm.exportDraft { exported ->
                        if (exported == null) return@exportDraft
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exported)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share drill draft"))
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export & Share JSON")
                }
            }
        }
    }
}

@Composable
private fun PhaseRail(
    draft: DrillStudioDocument,
    selectedPhaseId: String,
    onSelectPhase: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        draft.phases.forEach { phase ->
            val isSelected = phase.id == selectedPhaseId
            val frameIndex = resolveFrameIndexForPhase(draft, phase)
            Card(
                modifier = Modifier.size(width = 160.dp, height = 96.dp).clickable { onSelectPhase(phase.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${phase.order}. ${phase.label}", style = MaterialTheme.typography.titleSmall)
                    Text("Phase ${phase.order}", style = MaterialTheme.typography.bodySmall)
                    Text("Frame ${frameIndex + 1}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrillDropdown(label: String, selected: String, options: Map<String, String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = options[selected] ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Text(if (expanded) "▲" else "▼") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ThresholdEditor(draft: DrillStudioDocument, onUpdated: (DrillStudioDocument) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Metric thresholds")
            draft.metricThresholds.forEach { (key, value) ->
                val metadata = DrillStudioThresholdRegistry.forMetric(key)
                Text("${metadata.label}: ${"%.2f".format(value)} ${metadata.unit}")
                Slider(
                    value = value,
                    onValueChange = { next ->
                        onUpdated(draft.copy(metricThresholds = draft.metricThresholds + (key to next)))
                    },
                    valueRange = metadata.min..metadata.max,
                    steps = (((metadata.max - metadata.min) / metadata.step).toInt() - 1).coerceAtLeast(0),
                )
            }
        }
    }
}

@Composable
private fun JointEditor(
    draft: DrillStudioDocument,
    selectedPhaseId: String,
    selectedJoint: BodyJoint,
    onJointSelected: (BodyJoint) -> Unit,
    onUpdated: (DrillStudioDocument) -> Unit,
) {
    val resolvedFrameIndex = resolveFrameIndexForSelectedPhase(draft, selectedPhaseId)
    if (resolvedFrameIndex < 0) return
    val frame = draft.animationSpec.keyframes.getOrNull(resolvedFrameIndex)
    val joints = frame?.joints.orEmpty()
    val point = joints[selectedJoint] ?: NormalizedPoint(0.5f, 0.5f)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DrillDropdown(
                label = "Joint",
                selected = selectedJoint.name,
                options = BodyJoint.entries.associate { it.name to it.name },
                onSelect = { name -> onJointSelected(BodyJoint.entries.first { it.name == name }) },
            )
            Text("${selectedJoint.name}: x=${"%.2f".format(point.x)} y=${"%.2f".format(point.y)}")
            Slider(value = point.x, onValueChange = { x -> onUpdated(updateJointForSelectedPhase(draft, selectedPhaseId, selectedJoint, x, point.y)) }, valueRange = 0f..1f)
            Slider(value = point.y, onValueChange = { y -> onUpdated(updateJointForSelectedPhase(draft, selectedPhaseId, selectedJoint, point.x, y)) }, valueRange = 0f..1f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onUpdated(updateJointForSelectedPhase(draft, selectedPhaseId, selectedJoint, (point.x - 0.01f).coerceAtLeast(0f), point.y)) }) { Text("X-") }
                Button(onClick = { onUpdated(updateJointForSelectedPhase(draft, selectedPhaseId, selectedJoint, (point.x + 0.01f).coerceAtMost(1f), point.y)) }) { Text("X+") }
                Button(onClick = { onUpdated(updateJointForSelectedPhase(draft, selectedPhaseId, selectedJoint, point.x, (point.y - 0.01f).coerceAtLeast(0f))) }) { Text("Y-") }
                Button(onClick = { onUpdated(updateJointForSelectedPhase(draft, selectedPhaseId, selectedJoint, point.x, (point.y + 0.01f).coerceAtMost(1f))) }) { Text("Y+") }
            }
        }
    }
}

internal fun coerceSelectedPhaseId(draft: DrillStudioDocument, selectedPhaseId: String): String {
    if (selectedPhaseId.isNotBlank() && draft.phases.any { it.id == selectedPhaseId }) return selectedPhaseId
    return draft.phases.firstOrNull()?.id.orEmpty()
}

internal fun resolveSelectedPhase(draft: DrillStudioDocument, selectedPhaseId: String): DrillStudioPhase? =
    draft.phases.firstOrNull { it.id == selectedPhaseId }

internal fun resolveFrameIndexForSelectedPhase(draft: DrillStudioDocument, selectedPhaseId: String): Int {
    val phase = resolveSelectedPhase(draft, selectedPhaseId) ?: return -1
    return resolveFrameIndexForPhase(draft, phase)
}

internal fun resolveFrameIndexForPhase(draft: DrillStudioDocument, phase: DrillStudioPhase): Int {
    val keyframes = draft.animationSpec.keyframes
    if (keyframes.isEmpty()) return -1
    val anchor = phase.anchorKeyframeName
    if (anchor != null) {
        val anchored = keyframes.indexOfFirst { it.name == anchor }
        if (anchored >= 0) return anchored
    }
    val midpoint = (phase.progressWindow.start + phase.progressWindow.end) / 2f
    return keyframes.withIndex().minByOrNull { (_, frame) -> kotlin.math.abs(frame.progress - midpoint) }?.index ?: 0
}

internal fun updateJoint(draft: DrillStudioDocument, frameIndex: Int, joint: BodyJoint, x: Float, y: Float): DrillStudioDocument {
    val frames = draft.animationSpec.keyframes.toMutableList()
    val target = frames.getOrNull(frameIndex) ?: return draft
    val safeJoints = target.joints.orEmpty()
    frames[frameIndex] = target.copy(joints = safeJoints + (joint to NormalizedPoint(x, y)))
    return draft.copy(animationSpec = draft.animationSpec.copy(keyframes = frames))
}

internal fun updateJointForSelectedPhase(
    draft: DrillStudioDocument,
    selectedPhaseId: String,
    joint: BodyJoint,
    x: Float,
    y: Float,
): DrillStudioDocument {
    val frameIndex = resolveFrameIndexForSelectedPhase(draft, selectedPhaseId)
    if (frameIndex < 0) return draft
    return updateJoint(draft, frameIndex, joint, x, y)
}
