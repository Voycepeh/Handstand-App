package com.inversioncoach.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.ui.components.ScaffoldedScreen

private val samples = listOf(
    SessionRecord(1, "Morning chest-to-wall", DrillType.CHEST_TO_WALL_HANDSTAND, 0, 1, 74, "line_quality", "rib_pelvis_control", "ribs flare", "good shoulders", "{}", null, null, 18000, 42000, "shoulder elevation"),
    SessionRecord(2, "Pike strength", DrillType.PIKE_PUSH_UP, 0, 1, 81, "tempo_control", "hip_height", "hips low", "great tempo", "{}", null, null, 21000, 51000, "hip height"),
)

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    ScaffoldedScreen(title = "History", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trend: Avg score ↑ 6% in last 2 weeks")
            Text("Most common fault: ribs flaring")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(samples) { session ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(session.title)
                            Text("${session.drillType} • Score ${session.overallScore}")
                            Text("Limiter: ${session.limitingFactor}")
                        }
                    }
                }
            }
        }
    }
}
