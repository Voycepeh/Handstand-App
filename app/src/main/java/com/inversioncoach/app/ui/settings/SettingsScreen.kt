package com.inversioncoach.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onDeveloperTuning: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }

    val scope = rememberCoroutineScope()
    var cueFrequency by remember { mutableFloatStateOf(2f) }
    var overlay by remember { mutableFloatStateOf(1f) }
    var debug by remember { mutableStateOf(false) }
    var localOnlyPrivacyMode by remember { mutableStateOf(true) }
    var maxStorageMb by remember { mutableIntStateOf(1024) }

    LaunchedEffect(Unit) {
        repository.observeSettings().collect { s ->
            cueFrequency = s.cueFrequencySeconds
            overlay = s.overlayIntensity
            debug = s.debugOverlayEnabled
            localOnlyPrivacyMode = s.localOnlyPrivacyMode
            maxStorageMb = s.maxStorageMb
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Debug overlay (raw metrics/angles)")
                Checkbox(checked = debug, onCheckedChange = { debug = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Local-only privacy mode")
                Checkbox(checked = localOnlyPrivacyMode, onCheckedChange = { localOnlyPrivacyMode = it })
            }
            Button(
                onClick = {
                    scope.launch {
                        repository.saveSettings(
                            UserSettings(
                                cueFrequencySeconds = cueFrequency,
                                overlayIntensity = overlay,
                                debugOverlayEnabled = debug,
                                localOnlyPrivacyMode = localOnlyPrivacyMode,
                                maxStorageMb = maxStorageMb,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save settings") }
            Button(onClick = onDeveloperTuning, modifier = Modifier.fillMaxWidth()) { Text("Developer threshold tuning") }
            Button(
                onClick = {
                    scope.launch { repository.clearAllSessions() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete all sessions") }
        }
    }
}
