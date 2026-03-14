package com.inversioncoach.app.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val avgScore = sessions.map { it.overallScore }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
    val topIssue = sessions
        .flatMap { it.issues.split(",").map(String::trim).filter(String::isNotBlank) }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: "No consistent issue yet"

    ScaffoldedScreen(title = "History", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trend: Avg score $avgScore across ${sessions.size} sessions")
            Text("Most common fault: $topIssue")
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(session.id) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Session ID: ${session.id}")
                            Text("${session.drillType} • Score ${session.overallScore}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Limiter: ${session.limitingFactor}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Tap to review session")
                        }
                    }
                }
            }
        }
    }
}
