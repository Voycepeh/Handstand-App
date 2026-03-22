package com.inversioncoach.app.ui.live

import androidx.compose.ui.geometry.Rect
import com.inversioncoach.app.model.JointPoint

data class LivePoseDebugOverlayState(
    val rawLandmarks: List<JointPoint> = emptyList(),
    val transformedLandmarks: List<JointPoint> = emptyList(),
    val previewContentBounds: Rect? = null,
    val showReferenceLines: Boolean = true,
    val showConfidence: Boolean = true,
)
