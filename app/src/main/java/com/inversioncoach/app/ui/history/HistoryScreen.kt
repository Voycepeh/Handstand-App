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
import androidx.compose.material3.TextButton
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
import com.inversioncoach.app.media.SessionMediaOwnership
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.storage.repository.resolvedDrillId
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.live.SessionDiagnostics

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    drillIdFilter: String? = null,
    comparisonMode: Boolean = false,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val sessions by repository.observeHistorySessions(drillIdFilter).collectAsState(initial = emptyList())
    val comparedSessionIds by repository.observeComparedSessionIds().collectAsState(initial = emptyList())
    val latestComparisonScores by repository.observeLatestComparisonScores().collectAsState(initial = emptyMap())
    val drills by repository.getAllDrills().collectAsState(initial = emptyList())
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
    val filteredSessions = sessions

    val sortedSessions = remember(filteredSessions, selectedSort, sortAscending, sessionSizes.toMap()) {
        val sorted = when (selectedSort) {
            HistorySort.RECENCY -> filteredSessions.sortedBy { it.startedAtMs }
            HistorySort.STORAGE_SIZE -> filteredSessions.sortedBy { sessionSizes[it.id] ?: 0L }
            HistorySort.SESSION_DURATION -> filteredSessions.sortedBy {
                computeSessionDurationMs(it.startedAtMs, it.completedAtMs)
            }
        }
        if (sortAscending) sorted else sorted.reversed()
    }
    val compareSelection = remember(sortedSessions, comparedSessionIds) {
        selectCompareAttemptTargets(sortedSessions, comparedSessionIds)
    }

    fun onSortSelected(sort: HistorySort) {
        if (selectedSort == sort) {
            sortAscending = !sortAscending
        } else {
            selectedSort = sort
            sortAscending = false
        }
    }

    ScaffoldedScreen(title = if (comparisonMode) "Sessions" else "History", onBack = onBack) { padding ->
        DrillSessionsSection(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            drillIdFilter = drillIdFilter,
            comparisonMode = comparisonMode,
            drills = drills,
            sortedSessions = sortedSessions,
            selectedSort = selectedSort,
            sortAscending = sortAscending,
            sessionSizes = sessionSizes,
            totalStorageBytes = totalStorageBytes,
            maxStorageBytes = maxStorageBytes,
            comparedSessionIds = comparedSessionIds,
            latestComparisonScores = latestComparisonScores,
            onSortSelected = ::onSortSelected,
            onOpenSession = onOpenSession,
            compareSelection = compareSelection,
        )
    }
}

