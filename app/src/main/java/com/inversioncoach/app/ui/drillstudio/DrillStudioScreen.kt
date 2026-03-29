package com.inversioncoach.app.ui.drillstudio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.catalog.DrillCatalogRepository
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.StickFigureAnimator
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.isActive

@Composable
fun DrillStudioScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { DrillCatalogRepository(context) }
    val catalog = remember { runCatching { repository.loadCatalog() }.getOrNull() }

    var selectedIndex by remember(catalog) { mutableIntStateOf(0) }
    val drills = catalog?.drills.orEmpty()
    val selectedDrill = drills.getOrNull(selectedIndex)

    var progress by remember(selectedDrill?.id) { mutableFloatStateOf(0f) }
    var autoPlay by remember(selectedDrill?.id) { mutableStateOf(true) }
    var mirrored by remember(selectedDrill?.id) { mutableStateOf(false) }
    var exportedJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(autoPlay, selectedDrill?.id) {
        if (!autoPlay || selectedDrill == null) return@LaunchedEffect
        while (isActive) {
            val fps = selectedDrill.skeletonTemplate.framesPerSecond.coerceAtLeast(1)
            progress += 1f / fps.toFloat()
            if (progress > 1f) progress -= 1f
            kotlinx.coroutines.delay((1000L / fps).coerceAtLeast(16L))
        }
    }

    ScaffoldedScreen(title = "Drill Studio", onBack = onBack) { padding ->
        DrillStudioContent(
            padding = padding,
            drills = drills,
            selectedIndex = selectedIndex,
            onSelectDrill = { selectedIndex = it },
            selectedDrill = selectedDrill,
            progress = progress,
            onProgressChange = {
                autoPlay = false
                progress = it
            },
            autoPlay = autoPlay,
            onToggleAutoPlay = { autoPlay = !autoPlay },
            mirrored = mirrored,
            onToggleMirrored = { mirrored = !mirrored },
            onExport = {
                exportedJson = catalog?.let(repository::exportCatalog)
            },
            exportedJson = exportedJson,
        )
    }
}

@Composable
private fun DrillStudioContent(
    padding: PaddingValues,
    drills: List<DrillTemplate>,
    selectedIndex: Int,
    onSelectDrill: (Int) -> Unit,
    selectedDrill: DrillTemplate?,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    autoPlay: Boolean,
    onToggleAutoPlay: () -> Unit,
    mirrored: Boolean,
    onToggleMirrored: () -> Unit,
    onExport: () -> Unit,
    exportedJson: String?,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Authored Templates",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Animated preview + phase scrubber for authored catalog drills.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selectedDrill != null) {
            item {
                DrillPreviewCard(
                    drill = selectedDrill,
                    progress = progress,
                    onProgressChange = onProgressChange,
                    autoPlay = autoPlay,
                    onToggleAutoPlay = onToggleAutoPlay,
                    mirrored = mirrored,
                    onToggleMirrored = onToggleMirrored,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport) {
                    Text("Export Catalog JSON")
                }
                if (exportedJson != null) {
                    Text(
                        "${exportedJson.length} chars",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (exportedJson != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Text(
                        text = exportedJson.take(700),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        items(drills.indices.toList(), key = { drills[it].id }) { index ->
            val drill = drills[index]
            val selected = selectedIndex == index
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDrill(index) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(drill.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${drill.cameraView.name.lowercase()} • ${drill.comparisonMode.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${drill.movementType.name.lowercase()} • ${drill.phases.size} phases • ${drill.skeletonTemplate.keyframes.size} keyframes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "phases: ${drill.phases.sortedBy { it.order }.joinToString { it.label }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (drills.isEmpty()) {
            item {
                Text(
                    text = "No authored templates found in drill_catalog_v1.json",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DrillPreviewCard(
    drill: DrillTemplate,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    autoPlay: Boolean,
    onToggleAutoPlay: () -> Unit,
    mirrored: Boolean,
    onToggleMirrored: () -> Unit,
) {
    val pose = remember(drill.id, progress, mirrored) {
        StickFigureAnimator.poseAt(drill.skeletonTemplate, progress, mirrored)
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val jointColor = MaterialTheme.colorScheme.secondary

    val activePhase = drill.phases
        .sortedBy { it.order }
        .firstOrNull { phase -> phase.progressWindow?.let { progress in it.start..it.end } == true }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Preview: ${drill.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "progress ${"%.2f".format(progress)} • phase ${activePhase?.label ?: "n/a"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                StickFigureAnimator.canonicalBones.forEach { (start, end) ->
                    val a = pose[start]
                    val b = pose[end]
                    if (a != null && b != null) {
                        drawLine(
                            color = lineColor,
                            start = Offset(a.x * size.width, a.y * size.height),
                            end = Offset(b.x * size.width, b.y * size.height),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                pose.values.forEach { point ->
                    drawCircle(
                        color = jointColor,
                        radius = 6f,
                        center = Offset(point.x * size.width, point.y * size.height),
                        style = Stroke(width = 3f),
                    )
                }
            }
            Slider(value = progress, onValueChange = onProgressChange)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleAutoPlay) { Text(if (autoPlay) "Pause" else "Play") }
                Button(onClick = onToggleMirrored, enabled = drill.skeletonTemplate.mirroredSupported) {
                    Text(if (mirrored) "Unmirror" else "Mirror")
                }
            }
        }
    }
}
