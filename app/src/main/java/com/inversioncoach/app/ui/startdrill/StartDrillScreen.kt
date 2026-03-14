package com.inversioncoach.app.ui.startdrill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.motion.DrillCatalog
import com.inversioncoach.app.ui.components.DrillPreviewAnimation
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun StartDrillScreen(
    onBack: () -> Unit,
    onStart: (DrillType, LiveSessionOptions) -> Unit,
    onOpenDetail: (DrillType) -> Unit,
) {
    val drills = remember { DrillConfigs.all }
    val selected = remember { mutableStateOf(drills.first().type) }
    val voiceOn = remember { mutableStateOf(true) }
    val recordOn = remember { mutableStateOf(true) }
    val skeletonOn = remember { mutableStateOf(true) }
    val idealLineOn = remember { mutableStateOf(true) }
    val zoomOutCameraOn = remember { mutableStateOf(true) }

    ScaffoldedScreen(title = "Choose Drill", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Handstand Exercise Library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(drills, key = { it.type.name }) { drill ->
                    val metadata = DrillCatalog.byType(drill.type)
                    DrillItem(
                        label = drill.label,
                        level = metadata.level.name.lowercase(),
                        movementPattern = metadata.category,
                        checkpoints = metadata.cues.take(3),
                        animationSpec = metadata.animationSpec,
                        mirrored = false,
                        selected = selected.value == drill.type,
                        onClick = { selected.value = drill.type },
                        onOpenDetail = { onOpenDetail(drill.type) },
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
            ToggleRow("Zoom out camera (0.5x if supported)", zoomOutCameraOn.value) { zoomOutCameraOn.value = it }
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
                            zoomOutCamera = zoomOutCameraOn.value,
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
private fun DrillItem(
    label: String,
    level: String,
    movementPattern: String,
    checkpoints: List<String>,
    animationSpec: com.inversioncoach.app.motion.SkeletonAnimationSpec,
    mirrored: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val containerColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrillPreviewAnimation(animationSpec = animationSpec, mirrored = mirrored, isPlaying = selected)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("$level • $movementPattern", style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                )
            }
            checkpoints.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Button(onClick = onOpenDetail) { Text("Details") }
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
