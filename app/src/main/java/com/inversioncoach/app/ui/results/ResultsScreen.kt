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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.IssueEvent
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

    val collapsedIssueTimeline = remember(issueTimeline, session?.startedAtMs) {
        collapseIssueTimeline(issueTimeline, session?.startedAtMs)
    }
    val sessionSummaryDisplay = remember(session) { buildSessionSummaryDisplay(session) }
    val sessionMode = session?.sessionMode()

    LaunchedEffect(sessionId) {
        notes = repository.readSessionNotes(sessionId).orEmpty()
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Session ID: $sessionId")
                    Text("Type: ${session?.drillType?.displayName ?: "-"}")
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
            if (session?.annotatedExportStatus == com.inversioncoach.app.model.AnnotatedExportStatus.FAILED && !rawUri.isNullOrBlank()) {
                Text("Annotated replay unavailable, showing raw replay")
            }
            if (showRawVideoButton) {
                Button(
                    onClick = { openVideo(context, rawUri) },
                    enabled = !rawUri.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Raw replay")
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
                        annotatedVideoUri = replaySelection.uri,
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

internal fun shouldShowRawVideoButton(replayUri: String?, rawUri: String?): Boolean =
    !rawUri.isNullOrBlank() && replayUri != rawUri


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
