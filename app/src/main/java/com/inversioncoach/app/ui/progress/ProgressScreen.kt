package com.inversioncoach.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.common.computeSessionDurationMs
import com.inversioncoach.app.ui.common.canOpenResultsRoute
import com.inversioncoach.app.ui.common.formatPrimaryPerformance
import com.inversioncoach.app.ui.common.formatSessionDateTime
import com.inversioncoach.app.ui.common.formatSessionDuration
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class HeatmapCellKey(val dayStartMs: Long, val blockIndex: Int)

private enum class HeatmapZoom(val label: String, val blockHours: Int) {
    Day("Day", 24),
    FourHours("4h", 4),
    Hour("1h", 1),
}

@Composable
fun HomeHistoryScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val settings by repository.observeSettings().collectAsState(initial = UserSettings())

    var selectedDrill: DrillType? by remember { mutableStateOf(null) }
    var expandedCell: HeatmapCellKey? by remember { mutableStateOf(null) }
    var showSummaryCards by remember { mutableStateOf(false) }
    var weekOffset by remember { mutableStateOf(0) }
    var zoom by remember { mutableStateOf(HeatmapZoom.FourHours) }

    val minimumDurationMs = settings.startupCountdownSeconds * 1000L
    val filteredSessions = remember(sessions, selectedDrill, minimumDurationMs) {
        sessions.filter { session ->
            val meetsDuration = computeSessionDurationMs(session.startedAtMs, session.completedAtMs) >= minimumDurationMs
            val matchesDrill = selectedDrill == null || session.drillType == selectedDrill
            meetsDuration && matchesDrill
        }
    }

    val sessionsWithIssues = filteredSessions.count { it.issues.isNotBlank() }
    val cleanSessions = filteredSessions.size - sessionsWithIssues
    val topDrill = filteredSessions.groupingBy { it.drillType }.eachCount().maxByOrNull { it.value }?.key?.displayName ?: "-"
    val avgDurationMs = if (filteredSessions.isEmpty()) 0L else filteredSessions.map { computeSessionDurationMs(it.startedAtMs, it.completedAtMs) }.average().toLong()
    val latestSessionStart = filteredSessions.maxByOrNull { it.startedAtMs }?.startedAtMs ?: 0L

    val weekDays = remember(weekOffset) { buildDaysForWeek(weekOffset) }
    val groupedByBlock = remember(filteredSessions, zoom) { groupSessionsByBlock(filteredSessions, zoom.blockHours) }
    val maxCount = groupedByBlock.values.maxOfOrNull { it.size } ?: 0
    val expandedSessions = expandedCell?.let { groupedByBlock[it] }.orEmpty().sortedByDescending { it.startedAtMs }
    val availableDrills = remember(sessions) { sessions.map { it.drillType }.distinct().sortedBy { it.displayName } }
    val hasNewerWeek = weekOffset < 0
    val weekLabel = remember(weekDays) { formatWeekLabel(weekDays.first(), weekDays.last()) }

    ScaffoldedScreen(title = "History", onBack = onBack) { padding ->
        val contentScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(contentScrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("History at a glance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Activity heatmap counts only sessions >= ${settings.startupCountdownSeconds}s", style = MaterialTheme.typography.bodySmall)

            FilterChips(
                selectedDrill = selectedDrill,
                availableDrills = availableDrills,
                onSelected = {
                    selectedDrill = it
                    expandedCell = null
                },
            )

            Text("Activity heatmap", fontWeight = FontWeight.SemiBold)
            WeekAndZoomControls(
                weekLabel = weekLabel,
                hasNewerWeek = hasNewerWeek,
                zoom = zoom,
                onPreviousWeek = {
                    weekOffset -= 1
                    expandedCell = null
                },
                onNextWeek = {
                    weekOffset += 1
                    expandedCell = null
                },
                onZoomOut = {
                    zoom = zoom.zoomedOut()
                    expandedCell = null
                },
                onZoomIn = {
                    zoom = zoom.zoomedIn()
                    expandedCell = null
                },
            )

            HeatmapTable(
                days = weekDays,
                groupedByBlock = groupedByBlock,
                maxCount = maxCount,
                blockHours = zoom.blockHours,
                expandedCell = expandedCell,
                onCellClick = { clicked -> expandedCell = if (expandedCell == clicked) null else clicked },
            )

            TextButton(onClick = { showSummaryCards = !showSummaryCards }) {
                Text(if (showSummaryCards) "Hide summary metrics" else "Show summary metrics")
            }

            if (showSummaryCards) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ProgressCard("Clean", "$cleanSessions", Modifier.weight(1f))
                        ProgressCard("Sessions", "${filteredSessions.size}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ProgressCard("With issues", "$sessionsWithIssues", Modifier.weight(1f))
                        ProgressCard("Avg duration", formatSessionDuration(avgDurationMs), Modifier.weight(1f))
                    }
                    ProgressCard("Latest", formatSessionDateTime(latestSessionStart), Modifier.fillMaxWidth())
                    ProgressCard("Top drill", topDrill, Modifier.fillMaxWidth())
                }
            }

            if (expandedCell != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sessions in selected block", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (expandedSessions.isEmpty()) {
                            Text("No sessions in this time block.")
                        } else {
                            expandedSessions.forEach { session ->
                                SessionSummaryRow(session = session, onOpenSession = onOpenSession)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selectedDrill: DrillType?, availableDrills: List<DrillType>, onSelected: (DrillType?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { onSelected(null) }) {
            Text(if (selectedDrill == null) "✓ All" else "All")
        }
        availableDrills.forEach { drillType ->
            OutlinedButton(onClick = { onSelected(drillType) }) {
                val label = drillType.displayName
                Text(if (selectedDrill == drillType) "✓ $label" else label)
            }
        }
    }
}

