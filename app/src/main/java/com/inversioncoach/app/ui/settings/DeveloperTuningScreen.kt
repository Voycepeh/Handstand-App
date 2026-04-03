package com.inversioncoach.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.motion.ThresholdTuningStore
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun DeveloperTuningScreen(onBack: () -> Unit) {
    var elbow by remember { mutableFloatStateOf(ThresholdTuningStore.elbowBottomThresholdDeg) }
    var trunk by remember { mutableFloatStateOf(ThresholdTuningStore.trunkLeanMaxDeg) }
    var line by remember { mutableFloatStateOf(ThresholdTuningStore.lineDeviationMaxNorm) }

    ScaffoldedScreen(title = "Developer threshold tuning", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Elbow bottom threshold: ${"%.0f".format(elbow)}°")
            Slider(value = elbow, onValueChange = {
                elbow = it
                ThresholdTuningStore.elbowBottomThresholdDeg = it
            }, valueRange = 60f..130f)

            Text("Trunk lean max: ${"%.0f".format(trunk)}°")
            Slider(value = trunk, onValueChange = {
                trunk = it
                ThresholdTuningStore.trunkLeanMaxDeg = it
            }, valueRange = 5f..40f)

            Text("Line deviation max: ${"%.2f".format(line)}")
            Slider(value = line, onValueChange = {
                line = it
                ThresholdTuningStore.lineDeviationMaxNorm = it
            }, valueRange = 0.05f..0.35f)

            Text("Changes are applied live for debug/development sessions.", modifier = Modifier.fillMaxWidth())
        }
    }
}
