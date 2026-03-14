package com.inversioncoach.app.ui.startdrill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun StartDrillScreen(onBack: () -> Unit, onStart: (DrillType) -> Unit) {
    val selected = remember { mutableStateOf(DrillType.CHEST_TO_WALL_HANDSTAND) }
    val voiceOn = remember { mutableStateOf(true) }
    val recordOn = remember { mutableStateOf(true) }
    val skeletonOn = remember { mutableStateOf(true) }
    val idealLineOn = remember { mutableStateOf(true) }

    ScaffoldedScreen(title = "Start Drill", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Setup: side view, full body visible, good lighting.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(DrillConfigs.all) { drill ->
                    Card(onClick = { selected.value = drill.type }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(drill.label)
                            Text("Weights: ${drill.weights.joinToString { "${it.key}:${it.weight}" }}")
                        }
                    }
                }
            }
            ToggleRow("Voice feedback", voiceOn.value) { voiceOn.value = it }
            ToggleRow("Record session", recordOn.value) { recordOn.value = it }
            ToggleRow("Show skeleton overlay", skeletonOn.value) { skeletonOn.value = it }
            ToggleRow("Show ideal line", idealLineOn.value) { idealLineOn.value = it }
            Button(onClick = { onStart(selected.value) }, modifier = Modifier.fillMaxWidth()) {
                Text("Start 3s Countdown")
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}
