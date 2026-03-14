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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun ResultsScreen(onDone: () -> Unit) {
    ScaffoldedScreen(title = "Results") { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Overall score: 78")
                    Text("Top wins: active shoulders, straighter legs, improved stack")
                    Text("Top issues: rib flare under fatigue, hip drift, soft lockout")
                    Text("Top improvement focus: shoulder elevation with ribs in")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.weight(1f).height(100.dp)) { Text("Best frame\nT+00:18", Modifier.padding(8.dp)) }
                Card(modifier = Modifier.weight(1f).height(100.dp)) { Text("Worst frame\nT+00:42", Modifier.padding(8.dp)) }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Issue timeline")
                    Text("00:14 passive shoulders")
                    Text("00:25 ribs flaring")
                    Text("00:41 hips off stack")
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
