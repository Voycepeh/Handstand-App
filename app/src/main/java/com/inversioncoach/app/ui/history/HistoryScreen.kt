package com.inversioncoach.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.common.formatLimiterText
import com.inversioncoach.app.ui.common.formatPrimaryPerformance
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.live.resolvePreferredReplayUri

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val context = LocalContext.current
    val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val repository = remember { ServiceLocator.repository(context) }
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val settings by repository.observeSettings().collectAsState(initial = UserSettings())
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Session insights", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Total sessions", "${sessions.size}", Modifier.weight(1f))
                MetricCard("With logged issues", "${sessions.count { it.issues.isNotBlank() }}", Modifier.weight(1f))
            }
            MetricCard("Top issue", topIssue, modifier = Modifier.fillMaxWidth())
            MetricCard(
                "Storage",
                "${formatMb(totalStorageBytes)} MB used • ${formatMb((maxStorageBytes - totalStorageBytes).coerceAtLeast(0L))} MB left",
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sessions) { session ->
                    val sizeMb = formatMb(sessionSizes[session.id] ?: 0L)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(session.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(session.drillType.displayName)
                            Text("Started: ${formatSessionDateTime(session.startedAtMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Duration: ${formatSessionDuration(computeSessionDurationMs(session.startedAtMs, session.completedAtMs))}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Limiter: ${formatLimiterText(session)}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(formatPrimaryPerformance(session), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (!session.annotatedVideoUri.isNullOrBlank()) "Annotated Replay Ready" else "Raw Only",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (isDebuggable && session.annotatedExportStatus.name == "FAILED") {
                                Text("Reason: ${session.annotatedExportFailureReason.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isDebuggable) {
                                val replaySource = resolvePreferredReplayUri(session).source
                                Text("replay source selected: $replaySource", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("rawPersistStatus: ${session.rawPersistStatus}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("annotatedExportStatus: ${session.annotatedExportStatus}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("annotatedExportFailureReason: ${session.annotatedExportFailureReason.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("rawVideoUri: ${session.rawVideoUri.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("annotatedVideoUri: ${session.annotatedVideoUri.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("overlay frame count: ${session.overlayFrameCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Storage: $sizeMb MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return "%.1f".format(mb)
}