@Composable
fun DrillSessionsSection(
    modifier: Modifier = Modifier,
    drillIdFilter: String?,
    comparisonMode: Boolean,
    drills: List<com.inversioncoach.app.model.DrillDefinitionRecord>,
    sortedSessions: List<com.inversioncoach.app.model.SessionRecord>,
    selectedSort: HistorySort,
    sortAscending: Boolean,
    sessionSizes: Map<Long, Long>,
    totalStorageBytes: Long,
    maxStorageBytes: Long,
    comparedSessionIds: List<Long>,
    latestComparisonScores: Map<Long, Int>,
    onSortSelected: (HistorySort) -> Unit,
    onOpenSession: (Long) -> Unit,
    compareSelection: CompareAttemptSelection = CompareAttemptSelection(emptySet(), null, hasEnoughCandidates = false),
    onOpenComparisonTools: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sessions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (onOpenComparisonTools != null) {
                TextButton(onClick = onOpenComparisonTools) { Text("Compare Sessions") }
            }
        }
        Text(
            if (comparisonMode) "Select sessions to compare or open one to review results."
            else "Review session history and open any session for details.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (comparisonMode && !compareSelection.hasEnoughCandidates) {
            Text("Need at least 2 sessions for compare attempts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!drillIdFilter.isNullOrBlank()) {
            val drillName = drills.firstOrNull { it.id == drillIdFilter }?.name ?: drillIdFilter
            Text("Filtered to drill: $drillName", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Total sessions", "${sortedSessions.size}", Modifier.weight(1f))
            MetricCard(
                "Storage",
                "${formatGb(totalStorageBytes)} GB used • ${formatGb((maxStorageBytes - totalStorageBytes).coerceAtLeast(0L))} GB left",
                Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistorySort.entries.forEach { sort ->
                FilterChip(
                    selected = selectedSort == sort,
                    onClick = { onSortSelected(sort) },
                    label = { Text(sortLabel(sort.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, selectedSort == sort, sortAscending)) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(sortedSessions) { session ->
                val sizeGb = formatGb(sessionSizes[session.id] ?: 0L)
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
                        val drillId = session.resolvedDrillId()
                        val drillName = drills.firstOrNull { it.id == drillId }?.name
                        if (!drillId.isNullOrBlank()) {
                            Text("Drill: ${drillName ?: drillId}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(historyCardDurationText(session), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Time: ${formatSessionDateTime(session.startedAtMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Profile: ${session.userProfileId ?: "unknown"} • Body v${session.bodyProfileVersion ?: 0}" +
                                if (session.usedDefaultBodyModel) " (default model)" else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (comparedSessionIds.contains(session.id)) {
                            Text("Reference comparison saved", color = MaterialTheme.colorScheme.primary)
                            latestComparisonScores[session.id]?.let { score ->
                                Text("Similarity score: $score", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (comparisonMode && compareSelection.anchorSessionId == session.id) {
                            Text("Current compare anchor", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Storage: $sizeGb GB", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

enum class HistorySort { RECENCY, STORAGE_SIZE, SESSION_DURATION }

internal fun videoStatus(session: com.inversioncoach.app.model.SessionRecord): String = when {
    session.rawPersistStatus == RawPersistStatus.FAILED -> "Failed"
    session.rawPersistStatus == RawPersistStatus.PROCESSING -> "Copying raw video"
    session.rawPersistFailureReason in setOf("RAW_REPLAY_INVALID", "RAW_MEDIA_CORRUPT", "SOURCE_VIDEO_UNREADABLE") -> "Raw replay unavailable (${session.rawPersistFailureReason})"
    session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY -> "Ready"
    session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED -> "Failed"
    session.annotatedExportStatus in setOf(AnnotatedExportStatus.VALIDATING_INPUT, AnnotatedExportStatus.PROCESSING, AnnotatedExportStatus.PROCESSING_SLOW) -> {
        val stageLabel = when (session.annotatedExportStage) {
            AnnotatedExportStage.QUEUED -> if (session.annotatedExportStatus == AnnotatedExportStatus.VALIDATING_INPUT) "Validating input" else "Queued"
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
    session.annotatedExportStatus == AnnotatedExportStatus.SKIPPED && rawReplayPlayableForUi(session) -> "Raw replay ready (annotated skipped)"
    rawReplayPlayableForUi(session) -> "Raw replay ready"
    else -> "Replay unavailable"
}

internal fun uploadProgress(session: com.inversioncoach.app.model.SessionRecord): Float = when {
    session.rawPersistStatus == RawPersistStatus.FAILED -> 1f
    session.rawPersistStatus == RawPersistStatus.PROCESSING -> 0.2f
    session.annotatedExportStatus in setOf(AnnotatedExportStatus.VALIDATING_INPUT, AnnotatedExportStatus.PROCESSING, AnnotatedExportStatus.PROCESSING_SLOW) ->
        (session.annotatedExportPercent.coerceIn(0, 100) / 100f).coerceAtLeast(0.2f)
    session.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY -> 1f
    rawReplayPlayableForUi(session) -> 1f
    session.rawPersistStatus == RawPersistStatus.SUCCEEDED -> 0.7f
    else -> 0f
}

private fun rawReplayPlayableForUi(session: com.inversioncoach.app.model.SessionRecord): Boolean {
    return SessionMediaOwnership.rawReplayPlayable(session)
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

private fun formatGb(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return "%.1f".format(gb)
}
