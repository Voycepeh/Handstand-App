package com.inversioncoach.app.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun CalibrationCompleteScreen(
    modifier: Modifier = Modifier,
    profileSummary: String?,
    savedAtMs: Long?,
    onDone: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Calibration complete", style = MaterialTheme.typography.headlineSmall)
        Text("Your body profile has been saved.")
        profileSummary?.let { Text("Profile snapshot: $it") }
        savedAtMs?.let {
            Text("Saved: ${DateFormat.getDateTimeInstance().format(Date(it))}")
        }
        Text("Readiness: ✅ Body profile available for drill analysis.")
        Text("You can recalibrate anytime if tracking looks off.")
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}
