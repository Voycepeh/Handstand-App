package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.DrillType

/**
 * Consolidated per-drill calibration profile used across smoothing, scoring, and issue detection.
 */
data class DrillCalibrationProfile(
    val drillType: DrillType,
    val thresholds: DrillThresholdProfile,
    val scoreWeights: Map<String, Int>,
    val wallReferenceX: Float = 0.95f,
    val smoothingAlpha: Float = 0.35f,
    val frameAcceptanceRules: FrameAcceptanceRules = FrameAcceptanceRules(),
    val issueActivationFrames: Map<IssueType, Int> = emptyMap(),
)

data class FrameAcceptanceRules(
    val minConfidenceForMedium: Float = 0.5f,
    val minConfidenceForHigh: Float = 0.75f,
    val requireStablePoseForHigh: Boolean = true,
)
