package com.inversioncoach.app.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CalibrationIntroScreen(modifier: Modifier = Modifier, onStart: () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Structural Calibration", style = MaterialTheme.typography.headlineSmall)
        IntroCard("Use the back camera and keep the phone steady.")
        IntroCard("Place phone 2.5–4 m away so your full body stays visible.")
        IntroCard("Follow the on-screen poses in order: front, side, overhead, then controlled hold.")
        IntroCard("If a step says not ready, adjust position until all highlighted joints are visible.")
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start calibration")
        }
    }
}

@Composable
private fun IntroCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text = text, modifier = Modifier.padding(12.dp))
    }
}
