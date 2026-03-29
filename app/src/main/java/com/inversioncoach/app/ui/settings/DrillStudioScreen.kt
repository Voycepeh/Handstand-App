package com.inversioncoach.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.menuAnchor
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
import com.inversioncoach.app.motion.BodyJoint
import com.inversioncoach.app.motion.DrillCatalogDraftStore
import com.inversioncoach.app.motion.DrillCatalogImportExportManager
import com.inversioncoach.app.motion.DrillStudioComparisonMode
import com.inversioncoach.app.motion.DrillStudioDocument
import com.inversioncoach.app.motion.DrillStudioViewMode
import com.inversioncoach.app.motion.NormalizedPoint
import com.inversioncoach.app.ui.components.DrillPreviewAnimation
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { DrillCatalogDraftStore(context) }
    val importExport = remember { DrillCatalogImportExportManager(context, store) }

    var drills by remember { mutableStateOf(store.listDrills()) }
    var selectedDrillId by remember { mutableStateOf(drills.firstOrNull()?.id.orEmpty()) }
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
                DrillDropdown(
                    label = "Phase",
                    selected = draft.animationSpec.keyframes.getOrNull(selectedPhase)?.name.orEmpty(),
                    options = draft.animationSpec.keyframes.mapIndexed { index, frame -> index.toString() to frame.name }.toMap(),
                ) { phaseIndex -> selectedPhase = phaseIndex.toIntOrNull() ?: 0 }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Progress window: ${"%.2f".format(draft.progressWindow.start)} - ${"%.2f".format(draft.progressWindow.end)}")
                        Slider(
                            value = draft.progressWindow.start,
                            onValueChange = { value ->
                                working = draft.copy(progressWindow = draft.progressWindow.copy(start = min(value, draft.progressWindow.end)))
                            },
                            valueRange = 0f..1f,
                        )
                        Slider(
                            value = draft.progressWindow.end,
                            onValueChange = { value ->
                                working = draft.copy(progressWindow = draft.progressWindow.copy(end = max(value, draft.progressWindow.start)))
                            },
                            valueRange = 0f..1f,
                        )
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

                DrillDropdown(
                    label = "Supported view",
                    selected = draft.supportedView.name,
                    options = DrillStudioViewMode.entries.associate { it.name to it.name },
                ) { value ->
                    val mode = DrillStudioViewMode.entries.first { it.name == value }
                    working = draft.copy(supportedView = mode)
                }

                DrillDropdown(
                    label = "Default view",
                    selected = draft.defaultView.name,
                    options = DrillStudioViewMode.entries.associate { it.name to it.name },
                ) { value ->
                    val mode = DrillStudioViewMode.entries.first { it.name == value }
                    working = draft.copy(defaultView = mode)
                }

                DrillDropdown(
                    label = "Comparison mode",
                    selected = draft.comparisonMode.name,
                    options = DrillStudioComparisonMode.entries.associate { it.name to it.name },
                ) { value ->
                    val mode = DrillStudioComparisonMode.entries.first { it.name == value }
                    working = draft.copy(comparisonMode = mode)
                }

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
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = options[selected] ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                Text("$key: ${"%.2f".format(value)}")
                Slider(
                    value = value,
                    onValueChange = { next ->
                        onUpdated(draft.copy(metricThresholds = draft.metricThresholds + (key to next)))
                    },
                    valueRange = 0f..180f,
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
    val frame = draft.animationSpec.keyframes.getOrNull(phaseIndex) ?: return
    val point = frame.joints[selectedJoint] ?: NormalizedPoint(0.5f, 0.5f)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DrillDropdown(
                label = "Joint",
                selected = selectedJoint.name,
                options = BodyJoint.entries.associate { it.name to it.name },
                onSelect = { name -> onJointSelected(BodyJoint.entries.first { it.name == name }) },
            )
            Text("${selectedJoint.name}: x=${"%.2f".format(point.x)} y=${"%.2f".format(point.y)}")
            Slider(value = point.x, onValueChange = { x -> onUpdated(updateJoint(draft, phaseIndex, selectedJoint, x, point.y)) }, valueRange = 0f..1f)
            Slider(value = point.y, onValueChange = { y -> onUpdated(updateJoint(draft, phaseIndex, selectedJoint, point.x, y)) }, valueRange = 0f..1f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onUpdated(updateJoint(draft, phaseIndex, selectedJoint, (point.x - 0.01f).coerceAtLeast(0f), point.y)) }) { Text("X-") }
                Button(onClick = { onUpdated(updateJoint(draft, phaseIndex, selectedJoint, (point.x + 0.01f).coerceAtMost(1f), point.y)) }) { Text("X+") }
                Button(onClick = { onUpdated(updateJoint(draft, phaseIndex, selectedJoint, point.x, (point.y - 0.01f).coerceAtLeast(0f))) }) { Text("Y-") }
                Button(onClick = { onUpdated(updateJoint(draft, phaseIndex, selectedJoint, point.x, (point.y + 0.01f).coerceAtMost(1f))) }) { Text("Y+") }
            }
        }
    }
}

private fun updateJoint(draft: DrillStudioDocument, phaseIndex: Int, joint: BodyJoint, x: Float, y: Float): DrillStudioDocument {
    val frames = draft.animationSpec.keyframes.toMutableList()
    val target = frames.getOrNull(phaseIndex) ?: return draft
    frames[phaseIndex] = target.copy(joints = target.joints + (joint to NormalizedPoint(x, y)))
    return draft.copy(animationSpec = draft.animationSpec.copy(keyframes = frames))
}
