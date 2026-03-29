package com.inversioncoach.app.ui.drills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.launch

@Composable
fun ManageDrillsScreen(
    onBack: () -> Unit,
    onCreateDrill: () -> Unit,
    onEditDrill: (String) -> Unit,
    onOpenDrill: (String) -> Unit,
    onOpenInStudio: (String?) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val drills by repo.getAllDrills().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    ScaffoldedScreen(title = "Manage Drills", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onCreateDrill, modifier = Modifier.fillMaxWidth()) { Text("Create Drill") }
            OutlinedButton(onClick = { onOpenInStudio(null) }, modifier = Modifier.fillMaxWidth()) { Text("Open Drill Studio") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(drills) { drill ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(drill.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(drill.description, style = MaterialTheme.typography.bodySmall)
                            Text("${drill.movementMode} • ${drill.cameraView} • ${drill.status}", style = MaterialTheme.typography.labelSmall)
                            Button(onClick = { onOpenDrill(drill.id) }, modifier = Modifier.fillMaxWidth()) { Text("Open") }
                            OutlinedButton(onClick = { onEditDrill(drill.id) }, modifier = Modifier.fillMaxWidth()) { Text("Edit") }
                            OutlinedButton(onClick = { onOpenInStudio(drill.id) }, modifier = Modifier.fillMaxWidth()) { Text("Open in Drill Studio") }
                            OutlinedButton(
                                onClick = { scope.launch { repo.archiveDrill(drill.id) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Archive") }
                            OutlinedButton(
                                onClick = { scope.launch { repo.deleteDrill(drill.id) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}
