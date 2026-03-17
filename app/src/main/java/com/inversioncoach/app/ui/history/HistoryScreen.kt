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
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.model.AnnotatedExportStage
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.live.SessionDiagnostics

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val settings by repository.observeSettings().collectAsState(initial = UserSettings())
    val sessionSizes = remember { mutableStateMapOf<Long, Long>() }
    val lastRefreshSignatures = remember { mutableStateMapOf<Long, String>() }
    var selectedSort by remember { mutableStateOf(HistorySort.RECENCY) }
    var sortAscending by remember { mutableStateOf(false) }
    var totalStorageBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sessions) {
        val sizes = sessions.associate { it.id to repository.sessionStorageBytes(it.id) }
        sessionSizes.clear()
        sessionSizes.putAll(sizes)
        totalStorageBytes = repository.totalStorageBytes()

        sessions.forEach { session ->
            val signature = listOf(
                session.rawPersistStatus,
                session.annotatedExportStatus,
                session.annotatedExportFailureReason.orEmpty(),
                session.rawVideoUri.orEmpty(),
                session.annotatedVideoUri.orEmpty(),
                session.bestPlayableUri.orEmpty(),
            ).joinToString("|")
            val previous = lastRefreshSignatures[session.id]
            if (previous != signature) {
                lastRefreshSignatures[session.id] = signature
                SessionDiagnostics.logStructured(
                    event = "history_screen_session_refresh",
                    sessionId = session.id,
                    drillType = session.drillType,
                    rawUri = session.rawVideoUri,
                    annotatedUri = session.annotatedVideoUri,
                    overlayFrameCount = session.overlayFrameCount,
                    failureReason = "rawPersistStatus=${session.rawPersistStatus};annotatedExportStatus=${session.annotatedExportStatus};selectedReplayUri=${session.bestPlayableUri.orEmpty()};terminalStateReached=${session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY || session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED}",
                )
            }
        }
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
                            Text(historyCardDurationText(session), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Time: ${formatSessionDateTime(session.startedAtMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Storage: $sizeMb MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

internal fun historyCardDurationText(session: com.inversioncoach.app.model.SessionRecord): String {
    val durationMs = computeSessionDurationMs(session.startedAtMs, session.completedAtMs)
    return "Duration: ${formatSessionDuration(durationMs)}"
}

private enum class HistorySort { RECENCY, STORAGE_SIZE, SESSION_DURATION }

internal fun videoStatus(session: com.inversioncoach.app.model.SessionRecord): String = when {
    session.rawPersistStatus == RawPersistStatus.FAILED -> "Failed"
    session.rawPersistStatus == RawPersistStatus.PROCESSING -> "Copying raw video"
    session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY -> "Ready"
    session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED -> "Failed"
    session.annotatedExportStatus in setOf(AnnotatedExportStatus.PROCESSING, AnnotatedExportStatus.PROCESSING_SLOW) -> {
        val stageLabel = when (session.annotatedExportStage) {
            AnnotatedExportStage.QUEUED -> "Queued"
            AnnotatedExportStage.PREPARING -> "Preparing"
            AnnotatedExportStage.LOADING_OVERLAYS -> "Building overlay timeline"
            AnnotatedExportStage.DECODING_SOURCE -> "Analyzing frames"
            AnnotatedExportStage.RENDERING -> "Building overlay timeline"
            AnnotatedExportStage.ENCODING -> "Exporting annotated video"
            AnnotatedExportStage.VERIFYING -> "Verifying output"
            AnnotatedExportStage.COMPLETED -> "Completed"
            AnnotatedExportStage.FAILED -> "Failed"
        }
        "${stageLabel} ${session.annotatedExportPercent}%"
    }
    session.rawPersistStatus == RawPersistStatus.SUCCEEDED -> "Raw replay ready"
    else -> "Replay unavailable"
}

internal fun uploadProgress(session: com.inversioncoach.app.model.SessionRecord): Float = when {
    session.rawPersistStatus == RawPersistStatus.FAILED -> 1f
    session.rawPersistStatus == RawPersistStatus.PROCESSING -> 0.2f
    session.annotatedExportStatus in setOf(AnnotatedExportStatus.PROCESSING, AnnotatedExportStatus.PROCESSING_SLOW) ->
        (session.annotatedExportPercent.coerceIn(0, 100) / 100f).coerceAtLeast(0.2f)
    session.rawPersistStatus == RawPersistStatus.SUCCEEDED && session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY -> 1f
    session.rawPersistStatus == RawPersistStatus.SUCCEEDED && session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED -> 1f
    session.rawPersistStatus == RawPersistStatus.SUCCEEDED -> 0.7f
    else -> 0f
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
