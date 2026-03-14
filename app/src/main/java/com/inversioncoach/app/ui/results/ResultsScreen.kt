package com.inversioncoach.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(sessionId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val session by repository.observeSession(sessionId).collectAsState(initial = null)
    val frameMetrics by repository.observeSessionFrameMetrics(sessionId).collectAsState(initial = emptyList())
    val issueTimeline by repository.observeIssueTimeline(sessionId).collectAsState(initial = emptyList())
    val avgScore = frameMetrics.map { it.overallScore }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0

    ScaffoldedScreen(title = "Results") { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Overall score: ${session?.overallScore ?: avgScore}")
                    Text("Average sampled score: $avgScore")
                    Text("Top wins: ${session?.wins ?: "No wins captured yet"}")
                    Text("Top issues: ${session?.issues ?: "No issues captured"}")
                    Text("Top improvement focus: ${session?.topImprovementFocus ?: "-"}")
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
                            Text("${formatElapsed(session?.startedAtMs, it.timestampMs)} ${it.issue} (sev ${it.severity})")
                        }
                    }
                }
            }

            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Replay annotated video") }
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Replay raw video") }
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Save note") }
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Share summary") }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

private fun formatElapsed(startedAtMs: Long?, timestampMs: Long?): String {
    if (startedAtMs == null || timestampMs == null || timestampMs < startedAtMs) return "--:--"
    val elapsedSeconds = ((timestampMs - startedAtMs) / 1000).toInt()
    return "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
}
