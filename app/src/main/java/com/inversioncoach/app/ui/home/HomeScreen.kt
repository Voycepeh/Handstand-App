package com.inversioncoach.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Last session: Chest-to-wall handstand")
                Text("Recent average score: 76")
                Text("Most common issue: ribs flaring")
            }
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start Drill") }
        Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Review Sessions") }
        Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Progress") }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Drill Library") }
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
    }
}
