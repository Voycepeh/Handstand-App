package com.inversioncoach.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.menuAnchor
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.storage.repository.UserProfileStatus
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onStartFreestyle: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
    onUploadVideo: () -> Unit,
    onCalibration: () -> Unit,
    onReferenceTraining: () -> Unit = {},
    onManageDrills: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val scope = rememberCoroutineScope()

    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val profileStatuses by repository.observeProfileStatuses().collectAsState(initial = emptyList())

    val latestSession = sessions.maxByOrNull { it.startedAtMs }

    ScaffoldedScreen(title = "Inversion Coach") { padding ->
        Content(
            padding = padding,
            onStart = onStart,
            onStartFreestyle = onStartFreestyle,
            onHistory = onHistory,
            onProgress = onProgress,
            onSettings = onSettings,
            onUploadVideo = onUploadVideo,
            onCalibration = onCalibration,
            onReferenceTraining = onReferenceTraining,
            onManageDrills = onManageDrills,
            latestSessionStartMs = latestSession?.startedAtMs ?: 0L,
            latestSessionDurationMs = computeSessionDurationMs(latestSession?.startedAtMs ?: 0L, latestSession?.completedAtMs ?: 0L),
            profileStatuses = profileStatuses,
            onSelectProfile = { profileId ->
                scope.launch { repository.setActiveProfile(profileId) }
            },
            onCreateProfile = { profileName ->
                scope.launch {
                    val createdId = repository.createProfile(profileName) ?: return@launch
                    repository.setActiveProfile(createdId)
                }
            },
            onRenameProfile = { profileId, newName ->
                scope.launch { repository.renameProfile(profileId, newName) }
            },
            onArchiveProfile = { profileId ->
                scope.launch { repository.archiveProfile(profileId) }
            },
        )
    }
}

