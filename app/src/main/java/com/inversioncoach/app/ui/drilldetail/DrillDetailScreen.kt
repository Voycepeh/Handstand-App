package com.inversioncoach.app.ui.drilldetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
fun DrillDetailScreen(drillType: DrillType, onBack: () -> Unit) {
    val drill = DrillCatalog.byType(drillType)
    ScaffoldedScreen(title = drill.displayName, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DrillPreviewAnimation(animationSpec = drill.animationSpec, modifier = Modifier.size(180.dp))
            Text("Level: ${drill.level.name.lowercase()}", style = MaterialTheme.typography.bodyMedium)
            Text("Pattern: ${drill.movementPattern.name.lowercase()}", style = MaterialTheme.typography.bodyMedium)
            Text("Phases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            drill.mainPhases.forEach { Text("• ${it.label}", style = MaterialTheme.typography.bodySmall) }
            Text("Common faults", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(drill.commonFaults) { fault ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("Fault: $fault", modifier = Modifier.padding(12.dp))
                    }
                }
                items(drill.cues) { cue ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("Cue: $cue", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}
