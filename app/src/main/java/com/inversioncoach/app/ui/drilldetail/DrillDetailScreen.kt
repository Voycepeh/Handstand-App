package com.inversioncoach.app.ui.drilldetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.motion.DrillCatalog
import com.inversioncoach.app.ui.components.DrillPreviewAnimation
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun DrillDetailScreen(drillType: DrillType, onBack: () -> Unit, onOpenDrillStudio: (DrillType) -> Unit) {
    val drill = DrillCatalog.byType(drillType)
    ScaffoldedScreen(title = drill.displayName, onBack = onBack) { padding ->
        DrillDetailContent(
            padding = padding,
            drillType = drillType,
            drill = drill,
            onOpenDrillStudio = onOpenDrillStudio,
        )
    }
}

@Composable
private fun DrillDetailContent(
    padding: PaddingValues,
    drillType: DrillType,
    drill: com.inversioncoach.app.motion.DrillDefinition,
    onOpenDrillStudio: (DrillType) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DrillPreviewAnimation(animationSpec = drill.animationSpec, modifier = Modifier.size(180.dp))
                    Text("Level: ${drill.level.name.lowercase()}")
                    Text("Pattern: ${drill.movementPattern.name.lowercase()}")
                }
            }
        }

        item {
            Button(
                onClick = { onOpenDrillStudio(drillType) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open in Drill Studio")
            }
        }

        item {
            Text("Phases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        itemsIndexed(drill.mainPhases, key = { _, phase -> phase.label }) { _, phase ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                Text("• ${phase.label}", modifier = Modifier.padding(12.dp))
            }
        }

        item {
            Text("Common faults & cues", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(drill.commonFaults, key = { "fault_$it" }) { fault ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                Text("Fault: $fault", modifier = Modifier.padding(12.dp))
            }
        }
        items(drill.cues, key = { "cue_$it" }) { cue ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                Text("Cue: $cue", modifier = Modifier.padding(12.dp))
            }
        }
    }
}