@Composable
private fun Content(
    padding: PaddingValues,
    onStart: () -> Unit,
    onStartFreestyle: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
    onUploadVideo: () -> Unit,
    onCalibration: () -> Unit,
    onReferenceTraining: () -> Unit,
    onManageDrills: () -> Unit,
    latestSessionStartMs: Long,
    latestSessionDurationMs: Long,
    profileStatuses: List<UserProfileStatus>,
    onSelectProfile: (Long) -> Unit,
    onCreateProfile: (String) -> Unit,
    onRenameProfile: (Long, String) -> Unit,
    onArchiveProfile: (Long) -> Unit,
) {
    val orderedProfiles = remember(profileStatuses) {
        profileStatuses.sortedWith(
            compareByDescending<UserProfileStatus> { it.isActive }
                .thenBy { it.name.lowercase() },
        )
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    var renameTarget by remember { mutableStateOf<UserProfileStatus?>(null) }
    var renameTargetName by remember { mutableStateOf("") }

    var archiveTarget by remember { mutableStateOf<UserProfileStatus?>(null) }
    var switchThenCalibrateTarget by remember { mutableStateOf<UserProfileStatus?>(null) }
    var showOverwriteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Train smarter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        ProfileCalibrationCard(
            profiles = orderedProfiles,
            onSelectProfile = onSelectProfile,
            onCalibrateProfile = { profile ->
                if (!profile.isActive) {
                    switchThenCalibrateTarget = profile
                    return@ProfileCalibrationCard
                }
                if (profile.isCalibrated) showOverwriteDialog = true else onCalibration()
            },
            onCreateProfile = { showCreateDialog = true },
            onRenameProfile = { profile ->
                renameTarget = profile
                renameTargetName = profile.name
            },
            onArchiveProfile = { profile ->
                archiveTarget = profile
            },
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
            label = "Upload Video",
            subtitle = "Analyze a recorded video with pose overlay",
            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
            onClick = onUploadVideo,
        )
        ActionTile(
            label = "Latest Session",
            subtitle = if (latestSessionStartMs > 0L) "${formatSessionDateTime(latestSessionStartMs)} • ${formatSessionDuration(latestSessionDurationMs)}" else "Open history to review saved sessions",
            icon = { Icon(Icons.Default.History, contentDescription = null) },
            onClick = onHistory,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Review", "Session history", { Icon(Icons.Default.History, contentDescription = null) }, onHistory, modifier = Modifier.weight(1f))
            ActionTile("Progress", "Patterns & trends", { Icon(Icons.Default.BarChart, contentDescription = null) }, onProgress, modifier = Modifier.weight(1f))
        }

        ActionTile(
            label = "Settings",
            subtitle = "Preferences & privacy",
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = onSettings,
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create profile") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    onCreateProfile(newProfileName.trim().ifBlank { "User" })
                    newProfileName = ""
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }

    renameTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = renameTargetName,
                    onValueChange = { renameTargetName = it },
                    label = { Text("Profile name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameProfile(profile.id, renameTargetName)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    archiveTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("Archive profile?") },
            text = {
                Text("This will hide the profile from active use. Sessions and calibration data remain saved.")
            },
            confirmButton = {
                Button(onClick = {
                    onArchiveProfile(profile.id)
                    archiveTarget = null
                }) { Text("Archive") }
            },
            dismissButton = {
                OutlinedButton(onClick = { archiveTarget = null }) { Text("Cancel") }
            },
        )
    }

    switchThenCalibrateTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { switchThenCalibrateTarget = null },
            title = { Text("Switch profile and calibrate?") },
            text = { Text("Set ${profile.name} as active profile, then start calibration.") },
            confirmButton = {
                Button(onClick = {
                    onSelectProfile(profile.id)
                    switchThenCalibrateTarget = null
                    if (profile.isCalibrated) showOverwriteDialog = true else onCalibration()
                }) { Text("Continue") }
            },
            dismissButton = {
                OutlinedButton(onClick = { switchThenCalibrateTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("Overwrite calibration?") },
            text = { Text("This will replace the saved body calibration for the active profile.") },
            confirmButton = {
                Button(onClick = {
                    showOverwriteDialog = false
                    onCalibration()
                }) { Text("Overwrite") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOverwriteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileCalibrationCard(
    profiles: List<UserProfileStatus>,
    onSelectProfile: (Long) -> Unit,
    onCalibrateProfile: (UserProfileStatus) -> Unit,
    onCreateProfile: () -> Unit,
    onRenameProfile: (UserProfileStatus) -> Unit,
    onArchiveProfile: (UserProfileStatus) -> Unit,
) {
    val activeCount = profiles.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            profiles.forEach { profile ->
                val rowHighlight = if (profile.isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = rowHighlight),
                    onClick = { onSelectProfile(profile.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(if (profile.isActive) "●" else "○", fontWeight = FontWeight.Bold)
                        Text(profile.name, modifier = Modifier.weight(1f))
                        Spacer(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (profile.isCalibrated) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color.Gray,
                                    CircleShape,
                                ),
                        )
                        OutlinedButton(onClick = { onCalibrateProfile(profile) }) { Text("Calibrate") }
                        OutlinedButton(onClick = { onRenameProfile(profile) }) { Text("Rename") }
                        OutlinedButton(
                            onClick = { onArchiveProfile(profile) },
                            enabled = activeCount > 1 && !profile.isActive,
                        ) { Text("Archive") }
                    }
                }
            }

            OutlinedButton(onClick = onCreateProfile, modifier = Modifier.fillMaxWidth()) {
                Text("+ Create profile")
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
    val colors = if (featured) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
    else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), contentColor = MaterialTheme.colorScheme.onSurface)

    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(if (hero) 28.dp else 24.dp), colors = colors) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = if (hero) 20.dp else 16.dp, vertical = if (hero) 24.dp else 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp), tonalElevation = 0.dp, shadowElevation = 0.dp) {
                    Row(modifier = Modifier.padding(if (hero) 10.dp else 8.dp)) { icon() }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = if (hero) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = if (hero) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(imageVector = Icons.Default.ArrowOutward, contentDescription = null, modifier = Modifier.size(if (hero) 22.dp else 18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
