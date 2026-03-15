package com.inversioncoach.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    onProgress: () -> Unit,
    onSettings: () -> Unit,
) {
    ScaffoldedScreen(title = "Inversion Coach") { padding ->
        Content(padding, onStart, onHistory, onProgress, onSettings)
    }
}

@Composable
private fun Content(
    padding: PaddingValues,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Train smarter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "Quickly start a drill, review sessions, and track trends.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ActionTile(
            label = "Choose Drill",
            subtitle = "Start a guided session",
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            onClick = onStart,
            featured = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(
                label = "Review",
                subtitle = "Session history",
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                onClick = onHistory,
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                label = "Progress",
                subtitle = "Patterns & trends",
                icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                onClick = onProgress,
                modifier = Modifier.weight(1f),
            )
        }

        ActionTile(
            label = "Settings",
            subtitle = "Preferences & privacy",
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = onSettings,
        )
    }
}

@Composable
private fun ActionTile(
    label: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
) {
    val colors = if (featured) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = colors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 0.dp,
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        icon()
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(imageVector = Icons.Default.ArrowOutward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
