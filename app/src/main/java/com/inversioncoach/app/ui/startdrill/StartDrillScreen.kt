package com.inversioncoach.app.ui.startdrill

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.R
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.components.ScaffoldedScreen

private val defaultSessionOptions = LiveSessionOptions(
    voiceEnabled = true,
    recordingEnabled = true,
    showSkeletonOverlay = true,
    showIdealLine = true,
    zoomOutCamera = true,
)

private data class DrillGridItem(
    val type: DrillType,
    @DrawableRes val imageRes: Int,
)

@Composable
fun StartDrillScreen(
    onBack: () -> Unit,
    onStart: (DrillType, LiveSessionOptions) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.repository(context) }
    val drillByType = remember { DrillConfigs.all.associateBy { it.type } }
    val gridItems = remember(drillByType) {
        listOf(
            DrillGridItem(DrillType.FREE_HANDSTAND, R.drawable.handstand_free_preview),
            DrillGridItem(DrillType.WALL_HANDSTAND, R.drawable.handstand_wall_preview),
            DrillGridItem(DrillType.PIKE_PUSH_UP, R.drawable.pike_pushup_preview),
            DrillGridItem(DrillType.ELEVATED_PIKE_PUSH_UP, R.drawable.pike_pushup_elevated_preview),
            DrillGridItem(DrillType.HANDSTAND_PUSH_UP, R.drawable.handstand_pushup_free_preview),
            DrillGridItem(DrillType.WALL_HANDSTAND_PUSH_UP, R.drawable.handstand_pushup_wall_preview),
        ).filter { drillByType.containsKey(it.type) }
    }

    var selectedDrill by remember { mutableStateOf<DrillType?>(null) }
    var preferredSide by remember { mutableStateOf(defaultSessionOptions.drillCameraSide) }

    LaunchedEffect(selectedDrill) {
        val drill = selectedDrill ?: return@LaunchedEffect
        preferredSide = repository.getDrillCameraSide(drill) ?: defaultSessionOptions.drillCameraSide
    }

    ScaffoldedScreen(title = "Choose Drill", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose your flow", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Tap a drill to select it, then start coaching.",
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
                items(gridItems, key = { it.type.name }) { drill ->
                    DrillGridCard(
                        label = drill.type.displayName,
                        imageRes = drill.imageRes,
                        selected = selectedDrill == drill.type,
                        onClick = { selectedDrill = drill.type },
                    )
                }
            }

            if (selectedDrill != null) {
                Card(
                    onClick = {
                        val drill = selectedDrill ?: return@Card
                        onStart(
                            drill,
                            defaultSessionOptions.copy(drillCameraSide = preferredSide),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        "Start ${selectedDrill?.displayName}",
                        modifier = Modifier.padding(14.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrillGridCard(
    label: String,
    @DrawableRes imageRes: Int,
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
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
