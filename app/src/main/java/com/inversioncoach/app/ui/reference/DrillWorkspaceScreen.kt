package com.inversioncoach.app.ui.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.DrillDefinitionResolver
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import com.inversioncoach.app.ui.history.DrillSessionsSection
import com.inversioncoach.app.ui.history.HistorySort


internal object DrillWorkspacePrimaryActions {
    val primary = listOf("Start Live Coaching", "Upload Attempt", "Manage This Drill")
    val hiddenLegacy = listOf(
        "Upload New Reference",
        "Use Past Session as Reference",
        "Reference Template",
        "Edit Drill",
        "New Drill",
    )
}

@Composable
fun DrillWorkspaceScreen(
    drillId: String,
    onBack: () -> Unit,
    onUploadAttempt: (String) -> Unit,
    onCompareAttempts: (String) -> Unit,
    onOpenSession: (Long) -> Unit,
    onStartLiveSession: (DrillType) -> Unit,
    onManageDrill: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val drills by repo.getAllDrills().collectAsState(initial = emptyList())
    val allSessions by repo.observeSessions().collectAsState(initial = emptyList())
    val comparedSessionIds by repo.observeComparedSessionIds().collectAsState(initial = emptyList())
    val latestComparisonScores by repo.observeLatestComparisonScores().collectAsState(initial = emptyMap())
    val settings by repo.observeSettings().collectAsState(initial = com.inversioncoach.app.model.UserSettings())
    val sessionSizes = remember { mutableStateMapOf<Long, Long>() }
    var selectedSort by remember { mutableStateOf(HistorySort.RECENCY) }
    var sortAscending by remember { mutableStateOf(false) }
    var totalStorageBytes by remember { mutableLongStateOf(0L) }
    val filteredSessions = remember(allSessions, drillId) {
        allSessions.filter { session ->
            val sessionDrillId = session.drillId ?: session.metricsJson.split('|')
                .firstOrNull { it.startsWith("drillId:") }
                ?.substringAfter(':')
                ?.takeIf { it.isNotBlank() }
            sessionDrillId == drillId
        }
    }
    val sortedSessions = remember(filteredSessions, selectedSort, sortAscending, sessionSizes.toMap()) {
        val sorted = when (selectedSort) {
            HistorySort.RECENCY -> filteredSessions.sortedBy { it.startedAtMs }
            HistorySort.STORAGE_SIZE -> filteredSessions.sortedBy { sessionSizes[it.id] ?: 0L }
            HistorySort.SESSION_DURATION -> filteredSessions.sortedBy {
                com.inversioncoach.app.ui.common.computeSessionDurationMs(it.startedAtMs, it.completedAtMs)
            }
        }
        if (sortAscending) sorted else sorted.reversed()
    }

    LaunchedEffect(filteredSessions) {
        sessionSizes.clear()
        filteredSessions.forEach { session ->
            sessionSizes[session.id] = repo.sessionStorageBytes(session.id)
        }
        totalStorageBytes = repo.totalStorageBytes()
    }

    val selectedDrill = drills.firstOrNull { it.id == drillId }
    val isReady = selectedDrill?.status == DrillStatus.READY

    fun onSortSelected(sort: HistorySort) {
        if (selectedSort == sort) {
            sortAscending = !sortAscending
        } else {
            selectedSort = sort
            sortAscending = false
        }
    }

    ScaffoldedScreen(title = "Drill Workspace", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(selectedDrill?.name ?: "Drill", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = selectedDrill?.description.orEmpty().ifBlank { "Practice this drill, upload attempts, and review session history." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isReady) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "This drill is not READY yet. Live coaching and upload actions are currently disabled.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = {
                    val drillType = selectedDrill?.let(DrillDefinitionResolver::resolveLegacyDrillType) ?: DrillType.FREESTYLE
                    onStartLiveSession(drillType)
                },
                enabled = isReady,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(DrillWorkspacePrimaryActions.primary[0]) }

            Button(
                onClick = { onUploadAttempt(drillId) },
                enabled = isReady,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(DrillWorkspacePrimaryActions.primary[1]) }

            Button(
                onClick = { onManageDrill(drillId) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(DrillWorkspacePrimaryActions.primary[2]) }

            DrillSessionsSection(
                modifier = Modifier.weight(1f),
                drillIdFilter = drillId,
                comparisonMode = false,
                drills = drills,
                sortedSessions = sortedSessions,
                selectedSort = selectedSort,
                sortAscending = sortAscending,
                sessionSizes = sessionSizes,
                totalStorageBytes = totalStorageBytes,
                maxStorageBytes = settings.maxStorageMb.toLong() * 1024L * 1024L,
                comparedSessionIds = comparedSessionIds,
                latestComparisonScores = latestComparisonScores,
                onSortSelected = ::onSortSelected,
                onOpenSession = onOpenSession,
                onOpenComparisonTools = { onCompareAttempts(drillId) },
            )
        }
    }
}
