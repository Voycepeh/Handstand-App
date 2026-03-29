package com.inversioncoach.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDeveloperTuning: () -> Unit,
    onNavigateHome: () -> Unit,
    onDrillStudio: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val scope = rememberCoroutineScope()

    var latestSettings by remember { mutableStateOf(UserSettings()) }
    var cueFrequency by remember { mutableFloatStateOf(2f) }
    var debug by remember { mutableStateOf(false) }
    var localOnlyPrivacyMode by remember { mutableStateOf(true) }
    var maxStorageMb by remember { mutableIntStateOf(1024) }
    var startupCountdownSeconds by remember { mutableIntStateOf(10) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.observeSettings().collect { settings ->
            latestSettings = settings
            cueFrequency = settings.cueFrequencySeconds
            debug = settings.debugOverlayEnabled
            localOnlyPrivacyMode = settings.localOnlyPrivacyMode
            maxStorageMb = settings.maxStorageMb
            startupCountdownSeconds = settings.startupCountdownSeconds
        }
    }

    ScaffoldedScreen(title = "Settings", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Preferences", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            SettingsCard(title = "Voice cues") {
                Text("Cue frequency: ${"%.1f".format(cueFrequency)}s")
                Slider(value = cueFrequency, onValueChange = { cueFrequency = it }, valueRange = 1.5f..4f)
            }

            SettingsCard(title = "Overlay") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Debug overlay (raw metrics/angles)")
                    Checkbox(checked = debug, onCheckedChange = { debug = it })
                }
            }

            SettingsCard(title = "Session") {
                Text("Startup countdown: ${startupCountdownSeconds}s")
                Slider(value = startupCountdownSeconds.toFloat(), onValueChange = { startupCountdownSeconds = it.toInt().coerceIn(0, 30) }, valueRange = 0f..30f)
                Text("Time before recording starts.")
            }

            SettingsCard(title = "Storage & privacy") {
                Text("Max video storage: ${maxStorageMb} MB")
                Slider(value = maxStorageMb.toFloat(), onValueChange = { maxStorageMb = it.toInt().coerceIn(256, 4096) }, valueRange = 256f..4096f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Local-only privacy mode")
                    Checkbox(checked = localOnlyPrivacyMode, onCheckedChange = { localOnlyPrivacyMode = it })
                }
                Button(onClick = { showDeleteConfirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all sessions") }
            }

            Button(onClick = { showSaveConfirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
            Button(onClick = onDeveloperTuning, modifier = Modifier.fillMaxWidth()) { Text("Developer threshold tuning") }
            Button(onClick = onDrillStudio, modifier = Modifier.fillMaxWidth()) { Text("Drill Studio") }
        }

        if (showSaveConfirmation) {
            AlertDialog(
                onDismissRequest = { showSaveConfirmation = false },
                title = { Text("Save settings?") },
                text = { Text("This will apply your updated preferences and return you to the home page.") },
                confirmButton = {
                    Button(onClick = {
                        showSaveConfirmation = false
                        scope.launch {
                            repository.saveSettings(
                                latestSettings.copy(
                                    cueFrequencySeconds = cueFrequency,
                                    debugOverlayEnabled = debug,
                                    localOnlyPrivacyMode = localOnlyPrivacyMode,
                                    maxStorageMb = maxStorageMb,
                                    startupCountdownSeconds = startupCountdownSeconds,
                                ),
                            )
                            onNavigateHome()
                        }
                    }) { Text("Save") }
                },
                dismissButton = { Button(onClick = { showSaveConfirmation = false }) { Text("Cancel") } },
            )
        }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete all sessions?") },
                text = { Text("This action cannot be undone. All saved sessions will be removed and you will return to the home page.") },
                confirmButton = {
                    Button(onClick = {
                        showDeleteConfirmation = false
                        scope.launch {
                            repository.clearAllSessions()
                            onNavigateHome()
                        }
                    }) { Text("Delete") }
                },
                dismissButton = { Button(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
