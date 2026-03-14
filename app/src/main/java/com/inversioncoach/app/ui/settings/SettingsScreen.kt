package com.inversioncoach.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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

    LaunchedEffect(Unit) {
        repository.observeSettings().collect { s ->
            cueFrequency = s.cueFrequencySeconds
            overlay = s.overlayIntensity
            debug = s.debugOverlayEnabled
        }
    }

    ScaffoldedScreen(title = "Settings", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Voice style: concise / technical / encouraging")
            Text("Cue frequency: ${"%.1f".format(cueFrequency)}s")
            Slider(value = cueFrequency, onValueChange = { cueFrequency = it }, valueRange = 1.5f..4f)
            Text("Overlay intensity: ${"%.1f".format(overlay)}")
            Slider(value = overlay, onValueChange = { overlay = it }, valueRange = 0.2f..1f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Debug overlay (raw metrics/angles)")
                Checkbox(checked = debug, onCheckedChange = { debug = it })
            }
            Button(
                onClick = {
                    scope.launch {
                        repository.saveSettings(
                            UserSettings(
                                cueFrequencySeconds = cueFrequency,
                                overlayIntensity = overlay,
                                debugOverlayEnabled = debug,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save settings") }
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Enable local-only privacy mode") }
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
