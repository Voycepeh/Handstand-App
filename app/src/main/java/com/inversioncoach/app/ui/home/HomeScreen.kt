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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.calibration.ActiveProfileContext
import com.inversioncoach.app.model.UserProfileRecord
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    val userProfileManager = remember { ServiceLocator.userProfileManager(context) }
    val scope = rememberCoroutineScope()

    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val profiles by userProfileManager.observeAvailableProfiles().collectAsState(initial = emptyList())
    var activeProfileContext by remember { mutableStateOf<ActiveProfileContext?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var renameTargetProfileId by remember { mutableStateOf<String?>(null) }
    var renameTargetName by remember { mutableStateOf("") }

    LaunchedEffect(profiles) {
        activeProfileContext = runCatching { userProfileManager.resolveActiveProfileContext() }.getOrNull()
    }

    val latestSession = sessions.maxByOrNull { it.startedAtMs }
    val activeProfile = activeProfileContext?.userProfile
    val activeProfileName = activeProfile?.displayName ?: "No active profile"
    val activeHasCalibration = activeProfileContext?.bodyProfileRecord != null
    val activeBodyProfileVersion = activeProfileContext?.bodyProfileRecord?.version

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
            profiles = profiles,
            activeProfileId = activeProfile?.id,
            activeProfileName = activeProfileName,
            activeHasCalibration = activeHasCalibration,
            activeBodyProfileVersion = activeBodyProfileVersion,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onSelectProfile = { profileId ->
                scope.launch {
                    userProfileManager.setActiveProfile(profileId)
                    activeProfileContext = userProfileManager.resolveActiveProfileContext()
                }
            },
            onCreateProfile = { profileName ->
                scope.launch {
                    val created = userProfileManager.createProfile(profileName)
                    userProfileManager.setActiveProfile(created.id)
                    activeProfileContext = userProfileManager.resolveActiveProfileContext()
                }
            },
            onRenameProfile = { profileId, newName ->
                scope.launch {
                    userProfileManager.renameProfile(profileId, newName)
                    activeProfileContext = userProfileManager.resolveActiveProfileContext()
                }
            },
            onArchiveProfile = { profileId ->
                scope.launch {
                    userProfileManager.archiveProfile(profileId)
                    activeProfileContext = userProfileManager.resolveActiveProfileContext()
                }
            },
            showOverwriteDialog = showOverwriteDialog,
            onShowOverwriteDialogChange = { showOverwriteDialog = it },
            showCreateDialog = showCreateDialog,
            onShowCreateDialogChange = { showCreateDialog = it },
            newProfileName = newProfileName,
            onNewProfileNameChange = { newProfileName = it },
            renameTargetProfileId = renameTargetProfileId,
            onRenameTargetProfileIdChange = { renameTargetProfileId = it },
            renameTargetName = renameTargetName,
            onRenameTargetNameChange = { renameTargetName = it },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    profiles: List<UserProfileRecord>,
    activeProfileId: String?,
    activeProfileName: String,
    activeHasCalibration: Boolean,
    activeBodyProfileVersion: Int?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (String) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onArchiveProfile: (String) -> Unit,
    showOverwriteDialog: Boolean,
    onShowOverwriteDialogChange: (Boolean) -> Unit,
    showCreateDialog: Boolean,
    onShowCreateDialogChange: (Boolean) -> Unit,
    newProfileName: String,
    onNewProfileNameChange: (String) -> Unit,
    renameTargetProfileId: String?,
    onRenameTargetProfileIdChange: (String?) -> Unit,
    renameTargetName: String,
    onRenameTargetNameChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Train smarter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Start live posture tracking instantly, or launch a drill-based coached session.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Active profile: $activeProfileName", fontWeight = FontWeight.SemiBold)
                Text(if (activeHasCalibration) "Body profile: Calibrated (v${activeBodyProfileVersion ?: 1})" else "Body profile: Default model (no calibration yet)")
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
                    OutlinedTextField(
                        value = activeProfileName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Switch profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.displayName) },
                                onClick = {
                                    onExpandedChange(false)
                                    onSelectProfile(profile.id)
                                },
                            )
                        }
                    }
                }
                Button(onClick = { onShowCreateDialogChange(true) }, modifier = Modifier.fillMaxWidth()) { Text("Create profile") }
                profiles.forEach { profile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (profile.id == activeProfileId) "• ${profile.displayName} (Active)" else "• ${profile.displayName}",
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = {
                            onRenameTargetProfileIdChange(profile.id)
                            onRenameTargetNameChange(profile.displayName)
                        }) { Text("Rename") }
                        Button(onClick = { onArchiveProfile(profile.id) }, enabled = profiles.size > 1 && profile.id != activeProfileId) { Text("Archive") }
                    }
                }
            }
        }

        ActionTile("Start Live Coaching", "Generic posture tracking", { Icon(Icons.Default.FitnessCenter, contentDescription = null) }, onStartFreestyle, featured = true, hero = true)
        ActionTile("Choose Drill", "Guided drill-specific coaching", { Icon(Icons.Default.PlayArrow, contentDescription = null) }, onStart)
        ActionTile("Upload Video", "Analyze a recorded video with pose overlay", { Icon(Icons.Default.VideoLibrary, contentDescription = null) }, onUploadVideo)
        ActionTile("Reference Training", "Compare attempts against reference templates", { Icon(Icons.Default.Timeline, contentDescription = null) }, onReferenceTraining)
        ActionTile("Manage Drills", "Create and edit custom drills", { Icon(Icons.Default.EditNote, contentDescription = null) }, onManageDrills)

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

        ProfileCalibrationCard(
            activeProfileName = activeProfileName,
            isCalibrated = activeHasCalibration,
            hasActiveProfile = activeProfileId != null,
            onCalibrateClick = {
                if (activeProfileId == null) return@ProfileCalibrationCard
                if (activeHasCalibration) onShowOverwriteDialogChange(true) else onCalibration()
            },
        )

        ActionTile("Settings", "Preferences & privacy", { Icon(Icons.Default.Settings, contentDescription = null) }, onSettings)
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { onShowCreateDialogChange(false) },
            title = { Text("Create profile") },
            text = { OutlinedTextField(value = newProfileName, onValueChange = onNewProfileNameChange, label = { Text("Profile name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    onCreateProfile(newProfileName.trim().ifBlank { "User" })
                    onNewProfileNameChange("")
                    onShowCreateDialogChange(false)
                }) { Text("Create") }
            },
            dismissButton = { Button(onClick = { onShowCreateDialogChange(false) }) { Text("Cancel") } },
        )
    }

    if (renameTargetProfileId != null) {
        AlertDialog(
            onDismissRequest = { onRenameTargetProfileIdChange(null) },
            title = { Text("Rename profile") },
            text = { OutlinedTextField(value = renameTargetName, onValueChange = onRenameTargetNameChange, label = { Text("Profile name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    val id = renameTargetProfileId ?: return@Button
                    onRenameProfile(id, renameTargetName)
                    onRenameTargetProfileIdChange(null)
                }) { Text("Save") }
            },
            dismissButton = { Button(onClick = { onRenameTargetProfileIdChange(null) }) { Text("Cancel") } },
        )
    }

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { onShowOverwriteDialogChange(false) },
            title = { Text("Overwrite calibration?") },
            text = { Text("This will replace the saved body calibration for $activeProfileName.") },
            confirmButton = { Button(onClick = { onShowOverwriteDialogChange(false); onCalibration() }) { Text("Overwrite") } },
            dismissButton = { Button(onClick = { onShowOverwriteDialogChange(false) }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileCalibrationCard(
    activeProfileName: String,
    isCalibrated: Boolean,
    hasActiveProfile: Boolean,
    onCalibrateClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Active: $activeProfileName · ${if (isCalibrated) "Calibrated" else "Not calibrated"}")
            Button(onClick = onCalibrateClick, modifier = Modifier.fillMaxWidth(), enabled = hasActiveProfile) {
                Text(if (isCalibrated) "Recalibrate" else "Start calibration")
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
