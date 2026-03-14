package com.inversioncoach.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())

    val averageScore = sessions.map { it.overallScore }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
    val latestScore = sessions.firstOrNull()?.overallScore ?: 0
    val previousScore = sessions.getOrNull(1)?.overallScore ?: latestScore
    val scoreDelta = latestScore - previousScore

    ScaffoldedScreen(title = "Progress", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Overall trend", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Average score: $averageScore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Sessions tracked: ${sessions.size}")
                }
            }

            Card {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Most recent change", style = MaterialTheme.typography.labelLarge)
                    Text("Latest score: $latestScore")
                    Text("Delta vs previous: ${if (scoreDelta >= 0) "+" else ""}$scoreDelta")
                }
            }
        }
    }
}
