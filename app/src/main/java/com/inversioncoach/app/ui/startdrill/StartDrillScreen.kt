package com.inversioncoach.app.ui.startdrill

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.SelectableDrill
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.StickFigureAnimator
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.overlay.EffectiveView
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen

private val defaultSessionOptions = LiveSessionOptions(
    voiceEnabled = true,
    recordingEnabled = true,
    showSkeletonOverlay = true,
    showIdealLine = true,
    zoomOutCamera = true,
    effectiveView = EffectiveView.SIDE,
)

enum class StartDrillDestination { LIVE, WORKSPACE }

@Composable
fun StartDrillScreen(
    onBack: () -> Unit,
    onStart: (DrillType, LiveSessionOptions) -> Unit,
    destination: StartDrillDestination = StartDrillDestination.LIVE,
    onOpenWorkspace: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val drills by repository.observeSelectableTrainingDrills().collectAsState(initial = emptyList())

    var selectedDrillId by remember { mutableStateOf<String?>(null) }
    var preferredSide by remember { mutableStateOf(defaultSessionOptions.drillCameraSide) }
    var selectedView by remember { mutableStateOf(EffectiveView.SIDE) }
    var showCenterOfGravity by remember { mutableStateOf(true) }

    val selectedDrill = remember(selectedDrillId, drills) { drills.firstOrNull { it.id == selectedDrillId } }

    LaunchedEffect(drills) {
        if (selectedDrillId != null && drills.none { it.id == selectedDrillId }) selectedDrillId = null
    }

    LaunchedEffect(selectedDrill?.id) {
        val drill = selectedDrill ?: return@LaunchedEffect
        preferredSide = repository.getDrillCameraSide(drill.legacyDrillType) ?: defaultSessionOptions.drillCameraSide
        selectedView = if (drill.legacyDrillType == DrillType.FREESTYLE) EffectiveView.FREESTYLE else EffectiveView.SIDE
    }

    ScaffoldedScreen(title = if (destination == StartDrillDestination.WORKSPACE) "Drills" else "Choose Drill", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (destination == StartDrillDestination.WORKSPACE) "Drill catalog" else "Choose your flow", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = if (destination == StartDrillDestination.WORKSPACE) {
                    "Tap a drill to open its workspace for coaching, uploads, comparison, and history."
                } else {
                    "Tap a drill to select it, then start coaching."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(drills, key = { it.id }) { drill ->
                    DrillGridCard(
                        drill = drill,
                        selected = selectedDrillId == drill.id,
                        onClick = {
                            selectedDrillId = drill.id
                            if (destination == StartDrillDestination.WORKSPACE) {
                                onOpenWorkspace?.invoke(drill.id)
                            }
                        },
                    )
                }
            }

            if (drills.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "No drills available yet. Create or import a drill to get started.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (selectedDrill != null) {
                if (destination == StartDrillDestination.LIVE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        EffectiveView.entries.forEach { view ->
                            FilterChip(
                                selected = selectedView == view,
                                onClick = { selectedView = view },
                                label = { Text(view.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("Show CoG star")
                        Switch(checked = showCenterOfGravity, onCheckedChange = { showCenterOfGravity = it })
                    }
                    Card(
                        onClick = {
                            val drill = selectedDrill ?: return@Card
                            onStart(
                                drill.legacyDrillType,
                                defaultSessionOptions.copy(
                                    drillCameraSide = preferredSide,
                                    showCenterOfGravity = showCenterOfGravity,
                                    effectiveView = selectedView,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            "Start ${selectedDrill.name}",
                            modifier = Modifier.padding(14.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrillGridCard(
    drill: SelectableDrill,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DrillSkeletonPreview(drill = drill)
            Text(
                text = drill.name,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DrillSkeletonPreview(drill: SelectableDrill) {
    val skeleton = drill.previewSkeleton
    if (skeleton == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
        ) {
            Text(
                text = "Preview unavailable",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "choose_drill_preview")
    val previewLineColor = MaterialTheme.colorScheme.primary
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (1000f * 16f / skeleton.framesPerSecond.coerceAtLeast(1)).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "choose_drill_preview_progress",
    ).value
    val pose = remember(skeleton, progress) { StickFigureAnimator.poseAt(skeleton, progress) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
    ) {
        StickFigureAnimator.canonicalBones.forEach { (start, end) ->
            val a = pose[start]
            val b = pose[end]
            if (a != null && b != null) {
                drawLine(
                    color = previewLineColor,
                    start = a.toOffset(size.width, size.height),
                    end = b.toOffset(size.width, size.height),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun JointPoint.toOffset(width: Float, height: Float): Offset = Offset(x * width, y * height)
