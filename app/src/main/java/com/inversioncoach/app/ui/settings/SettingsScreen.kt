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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
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
import com.inversioncoach.app.model.AlignmentStrictness
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onDeveloperTuning: () -> Unit, onNavigateHome: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }

    val scope = rememberCoroutineScope()
    var cueFrequency by remember { mutableFloatStateOf(2f) }
    var overlay by remember { mutableFloatStateOf(1f) }
    var debug by remember { mutableStateOf(false) }
    var localOnlyPrivacyMode by remember { mutableStateOf(true) }
    var maxStorageMb by remember { mutableIntStateOf(1024) }
    var minSessionDurationSeconds by remember { mutableIntStateOf(3) }
    var alignmentStrictness by remember { mutableStateOf(AlignmentStrictness.BEGINNER) }
    var customLineDeviation by remember { mutableFloatStateOf(0.14f) }
    var customGoodForm by remember { mutableIntStateOf(72) }
    var customRepThreshold by remember { mutableIntStateOf(70) }
    var customHoldThreshold by remember { mutableIntStateOf(72) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.observeSettings().collect { s ->
            cueFrequency = s.cueFrequencySeconds
            overlay = s.overlayIntensity
            debug = s.debugOverlayEnabled
            localOnlyPrivacyMode = s.localOnlyPrivacyMode
            maxStorageMb = s.maxStorageMb
            minSessionDurationSeconds = s.minSessionDurationSeconds
            alignmentStrictness = s.alignmentStrictness
            customLineDeviation = s.customLineDeviation
            customGoodForm = s.customMinimumGoodFormScore
            customRepThreshold = s.customRepAcceptanceThreshold
            customHoldThreshold = s.customHoldAlignedThreshold
        }
    }

    ScaffoldedScreen(title = "Settings", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Voice style: concise / technical / encouraging")
            Text("Cue frequency: ${"%.1f".format(cueFrequency)}s")
            Slider(value = cueFrequency, onValueChange = { cueFrequency = it }, valueRange = 1.5f..4f)
            Text("Overlay intensity: ${"%.1f".format(overlay)}")
            Slider(value = overlay, onValueChange = { overlay = it }, valueRange = 0.2f..1f)
            Text("Max video storage: ${maxStorageMb} MB")
            Slider(
                value = maxStorageMb.toFloat(),
                onValueChange = { maxStorageMb = it.toInt().coerceIn(256, 4096) },
                valueRange = 256f..4096f,
            )
            Text("Minimum session length to keep (without video): ${minSessionDurationSeconds}s")
            Slider(
                value = minSessionDurationSeconds.toFloat(),
                onValueChange = { minSessionDurationSeconds = it.toInt().coerceIn(0, 30) },
                valueRange = 0f..30f,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Debug overlay (raw metrics/angles)")
                Checkbox(checked = debug, onCheckedChange = { debug = it })
            }
            Text("Preferences", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            SettingsCard(title = "Voice cues") {
                Text("Cue frequency: ${"%.1f".format(cueFrequency)}s")
                Slider(value = cueFrequency, onValueChange = { cueFrequency = it }, valueRange = 1.5f..4f)
                Text("Voice style: concise / technical / encouraging")
            }

            SettingsCard(title = "Overlay") {
                Text("Overlay intensity: ${"%.1f".format(overlay)}")
                Slider(value = overlay, onValueChange = { overlay = it }, valueRange = 0.2f..1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Debug overlay (raw metrics/angles)")
                    Checkbox(checked = debug, onCheckedChange = { debug = it })
                }
            }

            SettingsCard(title = "Alignment") {
                Text("Alignment strictness: ${alignmentStrictness.name.lowercase().replaceFirstChar { it.uppercase() }}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlignmentStrictness.entries.forEach { level ->
                        Button(
                            onClick = { alignmentStrictness = level },
                            modifier = Modifier.weight(1f),
                            enabled = alignmentStrictness != level,
                        ) {
                            Text(level.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                Text("Beginner is forgiving. Advanced is strict. Custom uses your thresholds from saved settings.")
                if (alignmentStrictness == AlignmentStrictness.CUSTOM) {
                    Text("Line deviation: ${"%.2f".format(customLineDeviation)}")
                    Slider(value = customLineDeviation, onValueChange = { customLineDeviation = it }, valueRange = 0.06f..0.24f)
                    Text("Minimum good form score: $customGoodForm")
                    Slider(value = customGoodForm.toFloat(), onValueChange = { customGoodForm = it.toInt().coerceIn(40, 95) }, valueRange = 40f..95f)
                    Text("Rep acceptance threshold: $customRepThreshold")
                    Slider(value = customRepThreshold.toFloat(), onValueChange = { customRepThreshold = it.toInt().coerceIn(40, 95) }, valueRange = 40f..95f)
                    Text("Hold aligned threshold: $customHoldThreshold")
                    Slider(value = customHoldThreshold.toFloat(), onValueChange = { customHoldThreshold = it.toInt().coerceIn(40, 95) }, valueRange = 40f..95f)
                }
            }

            SettingsCard(title = "Storage & privacy") {
                Text("Max video storage: ${maxStorageMb} MB")
                Slider(
                    value = maxStorageMb.toFloat(),
                    onValueChange = { maxStorageMb = it.toInt().coerceIn(256, 4096) },
                    valueRange = 256f..4096f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Local-only privacy mode")
                    Checkbox(checked = localOnlyPrivacyMode, onCheckedChange = { localOnlyPrivacyMode = it })
                }
            }

            Button(onClick = { showSaveConfirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
            Button(onClick = onDeveloperTuning, modifier = Modifier.fillMaxWidth()) { Text("Developer threshold tuning") }
            Button(onClick = { showDeleteConfirmation = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all sessions") }
        }

        if (showSaveConfirmation) {
            AlertDialog(
                onDismissRequest = { showSaveConfirmation = false },
                title = { Text("Save settings?") },
                text = { Text("This will apply your updated preferences and return you to the home page.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showSaveConfirmation = false
                            scope.launch {
                                repository.saveSettings(
                                    UserSettings(
                                        cueFrequencySeconds = cueFrequency,
                                        overlayIntensity = overlay,
                                        debugOverlayEnabled = debug,
                                        localOnlyPrivacyMode = localOnlyPrivacyMode,
                                        maxStorageMb = maxStorageMb,
                                        minSessionDurationSeconds = minSessionDurationSeconds,
                                        alignmentStrictness = alignmentStrictness,
                                        customLineDeviation = customLineDeviation,
                                        customMinimumGoodFormScore = customGoodForm,
                                        customRepAcceptanceThreshold = customRepThreshold,
                                        customHoldAlignedThreshold = customHoldThreshold,
                                    ),
                                )
                                onNavigateHome()
                            }
                        },
                    ) { Text("Save") }
                },
                dismissButton = {
                    Button(onClick = { showSaveConfirmation = false }) { Text("Cancel") }
                },
            )
        }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete all sessions?") },
                text = { Text("This action cannot be undone. All saved sessions will be removed and you will return to the home page.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmation = false
                            scope.launch {
                                repository.clearAllSessions()
                                onNavigateHome()
                            }
                        },
                    ) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
