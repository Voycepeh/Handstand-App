package com.inversioncoach.app.ui.results

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.live.mediaAssetExists
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(sessionId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val session by repository.observeSession(sessionId).collectAsState(initial = null)
    val frameMetrics by repository.observeSessionFrameMetrics(sessionId).collectAsState(initial = emptyList())
    val issueTimeline by repository.observeIssueTimeline(sessionId).collectAsState(initial = emptyList())
    val avgScore = frameMetrics.map { it.overallScore }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
    val annotatedReady = mediaAssetExists(session?.annotatedVideoUri)
    val rawReady = mediaAssetExists(session?.rawVideoUri)
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf("") }

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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Session ID: $sessionId")
                    Text("Overall score: ${session?.overallScore ?: avgScore}")
                    Text("Average sampled score: $avgScore")
                    Text("Top wins: ${session?.wins ?: "No wins captured yet"}", maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("Top issues: ${session?.issues ?: "No issues captured"}", maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("Top improvement focus: ${session?.topImprovementFocus ?: "-"}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.weight(1f).height(100.dp)) {
                    Text(
                        "Best frame\nT+${formatElapsed(session?.startedAtMs, session?.bestFrameTimestampMs)}",
                        Modifier.padding(8.dp),
                    )
                }
                Card(modifier = Modifier.weight(1f).height(100.dp)) {
                    Text(
                        "Worst frame\nT+${formatElapsed(session?.startedAtMs, session?.worstFrameTimestampMs)}",
                        Modifier.padding(8.dp),
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Issue timeline")
                    if (issueTimeline.isEmpty()) {
                        Text("No issue events captured for this session")
                    } else {
                        issueTimeline.forEach {
                            Text(
                                "${formatElapsed(session?.startedAtMs, it.timestampMs)} ${it.issue} (sev ${it.severity})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                onClick = { openVideo(context, session?.annotatedVideoUri) },
                enabled = annotatedReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Replay annotated video")
            }
            Button(
                onClick = { openVideo(context, session?.rawVideoUri) },
                enabled = rawReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Replay raw video")
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
                onClick = { shareSummary(context, sessionId, session?.overallScore ?: avgScore, session?.issues.orEmpty(), notes) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share summary") }
            Button(
                onClick = {
                    scope.launch {
                        repository.deleteSession(sessionId)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete this session and videos")
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
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


private fun shareSummary(context: android.content.Context, sessionId: Long, score: Int, issues: String, notes: String) {
    val summary = buildString {
        appendLine("Inversion Coach session #$sessionId")
        appendLine("Overall score: $score")
        appendLine("Top issues: ${issues.ifBlank { "No issues captured" }}")
        if (notes.isNotBlank()) {
            appendLine("Notes: $notes")
        }
    }.trim()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, summary)
    }
    context.startActivity(Intent.createChooser(intent, "Share session summary"))
}
