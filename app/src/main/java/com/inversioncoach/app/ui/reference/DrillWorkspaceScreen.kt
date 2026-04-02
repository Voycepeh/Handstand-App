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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.DrillDefinitionResolver
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import java.text.DateFormat
import java.util.Date


internal object DrillWorkspacePrimaryActions {
    val primary = listOf("Start Live Coaching", "Upload Attempt", "Compare Attempts", "View Past Sessions", "Manage This Drill")
    val hiddenLegacy = listOf(
        "Upload New Reference",
        "Use Past Session as Reference",
        "Reference Template",
        "Edit Drill",
        "New Drill",
    )
}

@Composable
fun DrillWorkspaceScreen(
    drillId: String,
    onBack: () -> Unit,
    onUploadAttempt: (String) -> Unit,
    onCompareAttempts: (String) -> Unit,
    onViewHistory: (String) -> Unit,
    onStartLiveSession: (DrillType) -> Unit,
    onManageDrill: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val drills by repo.getAllDrills().collectAsState(initial = emptyList())
    val drillSessions by repo.getRecentSessionsForDrill(drillId).collectAsState(initial = emptyList())

    val selectedDrill = drills.firstOrNull { it.id == drillId }
    val isReady = selectedDrill?.status == DrillStatus.READY

    ScaffoldedScreen(title = "Drill Workspace", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(selectedDrill?.name ?: "Drill", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = selectedDrill?.description.orEmpty().ifBlank { "Practice this drill, upload attempts, compare progress, and review past sessions." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isReady) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "This drill is not READY yet. Live coaching and upload actions are currently disabled.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = {
                    val drillType = selectedDrill?.let(DrillDefinitionResolver::resolveLegacyDrillType) ?: DrillType.FREESTYLE
                    onStartLiveSession(drillType)
                },
                enabled = isReady,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(DrillWorkspacePrimaryActions.primary[0]) }

            Button(
                onClick = { onUploadAttempt(drillId) },
                enabled = isReady,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(DrillWorkspacePrimaryActions.primary[1]) }

            Button(
                onClick = { onCompareAttempts(drillId) },
                enabled = isReady,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(DrillWorkspacePrimaryActions.primary[2]) }

            Button(
                onClick = { onViewHistory(drillId) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(DrillWorkspacePrimaryActions.primary[3]) }

            Button(
                onClick = { onManageDrill(drillId) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(DrillWorkspacePrimaryActions.primary[4]) }

            Text("Recent sessions", style = MaterialTheme.typography.titleMedium)
            if (drillSessions.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No sessions yet for this drill.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(drillSessions.take(5)) { session ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(session.title.ifBlank { "Session #${session.id}" }, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    DateFormat.getDateTimeInstance().format(Date(session.startedAtMs)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
