package com.inversioncoach.app.ui.startdrill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun StartDrillScreen(onBack: () -> Unit, onStart: (DrillType, LiveSessionOptions) -> Unit) {
    val selected = remember { mutableStateOf(DrillType.CHEST_TO_WALL_HANDSTAND) }
    val voiceOn = remember { mutableStateOf(true) }
    val recordOn = remember { mutableStateOf(true) }
    val skeletonOn = remember { mutableStateOf(true) }
    val idealLineOn = remember { mutableStateOf(true) }

    ScaffoldedScreen(title = "Choose Drill", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Pick a drill",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(DrillConfigs.all) { drill ->
                    DrillItem(
                        label = drill.label,
                        selected = selected.value == drill.type,
                        onClick = { selected.value = drill.type },
                    )
                }
            }
            Text(
                text = "Session options",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            ToggleRow("Voice feedback", voiceOn.value) { voiceOn.value = it }
            ToggleRow("Record session", recordOn.value) { recordOn.value = it }
            ToggleRow("Show skeleton overlay", skeletonOn.value) { skeletonOn.value = it }
            ToggleRow("Show ideal line", idealLineOn.value) { idealLineOn.value = it }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    onStart(
                        selected.value,
                        LiveSessionOptions(
                            voiceEnabled = voiceOn.value,
                            recordingEnabled = recordOn.value,
                            showSkeletonOverlay = skeletonOn.value,
                            showIdealLine = idealLineOn.value,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start drill")
            }
        }
    }
}

@Composable
private fun DrillItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val containerColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DrillIcon(selected = selected)
            Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(
                imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun DrillIcon(selected: Boolean) {
    val icon: ImageVector = if (selected) Icons.Default.FitnessCenter else Icons.Default.PlayArrow
    Box(
        modifier = Modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}
