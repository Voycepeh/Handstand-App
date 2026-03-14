package com.inversioncoach.app.ui.drilldetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.motion.DrillCatalog
import com.inversioncoach.app.model.DrillType
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
            DrillPreviewAnimation(keyframes = drill.keyframes)
            Text("Level: ${drill.level}", style = MaterialTheme.typography.bodyMedium)
            Text("Pattern: ${drill.movementPattern}", style = MaterialTheme.typography.bodyMedium)
            Text("Phases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            drill.engine.phases.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text("Checkpoints", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(drill.checkpoints) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("• $item", modifier = Modifier.padding(12.dp))
                    }
                }
                items(drill.engine.faults) { fault ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("Fault: $fault", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}
