package com.inversioncoach.app.ui.drillstudio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.inversioncoach.app.drills.catalog.AnalysisPlane
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.drills.catalog.DrillCatalogRepository
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.StickFigureAnimator
import com.inversioncoach.app.ui.components.DropdownOption
import com.inversioncoach.app.ui.components.MultiSelectChipsField
import com.inversioncoach.app.ui.components.ReliableDropdownField
import com.inversioncoach.app.ui.components.ScaffoldedScreen
import kotlinx.coroutines.isActive

@Composable
fun DrillStudioScreen(
    onBack: () -> Unit,
    initRequest: DrillStudioInitRequest,
) {
    val context = LocalContext.current
    val vm = remember {
        DrillStudioViewModel(
            repository = DrillCatalogRepository(context),
        )
    }
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(initRequest.mode, initRequest.drillId) {
        vm.initialize(initRequest)
    }

    ScaffoldedScreen(title = "Drill Studio", onBack = onBack) { padding ->
        when (val state = uiState) {
            DrillStudioUiState.Loading -> DrillStudioLoading(padding)
            is DrillStudioUiState.Error -> DrillStudioError(
                padding = padding,
                message = state.message,
                onRetry = { vm.initialize(initRequest) },
                onBack = onBack,
            )
            is DrillStudioUiState.Ready -> DrillStudioEditor(
                padding = padding,
                draft = state.draft,
                sourceSeedId = state.sourceSeedId,
                onUpdateDraft = vm::updateDraft,
            )
        }
    }
}

@Composable
private fun DrillStudioLoading(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Initializing editor…", style = MaterialTheme.typography.titleMedium)
        Text("Loading draft and validating drill state.")
    }
}

@Composable
private fun DrillStudioError(
    padding: PaddingValues,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Could not open Drill Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text("Retry") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun DrillStudioEditor(
    padding: PaddingValues,
    draft: DrillTemplate,
    sourceSeedId: String?,
    onUpdateDraft: ((DrillTemplate) -> DrillTemplate) -> Unit,
) {
    var progress by remember(draft.id) { mutableFloatStateOf(0f) }
    var autoPlay by remember(draft.id) { mutableStateOf(true) }
    var mirrored by remember(draft.id) { mutableStateOf(false) }

    LaunchedEffect(autoPlay, draft.id, draft.skeletonTemplate.framesPerSecond) {
        if (!autoPlay) return@LaunchedEffect
        while (isActive) {
            val fps = draft.skeletonTemplate.framesPerSecond.coerceAtLeast(1)
            progress += 1f / fps.toFloat()
            if (progress > 1f) progress -= 1f
            kotlinx.coroutines.delay((1000L / fps).coerceAtLeast(16L))
        }
    }

    val cameraOptions = remember { CameraView.entries.map { DropdownOption(it, it.name.pretty()) } }
    val planeOptions = remember { AnalysisPlane.entries.map { DropdownOption(it, it.name.pretty()) } }
    val movementOptions = remember { CatalogMovementType.entries.map { DropdownOption(it, it.name.pretty()) } }
    val comparisonOptions = remember { ComparisonMode.entries.map { DropdownOption(it, it.name.pretty()) } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Drill info") {
                Text("Editing: ${draft.title}")
                if (sourceSeedId != null) {
                    Text(
                        "Seeded source: $sourceSeedId (editing draft copy)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Custom draft")
                }
                ReliableDropdownField(
                    label = "Movement type",
                    selected = movementOptions.firstOrNull { it.value == draft.movementType } ?: movementOptions.first(),
                    options = movementOptions,
                    onOptionSelected = { option -> onUpdateDraft { it.copy(movementType = option.value) } },
                )
                ReliableDropdownField(
                    label = "Comparison mode",
                    selected = comparisonOptions.firstOrNull { it.value == draft.comparisonMode } ?: comparisonOptions.first(),
                    options = comparisonOptions,
                    onOptionSelected = { option -> onUpdateDraft { it.copy(comparisonMode = option.value) } },
                )
            }
        }

        item {
            SectionCard(title = "View config") {
                MultiSelectChipsField(
                    label = "Supported views",
                    options = cameraOptions,
                    selectedValues = draft.supportedViews.toSet(),
                    onToggle = { view ->
                        onUpdateDraft { current ->
                            val next = if (view in current.supportedViews) {
                                current.supportedViews - view
                            } else {
                                current.supportedViews + view
                            }
                            current.copy(supportedViews = next.ifEmpty { listOf(current.cameraView) }.distinct())
                        }
                    },
                )
                ReliableDropdownField(
                    label = "Primary/default view",
                    selected = cameraOptions.firstOrNull { it.value == draft.cameraView } ?: cameraOptions.first(),
                    options = cameraOptions.filter { it.value in draft.supportedViews },
                    onOptionSelected = { option -> onUpdateDraft { it.copy(cameraView = option.value) } },
                )
                ReliableDropdownField(
                    label = "Analysis plane",
                    selected = planeOptions.firstOrNull { it.value == draft.analysisPlane } ?: planeOptions.first(),
                    options = planeOptions,
                    onOptionSelected = { option -> onUpdateDraft { it.copy(analysisPlane = option.value) } },
                )
            }
        }

        item {
            SectionCard(title = "Phase editor") {
                DrillPreviewCard(
                    drill = draft,
                    progress = progress,
                    onProgressChange = {
                        autoPlay = false
                        progress = it
                    },
                    autoPlay = autoPlay,
                    onToggleAutoPlay = { autoPlay = !autoPlay },
                    mirrored = mirrored,
                    onToggleMirrored = { mirrored = !mirrored },
                )
                Text("${draft.phases.size} phases")
                draft.phases.sortedBy { it.order }.forEach { phase ->
                    val bounds = phase.progressWindow
                    Text("${phase.order}. ${phase.label} (${formatWindow(bounds?.start)}-${formatWindow(bounds?.end)})")
                }
            }
        }

        item {
            SectionCard(title = "Pose editor") {
                Text("Frames per second: ${draft.skeletonTemplate.framesPerSecond}")
                Slider(
                    value = draft.skeletonTemplate.framesPerSecond.toFloat(),
                    onValueChange = { fps ->
                        onUpdateDraft { current ->
                            current.copy(skeletonTemplate = current.skeletonTemplate.copy(framesPerSecond = fps.toInt().coerceAtLeast(12)))
                        }
                    },
                    valueRange = 12f..60f,
                )
                Text("Keyframes: ${draft.skeletonTemplate.keyframes.size}")
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
            Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private fun String.pretty(): String =
    lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun formatWindow(value: Float?): String = value?.let { "%.2f".format(it) } ?: "n/a"
