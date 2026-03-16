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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val context = LocalContext.current
    val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val repository = remember { ServiceLocator.repository(context) }
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val settings by repository.observeSettings().collectAsState(initial = UserSettings())
    val sessionSizes = remember { mutableStateMapOf<Long, Long>() }
    var selectedSort by remember { mutableStateOf(HistorySort.RECENCY) }
    var sortAscending by remember { mutableStateOf(false) }
    var totalStorageBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sessions) {
        val sizes = sessions.associate { it.id to repository.sessionStorageBytes(it.id) }
        sessionSizes.clear()
        sessionSizes.putAll(sizes)
        totalStorageBytes = repository.totalStorageBytes()
    }

    val maxStorageBytes = settings.maxStorageMb.toLong() * 1024L * 1024L
    val sortedSessions = remember(sessions, selectedSort, sortAscending, sessionSizes.toMap()) {
        val sorted = when (selectedSort) {
            HistorySort.RECENCY -> sessions.sortedBy { it.startedAtMs }
            HistorySort.STORAGE_SIZE -> sessions.sortedBy { sessionSizes[it.id] ?: 0L }
            HistorySort.SESSION_DURATION -> sessions.sortedBy {
                computeSessionDurationMs(it.startedAtMs, it.completedAtMs)
            }
        }
        if (sortAscending) sorted else sorted.reversed()
    }

    fun onSortSelected(sort: HistorySort) {
        if (selectedSort == sort) {
            sortAscending = !sortAscending
        } else {
            selectedSort = sort
            sortAscending = false
        }
    }

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
                MetricCard(
                    "Storage",
                    "${formatMb(totalStorageBytes)} MB used • ${formatMb((maxStorageBytes - totalStorageBytes).coerceAtLeast(0L))} MB left",
                    Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedSort == HistorySort.RECENCY,
                    onClick = { onSortSelected(HistorySort.RECENCY) },
                    label = { Text(sortLabel("By recency", selectedSort == HistorySort.RECENCY, sortAscending)) },
                )
                FilterChip(
                    selected = selectedSort == HistorySort.STORAGE_SIZE,
                    onClick = { onSortSelected(HistorySort.STORAGE_SIZE) },
                    label = { Text(sortLabel("By storage", selectedSort == HistorySort.STORAGE_SIZE, sortAscending)) },
                )
                FilterChip(
                    selected = selectedSort == HistorySort.SESSION_DURATION,
                    onClick = { onSortSelected(HistorySort.SESSION_DURATION) },
                    label = { Text(sortLabel("By duration", selectedSort == HistorySort.SESSION_DURATION, sortAscending)) },
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sortedSessions) { session ->
                    val sizeMb = formatMb(sessionSizes[session.id] ?: 0L)
                    val status = videoStatus(session)
                    val progress = uploadProgress(session)
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
                            Text("Time: ${formatSessionDateTime(session.startedAtMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if ((session.annotatedExportStatus == AnnotatedExportStatus.PROCESSING || session.annotatedExportStatus == AnnotatedExportStatus.PROCESSING_SLOW) && session.annotatedExportEtaSeconds != null) {
                                val etaMin = session.annotatedExportEtaSeconds / 60
                                val etaSec = session.annotatedExportEtaSeconds % 60
                                Text("Estimated time left: ${etaMin}m ${etaSec}s", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Storage: $sizeMb MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (isDebuggable && session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED) {
                                Text("Reason: ${session.annotatedExportFailureReason.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class HistorySort { RECENCY, STORAGE_SIZE, SESSION_DURATION }

private fun videoStatus(session: com.inversioncoach.app.model.SessionRecord): String = when {
    !session.annotatedVideoUri.isNullOrBlank() -> "Annotated replay ready"
    session.annotatedExportStatus == AnnotatedExportStatus.PROCESSING || session.annotatedExportStatus == AnnotatedExportStatus.PROCESSING_SLOW -> "Annotated video processing · ${session.annotatedExportPercent}%"
    !session.rawVideoUri.isNullOrBlank() -> "Raw replay ready"
    session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED -> "Annotated replay failed"
    else -> "Replay processing"
}

private fun uploadProgress(session: com.inversioncoach.app.model.SessionRecord): Float = when {
    !session.annotatedVideoUri.isNullOrBlank() -> 1f
    session.annotatedExportStatus == AnnotatedExportStatus.PROCESSING || session.annotatedExportStatus == AnnotatedExportStatus.PROCESSING_SLOW -> (session.annotatedExportPercent / 100f).coerceIn(0.05f, 0.95f)
    !session.rawVideoUri.isNullOrBlank() -> 0.6f
    session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED -> 0.35f
    else -> 0.15f
}

private fun sortLabel(baseLabel: String, isSelected: Boolean, isAscending: Boolean): String = when {
    !isSelected -> baseLabel
    isAscending -> "$baseLabel ↑"
    else -> "$baseLabel ↓"
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
