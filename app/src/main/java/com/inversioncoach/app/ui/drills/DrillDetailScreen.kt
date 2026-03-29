package com.inversioncoach.app.ui.drills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.drills.DrillStatus

@Composable
fun DrillDetailScreen(
    drillId: String,
    onBack: () -> Unit,
    onUploadReference: (String) -> Unit,
    onCompareAttempt: (String) -> Unit,
    onEditCalibration: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val drillFlow by repo.getAllDrills().collectAsState(initial = emptyList())
    val referenceAssets by repo.observeReferenceAssets(drillId).collectAsState(initial = emptyList())
    val templates by repo.getTemplatesForDrill(drillId).collectAsState(initial = emptyList())
    val calibrations by repo.observeCalibrationConfig(drillId).collectAsState(initial = emptyList())
    val drill = drillFlow.firstOrNull { it.id == drillId }

    ScaffoldedScreen(title = "Drill Detail", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(drill?.name ?: "Unknown drill")
            Text(drill?.description ?: "-")
            Text("Mode: ${drill?.movementMode ?: "-"} • Camera: ${drill?.cameraView ?: "-"}")
            Text("Status: ${drill?.status ?: "-"}")
            Text("Reference assets: ${referenceAssets.size}")
            Text("Templates: ${templates.size}")
            Text("Active calibration: ${calibrations.firstOrNull { it.isActive }?.displayName ?: "none"}")
            val isReady = drill?.status == DrillStatus.READY.name
            if (!isReady) {
                Text("This drill is not READY yet. Save and mark ready before using upload/compare.")
            }

            Button(onClick = { onUploadReference(drillId) }, enabled = isReady, modifier = Modifier.fillMaxWidth()) { Text("Upload Reference Video") }
            Button(onClick = { onCompareAttempt(drillId) }, enabled = isReady, modifier = Modifier.fillMaxWidth()) { Text("Compare New Attempt") }
            Button(onClick = { onEditCalibration(drillId) }, modifier = Modifier.fillMaxWidth()) { Text("Edit Calibration") }
        }
    }
}
