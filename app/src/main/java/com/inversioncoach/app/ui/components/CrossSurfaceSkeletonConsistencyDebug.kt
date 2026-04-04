package com.inversioncoach.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inversioncoach.app.drills.catalog.JointPoint

@Composable
fun CrossSurfaceSkeletonConsistencyDebug() {
    val samplePose = mapOf(
        "head" to JointPoint(0.50f, 0.12f),
        "left_shoulder" to JointPoint(0.40f, 0.28f),
        "right_shoulder" to JointPoint(0.60f, 0.28f),
        "left_elbow" to JointPoint(0.33f, 0.42f),
        "right_elbow" to JointPoint(0.67f, 0.42f),
        "left_wrist" to JointPoint(0.30f, 0.58f),
        "right_wrist" to JointPoint(0.70f, 0.58f),
        "left_hip" to JointPoint(0.45f, 0.56f),
        "right_hip" to JointPoint(0.55f, 0.56f),
        "left_knee" to JointPoint(0.43f, 0.76f),
        "right_knee" to JointPoint(0.57f, 0.76f),
        "left_ankle" to JointPoint(0.42f, 0.94f),
        "right_ankle" to JointPoint(0.58f, 0.94f),
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pose authoring (displayed image bounds)", style = MaterialTheme.typography.labelMedium)
        OverlaySkeletonPreview(
            joints = samplePose,
            policy = SkeletonPreviewPolicies.poseAuthoring,
            resolveOverlayBounds = { canvasSize ->
                SkeletonRenderContract.displayedImageBounds(
                    canvasSize = canvasSize,
                    imageWidth = 1920,
                    imageHeight = 1080,
                )
            },
            overlayContent = {
                drawRect(color = Color(0x182196F3))
            },
        )

        Text("Motion preview (content-rect bounds)", style = MaterialTheme.typography.labelMedium)
        OverlaySkeletonPreview(joints = samplePose, policy = SkeletonPreviewPolicies.motionPreview)

        Text("Choose drill preview (content-rect bounds)", style = MaterialTheme.typography.labelMedium)
        OverlaySkeletonPreview(
            joints = samplePose,
            policy = SkeletonPreviewPolicies.chooseDrillPreview,
            modifier = Modifier.background(Color.Transparent),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CrossSurfaceSkeletonConsistencyDebugPreview() {
    CrossSurfaceSkeletonConsistencyDebug()
}