@Composable
private fun HeatmapTable(
    days: List<Long>,
    groupedByBlock: Map<HeatmapCellKey, List<SessionRecord>>,
    maxCount: Int,
    blockHours: Int,
    expandedCell: HeatmapCellKey?,
    onCellClick: (HeatmapCellKey) -> Unit,
) {
    val hourScroll = rememberScrollState()
    val blockCount = 24 / blockHours
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Day", modifier = Modifier.padding(end = 4.dp))
            Row(modifier = Modifier.horizontalScroll(hourScroll), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until blockCount).forEach { blockIndex ->
                    val startHour = blockIndex * blockHours
                    Text(
                        "%02d".format(startHour),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.size(if (blockHours == 1) 18.dp else 22.dp),
                    )
                }
            }
        }
        days.forEach { dayStartMs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDayLabel(dayStartMs), modifier = Modifier.padding(end = 4.dp))
                Row(modifier = Modifier.horizontalScroll(hourScroll), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until blockCount).forEach { blockIndex ->
                        val key = HeatmapCellKey(dayStartMs, blockIndex)
                        val count = groupedByBlock[key]?.size ?: 0
                        val isExpanded = expandedCell == key
                        Box(
                            modifier = Modifier
                                .size(if (blockHours == 1) 18.dp else 22.dp)
                                .background(cellColor(count, maxCount, isExpanded), RoundedCornerShape(3.dp))
                                .clickable { onCellClick(key) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryRow(session: SessionRecord, onOpenSession: (Long) -> Unit) {
    val durationMs = computeSessionDurationMs(session.startedAtMs, session.completedAtMs)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.title, fontWeight = FontWeight.SemiBold)
            Text("Started: ${formatSessionDateTime(session.startedAtMs)}")
            Text("Duration: ${formatSessionDuration(durationMs)}")
            Text(formatPrimaryPerformance(session), style = MaterialTheme.typography.bodySmall)
            val canOpen = session.canOpenResultsRoute()
            OutlinedButton(onClick = { onOpenSession(session.id) }, enabled = canOpen) {
                Text(if (canOpen) "Open session details / video" else "Upload still processing")
            }
        }
    }
}

@Composable
private fun ProgressCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun buildDaysForWeek(weekOffset: Int): List<Long> {
    val today = Calendar.getInstance().apply { setToDayStart() }
    val monday = (today.clone() as Calendar).apply {
        firstDayOfWeek = Calendar.MONDAY
        while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        add(Calendar.WEEK_OF_YEAR, weekOffset)
    }
    return (0..6).map { offset ->
        (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }.timeInMillis
    }
}

private fun groupSessionsByBlock(sessions: List<SessionRecord>, blockHours: Int): Map<HeatmapCellKey, List<SessionRecord>> =
    sessions.groupBy { session ->
        val calendar = Calendar.getInstance().apply { timeInMillis = session.startedAtMs }
        val dayStart = (calendar.clone() as Calendar).apply { setToDayStart() }.timeInMillis
        val blockIndex = calendar.get(Calendar.HOUR_OF_DAY) / blockHours
        HeatmapCellKey(dayStartMs = dayStart, blockIndex = blockIndex)
    }

private fun formatDayLabel(dayStartMs: Long): String =
    SimpleDateFormat("EEE dd", Locale.getDefault()).format(Date(dayStartMs))

private fun formatWeekLabel(startMs: Long, endMs: Long): String {
    val formatter = SimpleDateFormat("dd MMM", Locale.getDefault())
    return "${formatter.format(Date(startMs))} - ${formatter.format(Date(endMs))}"
}

@Composable
private fun WeekAndZoomControls(
    weekLabel: String,
    hasNewerWeek: Boolean,
    zoom: HeatmapZoom,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onPreviousWeek) { Text("← Prev week") }
            Text(weekLabel, style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = onNextWeek, enabled = hasNewerWeek) { Text("Next week →") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onZoomOut, enabled = zoom != HeatmapZoom.Day) { Text("Zoom out") }
            Text("${zoom.label} blocks", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = onZoomIn, enabled = zoom != HeatmapZoom.Hour) { Text("Zoom in") }
        }
    }
}

private fun HeatmapZoom.zoomedOut(): HeatmapZoom =
    when (this) {
        HeatmapZoom.Hour -> HeatmapZoom.FourHours
        HeatmapZoom.FourHours -> HeatmapZoom.Day
        HeatmapZoom.Day -> HeatmapZoom.Day
    }

private fun HeatmapZoom.zoomedIn(): HeatmapZoom =
    when (this) {
        HeatmapZoom.Day -> HeatmapZoom.FourHours
        HeatmapZoom.FourHours -> HeatmapZoom.Hour
        HeatmapZoom.Hour -> HeatmapZoom.Hour
    }

private fun cellColor(count: Int, maxCount: Int, isExpanded: Boolean): Color {
    if (isExpanded) return Color(0xFF1B5E20)
    if (count <= 0 || maxCount <= 0) return Color(0xFF2A2A2A)
    val intensity = (count.toFloat() / maxCount.toFloat()).coerceIn(0.2f, 1f)
    return Color(0xFF2E7D32).copy(alpha = intensity)
}

private fun Calendar.setToDayStart() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}
