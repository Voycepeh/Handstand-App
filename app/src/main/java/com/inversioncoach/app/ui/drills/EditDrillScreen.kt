package com.inversioncoach.app.ui.drills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.drills.DrillMovementMode
import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.DrillDefinitionValidator
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDrillScreen(
    drillId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var movementMode by remember { mutableStateOf(DrillMovementMode.HOLD) }
    var cameraView by remember { mutableStateOf(DrillCameraView.LEFT) }
    var phaseSchema by remember { mutableStateOf("setup|hold") }
    var keyJoints by remember { mutableStateOf("shoulders|hips") }
    var normalizationBasis by remember { mutableStateOf("hips") }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var movementModeExpanded by remember { mutableStateOf(false) }
    var cameraViewExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(drillId) {
        if (drillId == null) return@LaunchedEffect
        val drill = repo.getDrill(drillId) ?: return@LaunchedEffect
        name = drill.name
        description = drill.description
        movementMode = when (drill.movementMode) {
            DrillMovementMode.REP -> DrillMovementMode.REP
            else -> DrillMovementMode.HOLD
        }
        cameraView = when (drill.cameraView) {
            DrillCameraView.LEFT -> DrillCameraView.LEFT
            DrillCameraView.RIGHT -> DrillCameraView.RIGHT
            DrillCameraView.FRONT -> DrillCameraView.FRONT
            DrillCameraView.BACK -> DrillCameraView.BACK
            DrillCameraView.FREESTYLE -> DrillCameraView.FREESTYLE
            "SIDE" -> DrillCameraView.LEFT
            else -> DrillCameraView.FREESTYLE
        }
        phaseSchema = drill.phaseSchemaJson
        keyJoints = drill.keyJointsJson
        normalizationBasis = drill.normalizationBasisJson
    }

    ScaffoldedScreen(title = if (drillId == null) "Create Drill" else "Edit Drill", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(
                expanded = movementModeExpanded,
                onExpandedChange = { movementModeExpanded = !movementModeExpanded },
            ) {
                OutlinedTextField(
                    value = movementMode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Movement mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = movementModeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                DropdownMenu(expanded = movementModeExpanded, onDismissRequest = { movementModeExpanded = false }) {
                    listOf(DrillMovementMode.HOLD, DrillMovementMode.REP).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                movementMode = option
                                movementModeExpanded = false
                            },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(
                expanded = cameraViewExpanded,
                onExpandedChange = { cameraViewExpanded = !cameraViewExpanded },
            ) {
                OutlinedTextField(
                    value = cameraView,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Camera view") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cameraViewExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                DropdownMenu(expanded = cameraViewExpanded, onDismissRequest = { cameraViewExpanded = false }) {
                    listOf(
                        DrillCameraView.LEFT,
                        DrillCameraView.RIGHT,
                        DrillCameraView.FRONT,
                        DrillCameraView.BACK,
                        DrillCameraView.FREESTYLE,
                    ).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                cameraView = option
                                cameraViewExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(phaseSchema, { phaseSchema = it }, label = { Text("Phases (| separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(keyJoints, { keyJoints = it }, label = { Text("Key joints (| separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(normalizationBasis, { normalizationBasis = it }, label = { Text("Normalization basis") }, modifier = Modifier.fillMaxWidth())

            validationErrors.forEach { message ->
                Text(message)
            }

            OutlinedButton(onClick = {
                val now = System.currentTimeMillis()
                scope.launch {
                    val existing = drillId?.let { id -> repo.getDrill(id) }
                    val record = DrillDefinitionRecord(
                        id = existing?.id ?: "drill-${UUID.randomUUID()}",
                        name = name,
                        description = description,
                        movementMode = movementMode,
                        cameraView = cameraView,
                        phaseSchemaJson = phaseSchema,
                        keyJointsJson = keyJoints,
                        normalizationBasisJson = normalizationBasis,
                        cueConfigJson = existing?.cueConfigJson ?: "",
                        sourceType = existing?.sourceType ?: DrillSourceType.USER_CREATED,
                        status = DrillStatus.DRAFT,
                        version = (existing?.version ?: 0) + 1,
                        createdAtMs = existing?.createdAtMs ?: now,
                        updatedAtMs = now,
                    )
                    if (existing == null) repo.createDrill(record) else repo.updateDrill(record)
                    validationErrors = emptyList()
                    onBack()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Save Draft")
            }

            Button(onClick = {
                val now = System.currentTimeMillis()
                scope.launch {
                    val existing = drillId?.let { id -> repo.getDrill(id) }
                    val record = DrillDefinitionRecord(
                        id = existing?.id ?: "drill-${UUID.randomUUID()}",
                        name = name,
                        description = description,
                        movementMode = movementMode,
                        cameraView = cameraView,
                        phaseSchemaJson = phaseSchema,
                        keyJointsJson = keyJoints,
                        normalizationBasisJson = normalizationBasis,
                        cueConfigJson = existing?.cueConfigJson ?: "",
                        sourceType = existing?.sourceType ?: DrillSourceType.USER_CREATED,
                        status = existing?.status ?: DrillStatus.DRAFT,
                        version = (existing?.version ?: 0) + 1,
                        createdAtMs = existing?.createdAtMs ?: now,
                        updatedAtMs = now,
                    )
                    val errors = DrillDefinitionValidator.validate(record)
                    if (errors.isNotEmpty()) {
                        validationErrors = errors
                        return@launch
                    }
                    if (existing == null) repo.createDrill(record) else repo.updateDrill(record)
                    val readyErrors = repo.validateAndMarkDrillReady(record.id)
                    validationErrors = readyErrors
                    if (readyErrors.isEmpty()) onBack()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Save and Mark Ready")
            }
        }
    }
}
