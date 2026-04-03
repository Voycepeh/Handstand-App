package com.inversioncoach.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
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
import com.inversioncoach.app.model.AnnotatedExportQuality
import com.inversioncoach.app.model.AppSettingsPolicy
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.model.effectiveExportQuality
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
    var maxStorageGb by remember { mutableIntStateOf(AppSettingsPolicy.defaultStorageGb) }
    var startupCountdownSeconds by remember { mutableIntStateOf(AppSettingsPolicy.defaultCountdownSeconds) }
    var exportQuality by remember { mutableStateOf(AppSettingsPolicy.defaultExportQuality) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.observeSettings().collect { settings ->
            latestSettings = settings
            cueFrequency = settings.cueFrequencySeconds
            debug = settings.debugOverlayEnabled
            localOnlyPrivacyMode = settings.localOnlyPrivacyMode
            maxStorageGb = AppSettingsPolicy.storageMbToGb(settings.maxStorageMb)
            startupCountdownSeconds = settings.startupCountdownSeconds
            exportQuality = settings.effectiveExportQuality()
        }
    }

    ScaffoldedScreen(title = "Settings", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Preferences", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            QualitySettingsCard(
                selected = exportQuality,
                onSelected = { exportQuality = it },
            )

            CountdownSettingsCard(
                selectedSeconds = startupCountdownSeconds,
                onSelectedSeconds = { startupCountdownSeconds = it },
            )

            StorageSettingsCard(
                storageLimitGb = maxStorageGb,
                onStorageLimitGbChanged = { maxStorageGb = it.coerceIn(AppSettingsPolicy.minStorageGb, AppSettingsPolicy.maxStorageGb) },
            )

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

            SettingsCard(title = "Storage & privacy") {
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
                                    maxStorageMb = AppSettingsPolicy.storageGbToMb(maxStorageGb),
                                    startupCountdownSeconds = startupCountdownSeconds,
                                    annotatedExportQuality = exportQuality.name,
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
private fun QualitySettingsCard(
    selected: AnnotatedExportQuality,
    onSelected: (AnnotatedExportQuality) -> Unit,
) {
    SettingsCard(title = "Annotated video export quality") {
        Text(
            "Choose how annotated videos are exported. Stable is faster and lighter to process with smaller files. High Quality gives you a sharper export, but processing takes longer and file size is larger.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Current: ${if (selected == AnnotatedExportQuality.STABLE) "Stable" else "High Quality"}")
        QualityOptionCard(
            title = "Stable",
            description = "Recommended for most use. 720p export with faster processing, lower storage usage, and reliable results.",
            selected = selected == AnnotatedExportQuality.STABLE,
            onClick = { onSelected(AnnotatedExportQuality.STABLE) },
        )
        QualityOptionCard(
            title = "High Quality",
            description = "1080p export for sharper playback and sharing. Processing takes longer and file size is larger.",
            selected = selected == AnnotatedExportQuality.HIGH_QUALITY,
            onClick = { onSelected(AnnotatedExportQuality.HIGH_QUALITY) },
        )
    }
}

@Composable
private fun CountdownSettingsCard(
    selectedSeconds: Int,
    onSelectedSeconds: (Int) -> Unit,
) {
    val options = AppSettingsPolicy.countdownOptionsSeconds
    SettingsCard(title = "Countdown before recording") {
        Text(
            "Choose how long the app waits before recording starts. Use a longer countdown if you need more time to get into position.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Current: $selectedSeconds seconds")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { seconds ->
                FilterChip(
                    selected = selectedSeconds == seconds,
                    onClick = { onSelectedSeconds(seconds) },
                    label = { Text("$seconds seconds") },
                )
            }
        }
    }
}

@Composable
private fun StorageSettingsCard(
    storageLimitGb: Int,
    onStorageLimitGbChanged: (Int) -> Unit,
) {
    SettingsCard(title = "Storage space limit") {
        Text(
            "Choose how much storage the app can use before older media is cleaned up. Higher limits keep more videos, but use more device space.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Current: $storageLimitGb GB")
        Slider(
            value = storageLimitGb.toFloat(),
            onValueChange = { onStorageLimitGbChanged(it.toInt()) },
            valueRange = AppSettingsPolicy.minStorageGb.toFloat()..AppSettingsPolicy.maxStorageGb.toFloat(),
        )
        Text("$storageLimitGb GB")
    }
}

@Composable
private fun QualityOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected) {
                Text("Currently selected", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
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
