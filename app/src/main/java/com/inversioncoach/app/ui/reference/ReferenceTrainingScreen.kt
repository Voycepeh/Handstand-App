package com.inversioncoach.app.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.DrillDefinitionResolver
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.launch

@Composable
fun ReferenceTrainingScreen(
    drillId: String,
    onBack: () -> Unit,
    onUploadReference: (String) -> Unit,
    onUploadAttempt: (String, String?) -> Unit,
    onCompareAttempts: (String) -> Unit,
    onEditDrill: (String, String?) -> Unit,
    onStartLiveSession: (DrillType) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val drills by repo.getAllDrills().collectAsState(initial = emptyList())
    val templates by repo.getTemplatesForDrill(drillId).collectAsState(initial = emptyList())
    val drillSessions by repo.getRecentSessionsForDrill(drillId).collectAsState(initial = emptyList())
    val allSessions by repo.observeSessions().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }
    var showPastSessionDialog by remember { mutableStateOf(false) }
    val selectedDrill = drills.firstOrNull { it.id == drillId }
    val isReady = selectedDrill?.status == DrillStatus.READY
    val baselineTemplate = templates.firstOrNull { it.isBaseline }

    if (showPastSessionDialog) {
        PromotePastSessionDialog(
            sessions = allSessions,
            drillsById = drills.associateBy { it.id },
            onDismiss = { showPastSessionDialog = false },
            onSave = { sessionId, referenceName, setBaseline ->
                scope.launch {
                    repo.promoteSessionToReference(
                        sessionId = sessionId,
                        targetDrillId = drillId,
                        referenceName = referenceName,
                        setAsBaseline = setBaseline,
                    )
                    showPastSessionDialog = false
                }
            },
        )
    }

    ScaffoldedScreen(title = "Reference Training", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(selectedDrill?.name ?: "Drill", style = MaterialTheme.typography.titleLarge)
            ReferenceSummaryCard(
                baselineName = baselineTemplate?.displayName,
                sourceType = baselineTemplate?.sourceType,
                updatedAtMs = baselineTemplate?.updatedAtMs,
                recentSessionCount = drillSessions.size,
            )
            if (!isReady) {
                Text("Only READY drills can be used for reference upload or comparison.")
            }

            Text("Reference", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { onUploadReference(drillId) }, enabled = isReady, modifier = Modifier.fillMaxWidth()) { Text("Upload New Reference") }
            Button(onClick = { showPastSessionDialog = true }, enabled = isReady, modifier = Modifier.fillMaxWidth()) { Text("Use Past Session as Reference") }

            Text("Train", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    val drillType = selectedDrill?.let(DrillDefinitionResolver::resolveLegacyDrillType) ?: DrillType.FREESTYLE
                    onStartLiveSession(drillType)
                },
                enabled = isReady,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start Live Session") }
            Button(onClick = { onUploadAttempt(drillId, selectedTemplateId) }, enabled = isReady, modifier = Modifier.fillMaxWidth()) { Text("Upload Attempt") }
            Button(onClick = { onCompareAttempts(drillId) }, enabled = isReady, modifier = Modifier.fillMaxWidth()) { Text("Compare Attempts") }

            Text("Manage Drill", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { onEditDrill(drillId, selectedTemplateId) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedTemplateId == null) "Edit Drill" else "Edit Drill (Selected Reference)")
            }

            Text("Reference Template", style = MaterialTheme.typography.titleMedium)
            if (templates.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No reference templates yet",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates) { template ->
                        Card(onClick = { selectedTemplateId = template.id }) {
                            Column(Modifier.padding(10.dp)) {
                                Text(template.displayName)
                                Text(
                                    buildString {
                                        if (template.isBaseline) append("Baseline • ")
                                        append(if (selectedTemplateId == template.id) "Selected" else "Tap to select")
                                        template.sourceSessionId?.let { append(" • Session #$it") }
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceSummaryCard(
    baselineName: String?,
    sourceType: String?,
    updatedAtMs: Long?,
    recentSessionCount: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Current reference: ${baselineName ?: "Not set"}")
            sourceType?.let { Text("Source: $it", style = MaterialTheme.typography.bodySmall) }
            updatedAtMs?.let { Text("Last updated: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}", style = MaterialTheme.typography.bodySmall) }
            Text("Recent sessions: $recentSessionCount", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PromotePastSessionDialog(
    sessions: List<com.inversioncoach.app.model.SessionRecord>,
    drillsById: Map<String, com.inversioncoach.app.model.DrillDefinitionRecord>,
    onDismiss: () -> Unit,
    onSave: (sessionId: Long, referenceName: String?, setAsBaseline: Boolean) -> Unit,
) {
    var selectedSessionId by remember(sessions) { mutableStateOf(sessions.maxByOrNull { it.startedAtMs }?.id) }
    var referenceName by remember { mutableStateOf("") }
    var setAsBaseline by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use Past Session as Reference") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick a past session to save as a reference for this drill.")
                if (sessions.isEmpty()) {
                    Text("No past sessions available yet.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sessions.sortedByDescending { it.startedAtMs }) { session ->
                            Card(onClick = { selectedSessionId = session.id }) {
                                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Checkbox(checked = selectedSessionId == session.id, onCheckedChange = { selectedSessionId = session.id })
                                    Column {
                                        Text(session.title.ifBlank { "Session #${session.id}" })
                                        val drillName = session.drillId?.let(drillsById::get)?.name
                                        Text(
                                            "ID ${session.id} • ${drillName ?: "Unassigned drill"} • ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(session.startedAtMs))}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = referenceName, onValueChange = { referenceName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Reference name (optional)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = setAsBaseline, onCheckedChange = { setAsBaseline = it })
                        Text("Set as baseline for this drill")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedSessionId?.let { onSave(it, referenceName.takeIf(String::isNotBlank), setAsBaseline) } },
                enabled = selectedSessionId != null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
