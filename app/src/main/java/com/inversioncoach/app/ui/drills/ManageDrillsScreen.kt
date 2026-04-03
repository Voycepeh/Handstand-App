package com.inversioncoach.app.ui.drills

import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.drillstudio.DrillPackageManager
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDrillsScreen(
    onBack: () -> Unit,
    onCreateDrill: () -> Unit,
    onOpenDrill: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val packageManager = remember { DrillPackageManager(context.applicationContext) }
    val drills by repo.observeManageDrills().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var drillPendingDeleteId by remember { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { packageManager.import(uri) }
                .onSuccess { repo.updateDrill(it) }
        }
    }

    ScaffoldedScreen(title = "Manage Drills", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onCreateDrill, modifier = Modifier.fillMaxWidth()) { Text("New Drill") }
            OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Import Drill Package")
            }
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
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val record = repo.getDrill(drill.id) ?: return@launch
                                        val exported = packageManager.export(record)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exported)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share drill package"))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Export") }
                            OutlinedButton(
                                onClick = { drillPendingDeleteId = drill.id },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    val pendingDelete = drills.firstOrNull { it.id == drillPendingDeleteId }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { drillPendingDeleteId = null },
            title = { Text("Delete drill?") },
            text = { Text("This will permanently delete \"${pendingDelete.name}\".") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { repo.deleteDrill(pendingDelete.id) }
                    drillPendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { drillPendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}
