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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.storage.repository.UserProfileStatus
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.components.ScaffoldedScreen

@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onStartFreestyle: () -> Unit,
    onDrillStudio: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
    onUploadVideo: () -> Unit,
    onCalibration: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val profileStatuses by repository.observeProfileStatuses().collectAsState(initial = emptyList())
    val latestSession = sessions.maxByOrNull { it.startedAtMs }

    ScaffoldedScreen(title = "Inversion Coach") { padding ->
        Content(
            padding = padding,
            onStart = onStart,
            onStartFreestyle = onStartFreestyle,
            onDrillStudio = onDrillStudio,
            onHistory = onHistory,
            onProgress = onProgress,
            onSettings = onSettings,
            onUploadVideo = onUploadVideo,
            onCalibration = onCalibration,
            profileStatuses = profileStatuses,
            latestSessionStartMs = latestSession?.startedAtMs ?: 0L,
            latestSessionDurationMs = computeSessionDurationMs(latestSession?.startedAtMs ?: 0L, latestSession?.completedAtMs ?: 0L),
        )
    }
}

@Composable
private fun Content(
    padding: PaddingValues,
    onStart: () -> Unit,
    onStartFreestyle: () -> Unit,
    onDrillStudio: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
    onUploadVideo: () -> Unit,
    onCalibration: () -> Unit,
    profileStatuses: List<UserProfileStatus>,
    latestSessionStartMs: Long,
    latestSessionDurationMs: Long,
) {
    val activeProfile = profileStatuses.firstOrNull { it.isActive }
    val activeProfileName = activeProfile?.name ?: "Profile 1"
    val activeProfileCalibrated = activeProfile?.isCalibrated ?: false
    var showOverwriteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Train smarter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "Start live posture tracking instantly, or launch a drill-based coached session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ActionTile(
            label = "Start Live Coaching",
            subtitle = "Generic posture tracking",
            icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
            onClick = onStartFreestyle,
            featured = true,
            hero = true,
        )

        ActionTile(
            label = "Choose Drill",
            subtitle = "Guided drill-specific coaching",
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            onClick = onStart,
        )

        ActionTile(
            label = "Drill Studio",
            subtitle = "Preview authored drill catalog templates",
            icon = { Icon(Icons.Default.Animation, contentDescription = null) },
            onClick = onDrillStudio,
        )

        ActionTile(
            label = "Upload Video",
            subtitle = "Analyze a recorded video with pose overlay",
            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
            onClick = onUploadVideo,
        )

        ActionTile(
            label = "Latest Session",
            subtitle = "${formatSessionDateTime(latestSessionStartMs)} • ${formatSessionDuration(latestSessionDurationMs)}",
            icon = { Icon(Icons.Default.History, contentDescription = null) },
            onClick = onHistory,
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

        ProfileCalibrationCard(
            activeProfileName = activeProfileName,
            isCalibrated = activeProfileCalibrated,
            profileStatuses = profileStatuses,
            onCalibrateClick = {
                if (activeProfileCalibrated) {
                    showOverwriteDialog = true
                } else {
                    onCalibration()
                }
            },
        )

        ActionTile(
            label = "Settings",
            subtitle = "Preferences & privacy",
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = onSettings,
        )
    }

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("Overwrite calibration?") },
            text = { Text("This will replace the saved body calibration for $activeProfileName.") },
            confirmButton = {
                Button(onClick = {
                    showOverwriteDialog = false
                    onCalibration()
                }) { Text("Overwrite") }
            },
            dismissButton = {
                Button(onClick = { showOverwriteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileCalibrationCard(
    activeProfileName: String,
    isCalibrated: Boolean,
    profileStatuses: List<UserProfileStatus>,
    onCalibrateClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Active profile: $activeProfileName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Body profile: ${if (isCalibrated) "Calibrated" else "Not calibrated"}")
            Button(onClick = onCalibrateClick, modifier = Modifier.fillMaxWidth()) {
                Text(if (isCalibrated) "Recalibrate" else "Start calibration")
            }
            profileStatuses.forEach { profile ->
                val suffix = if (profile.isActive) " (Active)" else ""
                Text("${profile.name}$suffix · ${if (profile.isCalibrated) "Calibrated" else "Not calibrated"}")
            }
        }
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
    hero: Boolean = false,
) {
    val colors = if (featured) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (hero) 28.dp else 24.dp),
        colors = colors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (hero) 20.dp else 16.dp, vertical = if (hero) 24.dp else 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(modifier = Modifier.padding(if (hero) 10.dp else 8.dp)) {
                        icon()
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        style = if (hero) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = if (hero) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.ArrowOutward,
                contentDescription = null,
                modifier = Modifier.size(if (hero) 22.dp else 18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
