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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.inversioncoach.app.drills.catalog.CatalogAnalysisPlane
import com.inversioncoach.app.drills.catalog.CatalogCameraView
import com.inversioncoach.app.drills.catalog.CatalogComparisonMode
import com.inversioncoach.app.drills.studio.DrillCatalogDraftStore
import com.inversioncoach.app.drills.studio.DrillCatalogImportExportManager
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
    val store = remember { DrillCatalogDraftStore(context) }
    val importExport = remember { DrillCatalogImportExportManager(context, store) }

    var drills by remember { mutableStateOf(store.listDrills()) }
    var selectedDrillId by remember { mutableStateOf(initialDrillId ?: drills.firstOrNull()?.id.orEmpty()) }
    var baseline by remember(selectedDrillId, drills.size) { mutableStateOf(if (selectedDrillId.isBlank()) null else store.loadForEditor(selectedDrillId)) }
    var working by remember(selectedDrillId, drills.size) { mutableStateOf(baseline) }
    var selectedPhase by remember { mutableIntStateOf(0) }
    var selectedJoint by remember { mutableStateOf(BodyJoint.HEAD) }
    var mirroredPreview by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val imported = importExport.importDraft(uri)
            drills = store.listDrills()
            selectedDrillId = imported.id
            baseline = store.loadForEditor(imported.id)
            working = baseline
            status = "Imported ${imported.displayName}"
        }.onFailure {
            status = "Import failed: ${it.message}"
        }
    }

    val document = working
    val persisted = baseline
    val hasUnsavedChanges = document != null && persisted != null && document != persisted
    val selectedMeta = drills.firstOrNull { it.id == selectedDrillId }

    ScaffoldedScreen(title = "Drill Studio", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
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
                selectedDrillId = newId
                baseline = store.loadForEditor(newId)
                working = baseline
                selectedPhase = 0
                status = null
            }

            document?.let { draft ->
                OutlinedTextField(draft.displayName, { value -> working = draft.copy(displayName = value) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())

                DrillDropdown(
                    label = "Camera view",
                    selected = draft.cameraView.name,
                    options = CatalogCameraView.entries.associate { it.name to it.name },
                ) { value ->
                    val view = CatalogCameraView.valueOf(value)
                    val updatedSupported = if (draft.supportedViews.contains(view)) draft.supportedViews else (draft.supportedViews + view)
                    working = draft.copy(cameraView = view, supportedViews = updatedSupported)
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
                                        working = draft.copy(supportedViews = next, cameraView = nextCamera)
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
                ) { value -> working = draft.copy(analysisPlane = CatalogAnalysisPlane.valueOf(value)) }
                DrillDropdown(
                    label = "Comparison mode",
                    selected = draft.comparisonMode.name,
                    options = CatalogComparisonMode.entries.associate { it.name to it.name },
                ) { value -> working = draft.copy(comparisonMode = CatalogComparisonMode.valueOf(value)) }

                DrillDropdown(
                    label = "Phase",
                    selected = draft.phases.getOrNull(selectedPhase)?.id.orEmpty(),
                    options = draft.phases.mapIndexed { index, phase -> index.toString() to "${phase.order}. ${phase.label}" }.toMap(),
                ) { phaseIndex -> selectedPhase = phaseIndex.toIntOrNull() ?: 0 }

                val phase = draft.phases.getOrNull(selectedPhase)
                if (phase != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Phase: ${phase.label} (#${phase.order})")
                            Text("Progress window: ${"%.2f".format(phase.progressWindow.start)} - ${"%.2f".format(phase.progressWindow.end)}")
                            Slider(
                                value = phase.progressWindow.start,
                                onValueChange = { value ->
                                    val updatedPhases = draft.phases.toMutableList()
                                    updatedPhases[selectedPhase] = phase.copy(progressWindow = phase.progressWindow.copy(start = min(value, phase.progressWindow.end)))
                                    working = draft.copy(phases = updatedPhases)
                                },
                                valueRange = 0f..1f,
                            )
                            Slider(
                                value = phase.progressWindow.end,
                                onValueChange = { value ->
                                    val updatedPhases = draft.phases.toMutableList()
                                    updatedPhases[selectedPhase] = phase.copy(progressWindow = phase.progressWindow.copy(end = max(value, phase.progressWindow.start)))
                                    working = draft.copy(phases = updatedPhases)
                                },
                                valueRange = 0f..1f,
                            )
                        }
                    }
                }

                ThresholdEditor(draft = draft) { updated -> working = updated }
                JointEditor(
                    draft = draft,
                    phaseIndex = selectedPhase,
                    selectedJoint = selectedJoint,
                    onJointSelected = { selectedJoint = it },
                    onUpdated = { updated -> working = updated },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Mirror preview")
                    Switch(checked = mirroredPreview, onCheckedChange = { mirroredPreview = it })
                }

                DrillPreviewAnimation(
                    animationSpec = draft.animationSpec,
                    mirrored = mirroredPreview,
                    modifier = Modifier.size(190.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        store.saveDraft(draft)
                        drills = store.listDrills()
                        baseline = store.loadForEditor(draft.id)
                        working = baseline
                        status = "Draft saved"
                    }) { Text("Save draft") }

                    Button(onClick = {
                        store.resetDraft(draft.id)
                        drills = store.listDrills()
                        val fallback = drills.firstOrNull()?.id.orEmpty()
                        selectedDrillId = if (drills.any { it.id == draft.id }) draft.id else fallback
                        baseline = if (selectedDrillId.isBlank()) null else store.loadForEditor(selectedDrillId)
                        working = baseline
                        status = "Draft reset"
                    }) { Text("Reset") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        val duplicate = store.duplicate(draft.id)
                        drills = store.listDrills()
                        selectedDrillId = duplicate.id
                        baseline = store.loadForEditor(duplicate.id)
                        working = baseline
                        status = "Duplicated to ${duplicate.displayName}"
                    }) { Text("Duplicate") }
                    Button(onClick = { picker.launch(arrayOf("application/json")) }) { Text("Import JSON") }
                }

                Button(onClick = {
                    val exported = importExport.exportDraft(draft)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exported)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share drill draft"))
                    status = "Exported ${exported.name}"
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export & Share JSON")
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
    phaseIndex: Int,
    selectedJoint: BodyJoint,
    onJointSelected: (BodyJoint) -> Unit,
    onUpdated: (DrillStudioDocument) -> Unit,
) {
    val phase = draft.phases.getOrNull(phaseIndex) ?: return
    val resolvedFrameIndex = resolveFrameIndexForPhase(draft, phase)
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
            Slider(value = point.x, onValueChange = { x -> onUpdated(updateJoint(draft, resolvedFrameIndex, selectedJoint, x, point.y)) }, valueRange = 0f..1f)
            Slider(value = point.y, onValueChange = { y -> onUpdated(updateJoint(draft, resolvedFrameIndex, selectedJoint, point.x, y)) }, valueRange = 0f..1f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onUpdated(updateJoint(draft, resolvedFrameIndex, selectedJoint, (point.x - 0.01f).coerceAtLeast(0f), point.y)) }) { Text("X-") }
                Button(onClick = { onUpdated(updateJoint(draft, resolvedFrameIndex, selectedJoint, (point.x + 0.01f).coerceAtMost(1f), point.y)) }) { Text("X+") }
                Button(onClick = { onUpdated(updateJoint(draft, resolvedFrameIndex, selectedJoint, point.x, (point.y - 0.01f).coerceAtLeast(0f))) }) { Text("Y-") }
                Button(onClick = { onUpdated(updateJoint(draft, resolvedFrameIndex, selectedJoint, point.x, (point.y + 0.01f).coerceAtMost(1f))) }) { Text("Y+") }
            }
        }
    }
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
