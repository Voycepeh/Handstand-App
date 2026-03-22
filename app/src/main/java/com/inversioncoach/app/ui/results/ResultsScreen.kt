package com.inversioncoach.app.ui.results

import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.SessionSource
import com.inversioncoach.app.model.sessionMode
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatPrimaryPerformance
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.common.parseSessionMetrics
import com.inversioncoach.app.ui.common.buildSessionSummaryDisplay
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.live.mediaAssetExists
import com.inversioncoach.app.ui.live.selectReplayAsset
import com.inversioncoach.app.ui.live.SessionDiagnostics
import com.inversioncoach.app.ui.live.ReplayAssetSelection
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun ResultsScreen(sessionId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val session by repository.observeSession(sessionId).collectAsState(initial = null)
    val issueTimeline by repository.observeIssueTimeline(sessionId).collectAsState(initial = emptyList())
    var replaySelection by remember(sessionId) { mutableStateOf(ReplayAssetSelection(uri = null, label = "Replay unavailable")) }
    var replaySelectionKey by remember(sessionId) { mutableStateOf<String?>(null) }
    val rawUri = session?.takeIf(::isRawReplayPlayable)?.rawVideoUri?.takeIf(::mediaAssetExists)
    val hasReplay = replaySelection.uri != null
    val showRawVideoButton = shouldShowRawVideoButton(
        replayUri = replaySelection.uri,
        rawUri = rawUri,
    )
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf("") }
    var diagnosticsExpanded by remember { mutableStateOf(false) }
    var persistedDiagnostics by remember { mutableStateOf<String?>(null) }
    var lastRefreshSignature by remember(sessionId) { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    val collapsedIssueTimeline = remember(issueTimeline, session?.startedAtMs) {
        collapseIssueTimeline(issueTimeline, session?.startedAtMs)
    }
    val sessionSummaryDisplay = remember(session) { buildSessionSummaryDisplay(session) }
    val sessionMode = session?.sessionMode()

    LaunchedEffect(sessionId) {
        notes = repository.readSessionNotes(sessionId).orEmpty()
        persistedDiagnostics = repository.readSessionDiagnostics(sessionId)
        repository.reconcileRawPersistState(sessionId)
    }


    LaunchedEffect(session) {
        val activeSession = session ?: return@LaunchedEffect
        val candidate = selectReplayAsset(activeSession)
        val stable = isReplayStateStable(activeSession)
        val candidateKey = "${candidate.label}|${candidate.uri.orEmpty()}"
        if (replaySelectionKey == null || candidate.uri != replaySelection.uri || stable) {
            replaySelection = candidate
            replaySelectionKey = candidateKey
        }
    }

    LaunchedEffect(sessionId, replaySelection.uri, replaySelection.label, replaySelectionKey) {
        if (replaySelectionKey == null) return@LaunchedEffect
        SessionDiagnostics.logStructured(
            event = "results_source_selection",
            sessionId = sessionId,
            drillType = session?.drillType ?: com.inversioncoach.app.model.DrillType.FREESTYLE,
            rawUri = session?.rawVideoUri,
            annotatedUri = session?.annotatedVideoUri,
            overlayFrameCount = session?.overlayFrameCount ?: 0,
            failureReason = "selection=${replaySelection.label}",
        )
    }

    LaunchedEffect(
        session?.rawPersistStatus,
        session?.annotatedExportStatus,
        session?.annotatedVideoUri,
        replaySelection.uri,
    ) {
        val activeSession = session ?: return@LaunchedEffect
        val signature = listOf(
            activeSession.rawPersistStatus,
            activeSession.annotatedExportStatus,
            activeSession.annotatedExportFailureReason.orEmpty(),
            activeSession.annotatedVideoUri.orEmpty(),
            activeSession.bestPlayableUri.orEmpty(),
            replaySelection.uri.orEmpty(),
        ).joinToString("|")
        if (lastRefreshSignature == signature) return@LaunchedEffect
        lastRefreshSignature = signature
        SessionDiagnostics.logStructured(
            event = "results_screen_state_refresh",
            sessionId = activeSession.id,
            drillType = activeSession.drillType,
            rawUri = activeSession.rawVideoUri,
            annotatedUri = activeSession.annotatedVideoUri,
            overlayFrameCount = activeSession.overlayFrameCount,
            failureReason = "rawPersistStatus=${activeSession.rawPersistStatus};annotatedExportStatus=${activeSession.annotatedExportStatus};selectedReplaySource=${replaySelection.label};selectedReplayUri=${replaySelection.uri.orEmpty()};terminalStateReached=${activeSession.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY || activeSession.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED}",
        )
    }

    ScaffoldedScreen(title = "Results") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Session breakdown", style = MaterialTheme.typography.headlineSmall)
            if (session == null) {
                Text(
                    "Session details are loading or unavailable. Some actions may be disabled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            session?.let { activeSession ->
                val isProcessing = activeSession.annotatedExportStatus in setOf(
                    AnnotatedExportStatus.VALIDATING_INPUT,
                    AnnotatedExportStatus.PROCESSING,
                    AnnotatedExportStatus.PROCESSING_SLOW,
                ) || activeSession.rawPersistStatus == com.inversioncoach.app.model.RawPersistStatus.PROCESSING
                if (isProcessing || activeSession.annotatedExportStatus in setOf(AnnotatedExportStatus.ANNOTATED_FAILED, AnnotatedExportStatus.SKIPPED)) {
                    val isFailed = activeSession.annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_FAILED
                    val isSkipped = activeSession.annotatedExportStatus == AnnotatedExportStatus.SKIPPED
                    val rawFallbackAvailable = isRawReplayPlayable(activeSession)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                when {
                                    rawFallbackAvailable && (isFailed || isSkipped) -> "Raw replay available"
                                    isSkipped -> "Annotated export skipped"
                                    isFailed -> "Annotated export failed"
                                    else -> "Processing status"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (isProcessing) {
                                LinearProgressIndicator(
                                    progress = { (activeSession.annotatedExportPercent.coerceIn(0, 100) / 100f).coerceAtLeast(0.05f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("Stage: ${activeSession.annotatedExportStage}")
                                Text("Progress: ${activeSession.annotatedExportPercent}%")
                                Text("ETA: ${activeSession.annotatedExportEtaSeconds?.let { "${it}s" } ?: "-"}")
                            } else if (isSkipped) {
                                Text("Status: ${humanReadableStatus("SKIPPED")}")
                                if (rawFallbackAvailable) {
                                    Text("Annotated export failed. Raw replay is available.")
                                }
                            } else {
                                Text("Status: ${humanReadableStatus("FAILED")}")
                                if (rawFallbackAvailable) {
                                    Text("Annotated export failed. Raw replay is available.")
                                }
                            }
                            StatusRow("Replay source", if (rawFallbackAvailable && !isProcessing) "Raw" else replaySelection.label.removeSuffix(" replay"))
                            StatusRow("Raw", humanReadableStatus(activeSession.rawPersistStatus.name))
                            if (activeSession.rawPersistFailureReason in setOf("RAW_REPLAY_INVALID", "RAW_MEDIA_CORRUPT", "SOURCE_VIDEO_UNREADABLE")) {
                                Text(
                                    "Raw replay file was copied but is not decodable (${activeSession.rawPersistFailureReason}).",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            StatusRow("Annotated", humanReadableStatus(activeSession.annotatedExportStatus.name))
                            if (!activeSession.annotatedExportFailureReason.isNullOrBlank()) {
                                StatusRow(
                                    "Failure reason",
                                    humanReadableStatus(activeSession.annotatedExportFailureReason.orEmpty()),
                                    valueColor = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (isProcessing) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        repository.updateAnnotatedExportStatus(sessionId, AnnotatedExportStatus.ANNOTATED_FAILED)
                                        repository.updateAnnotatedExportFailureReason(sessionId, "EXPORT_CANCELLED")
                                    }
                                }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Session ID: $sessionId")
                    Text("Type: ${sessionTypeLabel(session)}")
                    Text("Started: ${formatSessionDateTime(session?.startedAtMs ?: 0L)}")
                    Text("Duration: ${formatSessionDuration(computeSessionDurationMs(session?.startedAtMs ?: 0L, session?.completedAtMs ?: 0L))}")
                    session?.let { Text(formatPrimaryPerformance(it)) }
                    session?.let {
                        val metrics = parseSessionMetrics(it.metricsJson)
                        if (metrics.trackingMode == "HOLD_BASED") {
                            Text("Alignment %: ${((metrics.alignmentRate ?: 0f) * 100f).toInt()} • Avg alignment: ${metrics.avgAlignment ?: 0}")
                            Text("Avg stability: ${metrics.avgStability ?: 0}")
                        } else {
                            Text("Accepted reps: ${metrics.acceptedReps ?: metrics.validReps ?: 0} • Rejected: ${metrics.rejectedReps ?: 0}")
                            Text("Avg rep score: ${metrics.avgRepScore ?: 0} • Best rep: ${metrics.bestRepScore ?: 0}")
                            if (!metrics.repFailureReason.isNullOrBlank()) Text("Top failure reason: ${metrics.repFailureReason}")
                        }
                    }
                    Text(
                        "Top wins: ${sessionSummaryDisplay.wins}",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Top issues: ${sessionSummaryDisplay.issues}",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Top improvement focus: ${sessionSummaryDisplay.improvement}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Issue timeline summary")
                    if (sessionMode == SessionMode.FREESTYLE) {
                        Text("Issue timeline is not tracked for freestyle sessions")
                    } else if (collapsedIssueTimeline.isEmpty()) {
                        Text("No issue events captured for this session")
                    } else {
                        collapsedIssueTimeline.forEach { summary ->
                            Text(summary)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Session notes") },
            )

            Button(
                onClick = { openVideo(context, replaySelection.uri, sessionId) },
                enabled = hasReplay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(replaySelection.label)
            }
            if (!hasReplay) {
                val rawFailure = session?.rawPersistFailureReason
                val message = if (rawFailure in setOf("RAW_REPLAY_INVALID", "RAW_MEDIA_CORRUPT", "SOURCE_VIDEO_UNREADABLE")) {
                    "Replay unavailable: raw file exists but cannot be decoded ($rawFailure)."
                } else {
                    "No replay asset is available for this session."
                }
                Text(message)
            }
            Text(
                replayAvailabilityBadge(replaySelection.label),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showRawVideoButton) {
                Button(
                    onClick = { openVideo(context, rawUri) },
                    enabled = !rawUri.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Raw replay")
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val inMemoryReport = SessionDiagnostics.buildReport(session, sessionId)
                    val report = persistedDiagnostics?.takeIf { it.isNotBlank() } ?: inMemoryReport
                    val events = SessionDiagnostics.eventsForSession(sessionId)
                    val canCopyDiagnostics = report.isNotBlank()
                    Text("Developer diagnostics", style = MaterialTheme.typography.titleMedium)
                    Text(SessionDiagnostics.rootCauseSummary(session, events))
                    StatusRow("Replay source", if (replaySelection.label == "Annotated replay") "Annotated" else if (replaySelection.label == "Raw replay") "Raw" else "None")
                    StatusRow("Raw", humanReadableStatus(session?.rawPersistStatus?.name))
                    StatusRow("Annotated", humanReadableStatus(session?.annotatedExportStatus?.name))
                    session?.annotatedExportFailureReason?.takeIf { it.isNotBlank() }?.let { reason ->
                        StatusRow("Failure", humanReadableStatus(reason), valueColor = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { diagnosticsExpanded = !diagnosticsExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (diagnosticsExpanded) "Hide diagnostics" else "Show diagnostics")
                    }
                    if (canCopyDiagnostics) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(report))
                                Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Copy diagnostics log") }
                    }
                    if (diagnosticsExpanded) {
                        Text(
                            buildDeveloperStateDump(session, replaySelection.label),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 16,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            report,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 24,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        repository.saveSessionNotes(sessionId, notes)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save note") }
            Button(
                onClick = {
                    shareVideoOnly(
                        context = context,
                        rawVideoUri = rawUri,
                        annotatedVideoUri = session?.annotatedVideoUri?.takeIf(::mediaAssetExists),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !replaySelection.uri.isNullOrBlank() || !rawUri.isNullOrBlank(),
            ) { Text("Share video (.mp4)") }
            Button(
                onClick = {
                    scope.launch {
                        repository.clearSessionVideos(sessionId)
                        Toast.makeText(context, "Session videos deleted. Session history kept.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !replaySelection.uri.isNullOrBlank() || !rawUri.isNullOrBlank(),
            ) {
                Text("Delete videos only (keep session)")
            }
            Button(
                onClick = {
                    scope.launch {
                        repository.deleteSession(sessionId)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete this session and all data")
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}


private fun mediaDurationMs(uri: String?): Long {
    val target = uri?.takeIf(::mediaAssetExists) ?: return 0L
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        val path = android.net.Uri.parse(target).path ?: return 0L
        retriever.setDataSource(path)
        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}


private fun sessionTypeLabel(session: com.inversioncoach.app.model.SessionRecord?): String {
    val active = session ?: return "-"
    return when (active.sessionSource) {
        SessionSource.UPLOADED_VIDEO -> "Uploaded video analysis"
        SessionSource.LIVE_COACHING -> active.drillType.displayName
    }
}

private fun formatDurationWithMs(durationMs: Long): String = "${formatSessionDuration(durationMs)} (${durationMs} ms)"

internal fun shouldShowRawVideoButton(replayUri: String?, rawUri: String?): Boolean =
    !rawUri.isNullOrBlank() && replayUri != rawUri

internal fun replayAvailabilityBadge(replayLabel: String): String =
    if (replayLabel == "Annotated replay") "Annotated Replay Ready" else "Raw Only"


internal fun formatElapsedDuration(elapsedMs: Long?): String {
    val safeMs = (elapsedMs ?: 0L).coerceAtLeast(0L)
    val totalSec = safeMs / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return if (min > 0L) "${min}m ${sec}s" else "${sec}s"
}

@Composable
private fun StatusRow(
    label: String,
    value: String?,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label:")
        Text(value, color = valueColor)
    }
}

private fun humanReadableStatus(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return value
        .replace('_', ' ')
        .lowercase(Locale.US)
        .replaceFirstChar { it.titlecase(Locale.US) }
}

private fun buildDeveloperStateDump(
    session: com.inversioncoach.app.model.SessionRecord?,
    replayLabel: String,
): String = buildString {
    appendLine("replay source selected: ${if (replayLabel == "Annotated replay") "annotated" else if (replayLabel == "Raw replay") "raw" else "none"}")
    appendLine("rawPersistStatus: ${session?.rawPersistStatus}")
    appendLine("rawVideoUri: ${session?.rawVideoUri.orEmpty()}")
    appendLine("annotatedExportStatus: ${session?.annotatedExportStatus}")
    appendLine("annotatedExportFailureReason: ${session?.annotatedExportFailureReason.orEmpty()}")
    appendLine("annotatedVideoUri: ${session?.annotatedVideoUri.orEmpty()}")
    appendLine("overlay frame count: ${session?.overlayFrameCount ?: 0}")
    appendLine("overlayTimelineUri: ${session?.overlayTimelineUri.orEmpty()}")
    appendLine("export started at: ${session?.annotatedExportLastUpdatedAt ?: 0L}")
    appendLine("export completed at: ${session?.annotatedExportedAtMs ?: 0L}")
    appendLine("raw duration: ${formatDurationWithMs(mediaDurationMs(session?.rawVideoUri))}")
    appendLine("annotated duration: ${formatDurationWithMs(mediaDurationMs(session?.annotatedVideoUri))}")
}

private data class CollapsedIssueRange(
    val issue: String,
    val startMs: Long,
    val endMs: Long,
    val peakSeverity: Int,
)

private fun collapseIssueTimeline(issueEvents: List<IssueEvent>, sessionStartMs: Long?): List<String> {
    if (issueEvents.isEmpty()) return emptyList()
    val sorted = issueEvents.sortedBy { it.timestampMs }
    val merged = mutableListOf<CollapsedIssueRange>()
    val maxGapMs = 1200L

    for (event in sorted) {
        val last = merged.lastOrNull()
        val shouldMerge = last != null &&
            last.issue == event.issue &&
            event.timestampMs - last.endMs <= maxGapMs

        if (shouldMerge) {
            merged[merged.lastIndex] = last!!.copy(
                endMs = event.timestampMs,
                peakSeverity = maxOf(last.peakSeverity, event.severity),
            )
        } else {
            merged += CollapsedIssueRange(
                issue = event.issue,
                startMs = event.timestampMs,
                endMs = event.timestampMs,
                peakSeverity = event.severity,
            )
        }
    }

    return merged.map { range ->
        val start = formatElapsed(sessionStartMs, range.startMs)
        val end = formatElapsed(sessionStartMs, range.endMs)
        val durationSec = ((range.endMs - range.startMs).coerceAtLeast(0L)) / 1000.0
        if (start == end) {
            "$start ${range.issue} (peak sev ${range.peakSeverity})"
        } else {
            "$start–$end ${range.issue} (${String.format("%.1f", durationSec)}s, peak sev ${range.peakSeverity})"
        }
    }
}

private fun formatElapsed(startedAtMs: Long?, timestampMs: Long?): String {
    if (startedAtMs == null || timestampMs == null || timestampMs < startedAtMs) return "--:--"
    val elapsedSeconds = ((timestampMs - startedAtMs) / 1000).toInt()
    return "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
}

private fun openVideo(context: android.content.Context, videoUri: String?) {
    openVideo(context, videoUri, null)
}

private fun openVideo(context: android.content.Context, videoUri: String?, sessionId: Long?) {
    if (videoUri.isNullOrBlank()) return
    SessionDiagnostics.record(
        sessionId = sessionId,
        stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
        status = SessionDiagnostics.Status.PROGRESS,
        message = "player preparing",
        metrics = mapOf("uri" to videoUri),
    )
    val resolvedUri = toSharableVideoUri(context, Uri.parse(videoUri)) ?: run {
        Toast.makeText(context, "Unable to open video file.", Toast.LENGTH_SHORT).show()
        SessionDiagnostics.record(
            sessionId = sessionId,
            stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
            status = SessionDiagnostics.Status.FAILED,
            message = "onPlayerError: unresolved sharable URI",
            errorCode = "PLAYER_URI_RESOLUTION_FAILED",
            metrics = mapOf("uri" to videoUri),
        )
        return
    }
    val mimeType = context.contentResolver.getType(resolvedUri) ?: "video/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(resolvedUri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        val chooser = Intent.createChooser(intent, "Open video")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
        SessionDiagnostics.record(
            sessionId = sessionId,
            stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
            status = SessionDiagnostics.Status.SUCCEEDED,
            message = "player ready",
            metrics = mapOf("uri" to videoUri),
        )
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            Toast.makeText(context, "No video player available to open this file.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Unable to open video file.", Toast.LENGTH_SHORT).show()
        }
        SessionDiagnostics.record(
            sessionId = sessionId,
            stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
            status = SessionDiagnostics.Status.FAILED,
            message = "onPlayerError: ${error::class.java.name}",
            errorCode = error.message,
            throwable = error,
            metrics = mapOf("uri" to videoUri),
        )
    }
}

private fun isReplayStateStable(session: com.inversioncoach.app.model.SessionRecord): Boolean {
    val annotatedTerminal = session.annotatedExportStatus in setOf(
        AnnotatedExportStatus.ANNOTATED_READY,
        AnnotatedExportStatus.ANNOTATED_FAILED,
        AnnotatedExportStatus.SKIPPED,
        AnnotatedExportStatus.NOT_STARTED,
    )
    val rawTerminal = session.rawPersistStatus != com.inversioncoach.app.model.RawPersistStatus.PROCESSING
    return annotatedTerminal && rawTerminal
}

private fun isRawReplayPlayable(session: com.inversioncoach.app.model.SessionRecord): Boolean {
    val invalidReason = session.rawPersistFailureReason in setOf("RAW_REPLAY_INVALID", "RAW_MEDIA_CORRUPT", "SOURCE_VIDEO_UNREADABLE")
    if (invalidReason) return false
    val rawUri = session.rawFinalUri ?: session.rawVideoUri ?: session.rawMasterUri
    if (rawUri.isNullOrBlank() || !mediaAssetExists(rawUri)) return false
    val bestPlayableUri = session.bestPlayableUri
    val bestPlayableReadable = mediaAssetExists(bestPlayableUri)
    return bestPlayableUri.isNullOrBlank() || bestPlayableUri == rawUri || !bestPlayableReadable
}

private fun toSharableVideoUri(context: android.content.Context, sourceUri: Uri): Uri? {
    if (sourceUri.scheme != "file") return sourceUri
    val sourcePath = sourceUri.path ?: return null
    val sourceFile = File(sourcePath)
    if (!sourceFile.exists()) return null
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        sourceFile,
    )
}

private fun shareVideoOnly(
    context: android.content.Context,
    rawVideoUri: String?,
    annotatedVideoUri: String?,
) {
    val preferredShareUri = listOfNotNull(annotatedVideoUri, rawVideoUri)
        .firstNotNullOfOrNull { uri -> toSharableVideoUri(context, Uri.parse(uri)) }

    if (preferredShareUri == null) {
        Toast.makeText(context, "No video file available to share.", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, preferredShareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, "session-video", preferredShareUri)
    }
    context.startActivity(Intent.createChooser(intent, "Share session video"))
}
