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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val settings by repository.observeSettings().collectAsState(initial = UserSettings())
    val avgScore = sessions.map { it.overallScore }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
    val topIssue = sessions
        .flatMap { it.issues.split(",").map(String::trim).filter(String::isNotBlank) }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: "No consistent issue yet"

    val sessionSizes = remember { mutableStateMapOf<Long, Long>() }
    var totalStorageBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sessions) {
        val sizes = sessions.associate { it.id to repository.sessionStorageBytes(it.id) }
        sessionSizes.clear()
        sessionSizes.putAll(sizes)
        totalStorageBytes = repository.totalStorageBytes()
    }

    val maxStorageBytes = settings.maxStorageMb.toLong() * 1024L * 1024L

    ScaffoldedScreen(title = "History", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trend: Avg score $avgScore across ${sessions.size} sessions")
            Text("Most common fault: $topIssue")
            Text("Storage used: ${formatMb(totalStorageBytes)} MB / ${settings.maxStorageMb} MB")
            Text("Remaining: ${formatMb((maxStorageBytes - totalStorageBytes).coerceAtLeast(0L))} MB")
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions) { session ->
                    val sizeMb = formatMb(sessionSizes[session.id] ?: 0L)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(session.id) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Session ID: ${session.id}")
                            Text("${session.drillType} • Score ${session.overallScore}")
                            Text("Limiter: ${session.limitingFactor}")
                            Text("Storage: $sizeMb MB")
                            Text("Tap to review session")
                        }
                    }
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return "%.1f".format(mb)
}
