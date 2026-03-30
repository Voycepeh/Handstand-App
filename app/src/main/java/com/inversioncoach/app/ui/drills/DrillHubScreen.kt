package com.inversioncoach.app.ui.drills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun DrillHubScreen(
    onBack: () -> Unit,
    onChooseDrill: () -> Unit,
    onManageDrills: () -> Unit,
    onReferenceTraining: () -> Unit,
) {
    ScaffoldedScreen(title = "Drills", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Choose a drill to train, manage custom drills, or work on reference templates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DrillHubActionCard(
                title = "Choose Drill",
                description = "Pick a drill and start a live coaching session.",
                onClick = onChooseDrill,
            )
            DrillHubActionCard(
                title = "Manage Drills",
                description = "Create, edit, and organize your drill catalog.",
                onClick = onManageDrills,
            )
            DrillHubActionCard(
                title = "Reference Training",
                description = "Upload reference videos, compare attempts, promote sessions, and refine in Drill Studio.",
                onClick = onReferenceTraining,
            )
        }
    }
}

@Composable
private fun DrillHubActionCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick) {
                Text("Open")
            }
        }
    }
}
