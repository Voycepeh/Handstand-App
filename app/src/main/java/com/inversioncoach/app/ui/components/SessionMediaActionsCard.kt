package com.inversioncoach.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.media.ResolvedSessionMedia
import com.inversioncoach.app.media.SessionArtifact

@Composable
fun SessionMediaActionsCard(
    media: ResolvedSessionMedia,
    onPlayRaw: (String) -> Unit,
    onSaveRaw: (String) -> Unit,
    onPlayAnnotated: (String) -> Unit,
    onSaveAnnotated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Session media", style = MaterialTheme.typography.titleMedium)

            val raw = media.raw
            if (raw is SessionArtifact.Available) {
                OutlinedButton(onClick = { onPlayRaw(raw.uri) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Play raw")
                }
                OutlinedButton(onClick = { onSaveRaw(raw.uri) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save raw to device")
                }
            }

            when (val annotated = media.annotated) {
                is SessionArtifact.Available -> {
                    OutlinedButton(onClick = { onPlayAnnotated(annotated.uri) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Play annotated")
                    }
                    OutlinedButton(onClick = { onSaveAnnotated(annotated.uri) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save annotated to device")
                    }
                }

                is SessionArtifact.Processing -> {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("Play annotated")
                    }
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("Save annotated to device")
                    }
                    Text(
                        "Annotated export in progress: ${annotated.message}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is SessionArtifact.Unavailable -> Unit
            }

            if (raw !is SessionArtifact.Available && media.annotated !is SessionArtifact.Available && media.annotated !is SessionArtifact.Processing) {
                Text(
                    "No playable media artifacts are available yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
