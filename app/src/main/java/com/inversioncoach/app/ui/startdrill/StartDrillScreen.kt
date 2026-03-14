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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.R
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
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
    val label: String,
    @DrawableRes val imageRes: Int,
)

@Composable
fun StartDrillScreen(
    onBack: () -> Unit,
    onStart: (DrillType, LiveSessionOptions) -> Unit,
    onOpenDetail: (DrillType) -> Unit,
) {
    val drillByType = remember { DrillConfigs.all.associateBy { it.type } }
    val gridItems = remember(drillByType) {
        listOf(
            DrillGridItem(DrillType.FREESTANDING_HANDSTAND_FUTURE, "Free Handstand", R.drawable.handstand_free),
            DrillGridItem(DrillType.CHEST_TO_WALL_HANDSTAND, "Wall Handstand", R.drawable.handstand_wall),
            DrillGridItem(DrillType.PIKE_PUSH_UP, "Pike Push-Up", R.drawable.pike_pushup),
            DrillGridItem(DrillType.ELEVATED_PIKE_PUSH_UP, "Elevated Pike Push", R.drawable.pike_pushup_elevated),
            DrillGridItem(DrillType.PUSH_UP, "Handstand Push-Up", R.drawable.handstand_pushup_free),
            DrillGridItem(DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP, "Wall Handstand PU", R.drawable.handstand_pushup_wall),
        ).filter { drillByType.containsKey(it.type) }
    }

    ScaffoldedScreen(title = "Choose Drill", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Drill Selection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(gridItems, key = { it.type.name }) { drill ->
                    DrillGridCard(
                        label = drill.label,
                        imageRes = drill.imageRes,
                        onClick = { onStart(drill.type, defaultSessionOptions) },
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
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }
}
