package com.inversioncoach.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.AppSettingsPolicy
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.live.ExportWorkOwnership
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch


@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onStartFreestyle: () -> Unit,
    onHistory: () -> Unit,
    onDrills: () -> Unit,
    onSettings: () -> Unit,
    onUploadVideo: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val scope = rememberCoroutineScope()

    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val settings by repository.observeSettings().collectAsState<UserSettings, UserSettings?>(initial = null)
    var onboardingGate by remember { mutableStateOf(FirstLaunchOnboardingGate.Loading) }

    LaunchedEffect(settings?.hasCompletedPreferencesOnboarding) {
        onboardingGate = when {
            settings == null -> FirstLaunchOnboardingGate.Loading
            settings?.hasCompletedPreferencesOnboarding == true -> FirstLaunchOnboardingGate.SkipOnboarding
            else -> FirstLaunchOnboardingGate.ShowOnboarding
        }
    }

    LaunchedEffect(sessions) {
        repository.recoverStaleAnnotatedExports(
            activeExportSessionIds = ExportWorkOwnership.activeSessionIds(),
            trigger = "home_hydration",
        )
    }

    ScaffoldedScreen(title = "CaliVision") { padding ->
        Content(
            padding = padding,
            onStart = onStart,
            onStartFreestyle = onStartFreestyle,
            onHistory = onHistory,
            onDrills = onDrills,
            onSettings = onSettings,
            onUploadVideo = onUploadVideo,
            sessionSummaries = sessions,
        )

        if (onboardingGate == FirstLaunchOnboardingGate.ShowOnboarding) {
            FirstLaunchWelcomeDialog(
                onUseRecommendedSettings = {
                    val currentSettings = settings ?: return@FirstLaunchWelcomeDialog
                    scope.launch {
                        repository.saveSettings(AppSettingsPolicy.applyRecommendedRecordingDefaults(currentSettings))
                        onboardingGate = FirstLaunchOnboardingGate.SkipOnboarding
                    }
                },
                onOpenRecordingSettings = {
                    val currentSettings = settings ?: return@FirstLaunchWelcomeDialog
                    scope.launch {
                        repository.saveSettings(
                            currentSettings.copy(
                                hasCompletedPreferencesOnboarding = true,
                            ),
                        )
                        onboardingGate = FirstLaunchOnboardingGate.SkipOnboarding
                        onSettings()
                    }
                },
            )
        }
    }
}

@Composable
private fun FirstLaunchWelcomeDialog(
    onUseRecommendedSettings: () -> Unit,
    onOpenRecordingSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to CaliVision") },
        text = {
            Text("Choose your recording setup. You can use recommended settings now or change everything in Settings.")
        },
        confirmButton = {
            Button(onClick = onUseRecommendedSettings) {
                Text("Use recommended settings")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onOpenRecordingSettings) {
                Text("Open recording settings")
            }
        },
    )
}

private enum class FirstLaunchOnboardingGate {
    Loading,
    ShowOnboarding,
    SkipOnboarding,
}

@Composable
private fun Content(
    padding: PaddingValues,
    onStart: () -> Unit,
    onStartFreestyle: () -> Unit,
    onHistory: () -> Unit,
    onDrills: () -> Unit,
    onSettings: () -> Unit,
    onUploadVideo: () -> Unit,
    sessionSummaries: List<SessionRecord>,
) {
    val historySummary = remember(sessionSummaries) { sessionSummaries.toHistorySummary() }

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Train smarter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

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
            subtitle = "Pick a drill and start training",
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            onClick = onStart,
        )
        ActionTile(
            label = "Upload Video",
            subtitle = "Analyze a recorded video with pose overlay",
            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
            onClick = onUploadVideo,
        )
        HistorySummaryCard(summary = historySummary, onClick = onHistory, label = "History")
        ActionTile("Drills", "Browse drills and open workspace", { Icon(Icons.Default.SportsMartialArts, contentDescription = null) }, onDrills)

        ActionTile(
            label = "Settings",
            subtitle = "Preferences & privacy",
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = onSettings,
        )
    }

}

private data class HistorySummary(
    val sessionsThisWeek: Int,
    val totalPracticeTimeMsThisWeek: Long,
    val mostUsedDrillLabel: String?,
    val lastActivityAtMs: Long?,
)

private fun List<SessionRecord>.toHistorySummary(nowMs: Long = System.currentTimeMillis()): HistorySummary {
    if (isEmpty()) {
        return HistorySummary(0, 0L, null, null)
    }
    val zoneId = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMs).atZone(zoneId)
    val weekStart = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    val thisWeek = filter { it.startedAtMs >= weekStart }
    val totalPracticeTimeMs = thisWeek.sumOf { computeSessionDurationMs(it.startedAtMs, it.completedAtMs) }
    val mostUsedDrill = thisWeek
        .groupingBy { it.drillType.displayName }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    val lastActivity = maxByOrNull { it.startedAtMs }?.startedAtMs

    return HistorySummary(
        sessionsThisWeek = thisWeek.size,
        totalPracticeTimeMsThisWeek = totalPracticeTimeMs,
        mostUsedDrillLabel = mostUsedDrill,
        lastActivityAtMs = lastActivity,
    )
}

@Composable
private fun HistorySummaryCard(
    summary: HistorySummary,
    onClick: () -> Unit,
    label: String = "History",
) {
    val hasActivity = summary.lastActivityAtMs != null
    ActionTile(
        label = label,
        subtitle = if (hasActivity) {
            "Review past sessions and recent training activity."
        } else {
            "No history yet. Start live coaching or upload a video."
        },
        icon = { Icon(Icons.Default.History, contentDescription = null) },
        details = if (hasActivity) {
            buildString {
                append("Last activity: ")
                append(formatSessionDateTime(summary.lastActivityAtMs ?: 0L))
                append(" • This week: ")
                append(summary.sessionsThisWeek)
                append(" sessions")
                append(" • Practice: ")
                append(formatSessionDuration(summary.totalPracticeTimeMsThisWeek))
                if (!summary.mostUsedDrillLabel.isNullOrBlank()) {
                    append(" • Most used: ")
                    append(summary.mostUsedDrillLabel)
                }
            }
        } else {
            null
        },
        onClick = onClick,
    )
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
    details: String? = null,
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
                    if (!details.isNullOrBlank()) {
                        Text(
                            details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(imageVector = Icons.Default.ArrowOutward, contentDescription = null, modifier = Modifier.size(if (hero) 22.dp else 18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
