package com.inversioncoach.app.motion

import com.inversioncoach.app.model.DrillType
import kotlin.math.roundToInt

data class QualityThresholds(
    val acceptableLineDeviation: Float,
    val minimumGoodFormScore: Int,
    val repAcceptanceThreshold: Int,
    val holdAlignedThreshold: Int,
    val alignmentPersistenceMs: Long,
    val allowedRepAlignmentDropMs: Long,
)

data class UserCalibrationSettings(
    val thresholds: QualityThresholds = DEFAULT_THRESHOLDS,
) {
    fun resolvedThresholds(): QualityThresholds = thresholds

    companion object {
        val DEFAULT_THRESHOLDS: QualityThresholds = QualityThresholds(0.14f, 72, 70, 72, 300L, 300L)
    }
}

data class DrillQualityProfile(
    val drillType: DrillType,
    val componentWeights: Map<String, Float>,
    val dominantFaultMapping: Map<String, String>,
    val repDepthTargetDeg: Float = 95f,
    val repLockoutTargetDeg: Float = 168f,
)

object DrillQualityProfiles {
    private val holdWeights = mapOf(
        "shoulder_stack" to 0.2f,
        "hip_stack" to 0.2f,
        "ankle_stack" to 0.2f,
        "elbow_lock" to 0.15f,
        "knee_extension" to 0.1f,
        "trunk_straightness" to 0.15f,
    )
    private val pikeWeights = mapOf(
        "shoulder_stack" to 0.16f,
        "hip_stack" to 0.2f,
        "ankle_stack" to 0.08f,
        "elbow_lock" to 0.22f,
        "knee_extension" to 0.1f,
        "trunk_straightness" to 0.24f,
    )

    private val faultMap = mapOf(
        "banana_line" to "trunk_straightness",
        "pike" to "hip_stack",
        "bent_knees" to "knee_extension",
        "soft_elbows" to "elbow_lock",
        "line_loss" to "ankle_stack",
        "head_forward" to "shoulder_stack",
    )

    fun byType(drillType: DrillType): DrillQualityProfile = when (drillType) {
        DrillType.FREE_HANDSTAND,
        DrillType.WALL_HANDSTAND,
        DrillType.BACK_TO_WALL_HANDSTAND,
        -> DrillQualityProfile(drillType, holdWeights, faultMap)

        DrillType.PIKE_PUSH_UP,
        DrillType.ELEVATED_PIKE_PUSH_UP,
        -> DrillQualityProfile(drillType, pikeWeights, faultMap, repDepthTargetDeg = 92f)

        DrillType.HANDSTAND_PUSH_UP,
        DrillType.WALL_HANDSTAND_PUSH_UP,
        -> DrillQualityProfile(drillType, holdWeights, faultMap, repDepthTargetDeg = 98f)

        else -> DrillQualityProfile(drillType, holdWeights, faultMap)
    }
}

data class AlignmentScoreSnapshot(
    val rawScore: Int,
    val smoothedScore: Int,
    val dominantFault: String,
    val componentScores: Map<String, Int>,
)

data class StabilitySnapshot(
    val centerlineDeviation: Float,
    val swayAmplitude: Float,
    val swayFrequencyHz: Float,
    val stabilityScore: Int,
)

data class HoldQualitySnapshot(
    val totalHoldDurationMs: Long,
    val alignedHoldDurationMs: Long,
    val alignmentRate: Float,
    val bestAlignedStreakMs: Long,
    val averageAlignmentScore: Int,
    val liveAlignedDurationMs: Long,
)

data class RepQualityResult(
    val repIndex: Int,
    val repScore: Int,
    val repAccepted: Boolean,
    val repFaults: List<String>,
    val failureReason: String,
    val templateSimilarityScore: Int? = null,
)

data class RepQualitySnapshot(
    val totalRepsDetected: Int,
    val acceptedReps: Int,
    val rejectedReps: Int,
    val averageRepQuality: Int,
    val bestRepScore: Int,
    val mostCommonFailureReason: String,
    val latestRep: RepQualityResult?,
)

internal fun Float.toScore(): Int = (this * 100f).roundToInt().coerceIn(0, 100)
