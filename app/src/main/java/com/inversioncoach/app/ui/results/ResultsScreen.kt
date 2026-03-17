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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.AnnotatedExportStage
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
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ResultsScreen(sessionId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val session by repository.observeSession(sessionId).collectAsState(initial = null)
    val issueTimeline by repository.observeIssueTimeline(sessionId).collectAsState(initial = emptyList())
    val replaySelection = remember(session) { selectReplayAsset(session) }
    val rawUri = session?.rawVideoUri?.takeIf(::mediaAssetExists)
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


    LaunchedEffect(sessionId, replaySelection.uri, replaySelection.label) {
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
                    val rawFallbackAvailable = !activeSession.rawFinalUri.isNullOrBlank() || !activeSession.rawVideoUri.isNullOrBlank()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                when {
                                    isSkipped -> "Annotated export skipped"
                                    isFailed -> "Processing failed"
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
                                Text("Stage: ${AnnotatedExportStage.COMPLETED}")
                                if (rawFallbackAvailable) {
                                    Text("Annotated export was skipped, raw replay available")
                                }
                            } else {
                                Text("Stage: ${AnnotatedExportStage.FAILED}")
                                if (rawFallbackAvailable) {
                                    Text("Annotated export failed, raw replay available")
                                }
                            }
                            Text("Raw: ${activeSession.rawPersistStatus}")
                            Text("Annotated: ${activeSession.annotatedExportStatus}")
                            if (!activeSession.annotatedExportFailureReason.isNullOrBlank()) {
                                Text("Failure: ${activeSession.annotatedExportFailureReason}", color = MaterialTheme.colorScheme.error)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val report = repository.readSessionDiagnostics(sessionId).orEmpty().ifBlank { "No log entries yet." }
                                    clipboardManager.setText(AnnotatedString(report))
                                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                                }) { Text("Copy log") }
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
                onClick = { openVideo(context, replaySelection.uri) },
                enabled = hasReplay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(replaySelection.label)
            }
            if (!hasReplay) {
                Text("No replay asset is available for this session.")
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
            if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                Text("replay source selected: ${if (replaySelection.label == "Annotated replay") "annotated" else if (replaySelection.label == "Raw replay") "raw" else "none"}")
                Text("rawPersistStatus: ${session?.rawPersistStatus}")
                Text("rawVideoUri: ${session?.rawVideoUri.orEmpty()}")
                Text("annotatedExportStatus: ${session?.annotatedExportStatus}")
                Text("annotatedExportFailureReason: ${session?.annotatedExportFailureReason.orEmpty()}")
                Text("annotatedVideoUri: ${session?.annotatedVideoUri.orEmpty()}")
                Text("overlay frame count: ${session?.overlayFrameCount ?: 0}")
                Text("overlayTimelineUri: ${session?.overlayTimelineUri.orEmpty()}")
                Text("export started at: ${session?.annotatedExportLastUpdatedAt ?: 0L}")
                Text("export completed at: ${session?.annotatedExportedAtMs ?: 0L}")
                Text("raw duration: ${formatDurationWithMs(mediaDurationMs(session?.rawVideoUri))}")
                Text("annotated duration: ${formatDurationWithMs(mediaDurationMs(session?.annotatedVideoUri))}")
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
                    Text("Technical diagnostics", style = MaterialTheme.typography.titleMedium)
                    Text(SessionDiagnostics.rootCauseSummary(session, events))
                    Button(onClick = { diagnosticsExpanded = !diagnosticsExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (diagnosticsExpanded) "Hide diagnostics" else "Show diagnostics")
                    }
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(report))
                            Toast.makeText(context, "Diagnostic logs copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Export diagnostic logs") }
                    if (diagnosticsExpanded) {
                        Text(
                            report,
                            style = MaterialTheme.typography.bodySmall,
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
    if (videoUri.isNullOrBlank()) return
    val resolvedUri = toSharableVideoUri(context, Uri.parse(videoUri)) ?: run {
        Toast.makeText(context, "Unable to open video file.", Toast.LENGTH_SHORT).show()
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
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            Toast.makeText(context, "No video player available to open this file.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Unable to open video file.", Toast.LENGTH_SHORT).show()
        }
    }
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
