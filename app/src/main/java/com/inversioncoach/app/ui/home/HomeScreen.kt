package com.inversioncoach.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    ScaffoldedScreen(title = "Inversion Coach") { padding ->
        Content(padding, onStart, onHistory, onSettings)
    }
}

@Composable
private fun Content(padding: PaddingValues, onStart: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Last session", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Chest-to-wall handstand",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Recent average score: 76")
                Text("Most common issue: ribs flaring")
            }
        }

        ActionButton(
            label = "Choose Drill",
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            onClick = onStart,
        )
        ActionButton(
            label = "Review Sessions",
            icon = { Icon(Icons.Default.History, contentDescription = null) },
            onClick = onHistory,
        )
        ActionButton(
            label = "Progress",
            icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
            onClick = onHistory,
        )
        ActionButton(
            label = "Settings",
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = onSettings,
        )
    }
}

@Composable
private fun ActionButton(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        icon()
        Text(text = "  $label")
    }
}
