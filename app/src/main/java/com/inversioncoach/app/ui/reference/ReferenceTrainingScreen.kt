package com.inversioncoach.app.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun ReferenceTrainingScreen(
    drillId: String,
    onBack: () -> Unit,
    onUploadReference: (String) -> Unit,
    onUploadAttempt: (String, String?) -> Unit,
    onComparePastSessions: (String) -> Unit,
    onOpenDrillStudio: (String) -> Unit,
    onCreateNewDrillFromReference: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val drills by repo.getAllDrills().collectAsState(initial = emptyList())
    val templates by repo.getTemplatesForDrill(drillId).collectAsState(initial = emptyList())
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }
    val selectedDrill = drills.firstOrNull { it.id == drillId }
    val isReady = selectedDrill?.status == DrillStatus.READY

    ScaffoldedScreen(title = "Reference Training", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(selectedDrill?.name ?: "Drill")
            if (!isReady) {
                Text("Only READY drills can be used for reference upload or comparison.")
            }
            Button(onClick = { onUploadReference(drillId) }, enabled = isReady, modifier = Modifier.fillMaxWidth()) {
                Text("Upload Reference")
            }
            Button(onClick = { onUploadAttempt(drillId, selectedTemplateId) }, enabled = isReady, modifier = Modifier.fillMaxWidth()) {
                Text("Upload Attempt")
            }
            Button(onClick = { onComparePastSessions(drillId) }, enabled = isReady, modifier = Modifier.fillMaxWidth()) {
                Text("Compare Past Sessions")
            }
            Button(onClick = { onOpenDrillStudio(drillId) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Drill Studio")
            }
            Button(onClick = { onCreateNewDrillFromReference(drillId) }, enabled = isReady, modifier = Modifier.fillMaxWidth()) {
                Text("Create New Drill from Reference")
            }
            Text("Templates", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates) { template ->
                    Card(onClick = { selectedTemplateId = template.id }) {
                        Column(Modifier.padding(10.dp)) {
                            Text(template.displayName)
                            Text(if (selectedTemplateId == template.id) "Selected" else "Tap to select")
                        }
                    }
                }
            }
        }
    }
}
