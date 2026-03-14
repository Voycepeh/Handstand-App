package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.DrillType
import kotlin.math.abs

object DrillProfiles {
    private val baseline = DrillThresholdProfile(
        drillType = DrillType.CHEST_TO_WALL_HANDSTAND,
        holdStartStableMs = 700,
        visualPersistFrames = 5,
        spokenPersistFrames = 12,
        sameCueCooldownMs = 2000,
        sameIssueFamilyCooldownMs = 1500,
        encouragementCooldownMs = 4000,
        stackExcellentNorm = 0.07f,
        stackAcceptableNorm = 0.13f,
        stackPoorNorm = 0.20f,
        bodyLineGoodNorm = 0.08f,
        bodyLineWarnNorm = 0.15f,
        bodyLinePoorNorm = 0.20f,
        kneeGoodDeg = 170f,
        kneeWarnDeg = 160f,
        lockoutDeg = 170f,
        descentGoodSec = 1.2f,
        descentAcceptableSec = 0.8f,
        descentPoorSec = 0.55f,
        hipAboveShoulderNormMin = 0.16f,
        headForwardNormMax = 0.14f,
        archHipNormThreshold = 0.16f,
        archMarginNorm = 0.02f,
        wallNearNorm = 0.08f,
        shoulderEarNearNorm = 0.09f,
    )

    val defaults: Map<DrillType, DrillThresholdProfile> = DrillType.values().associateWith { drill ->
        when (drill) {
            DrillType.WALL_PUSH_UP,
            DrillType.INCLINE_OR_KNEE_PUSH_UP,
            DrillType.STANDARD_PUSH_UP,
            DrillType.PUSH_UP -> baseline.copy(
                drillType = drill,
                holdStartStableMs = 350,
                visualPersistFrames = 4,
                spokenPersistFrames = 9,
                lockoutDeg = 168f,
                descentGoodSec = 1.1f,
                descentAcceptableSec = 0.75f,
                descentPoorSec = 0.5f,
                bodyLineGoodNorm = 0.10f,
                bodyLineWarnNorm = 0.16f,
                bodyLinePoorNorm = 0.22f,
            )

            DrillType.BODYWEIGHT_SQUAT,
            DrillType.REVERSE_LUNGE,
            DrillType.BURPEE -> baseline.copy(
                drillType = drill,
                holdStartStableMs = 300,
                visualPersistFrames = 4,
                stackAcceptableNorm = 0.16f,
                stackPoorNorm = 0.24f,
                descentGoodSec = 1.0f,
                descentAcceptableSec = 0.7f,
            )

            DrillType.FOREARM_PLANK,
            DrillType.HOLLOW_BODY_HOLD,
            DrillType.L_SIT_HOLD,
            DrillType.WALL_FACING_HANDSTAND_HOLD,
            DrillType.STANDING_POSTURE_HOLD -> baseline.copy(
                drillType = drill,
                holdStartStableMs = 1200,
                visualPersistFrames = 6,
                stackExcellentNorm = 0.05f,
                stackAcceptableNorm = 0.10f,
                stackPoorNorm = 0.16f,
                bodyLineGoodNorm = 0.06f,
                bodyLineWarnNorm = 0.12f,
                bodyLinePoorNorm = 0.18f,
                kneeWarnDeg = 165f,
            )

            DrillType.SIT_UP,
            DrillType.GLUTE_BRIDGE,
            DrillType.HANGING_KNEE_RAISE -> baseline.copy(
                drillType = drill,
                holdStartStableMs = 300,
                visualPersistFrames = 4,
                spokenPersistFrames = 9,
                descentGoodSec = 1.4f,
                descentAcceptableSec = 0.9f,
                descentPoorSec = 0.6f,
                stackAcceptableNorm = 0.15f,
                stackPoorNorm = 0.22f,
            )

            DrillType.PULL_UP_OR_ASSISTED_PULL_UP,
            DrillType.PARALLEL_BAR_DIP,
            DrillType.CHEST_TO_WALL_HANDSTAND,
            DrillType.BACK_TO_WALL_HANDSTAND,
            DrillType.FREESTANDING_HANDSTAND_FUTURE -> baseline.copy(drillType = drill)

            DrillType.PIKE_PUSH_UP -> baseline.copy(drillType = drill, hipAboveShoulderNormMin = 0.18f)
            DrillType.ELEVATED_PIKE_PUSH_UP -> baseline.copy(drillType = drill, hipAboveShoulderNormMin = 0.20f)
            DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP -> baseline.copy(
                drillType = drill,
                descentGoodSec = 1.6f,
                descentAcceptableSec = 1.1f,
                descentPoorSec = 0.75f,
            )
        }
    }
}

class DrillMetricsCalculator {
    fun computeSubscores(drill: DrillType, metrics: DerivedMetrics, profile: DrillThresholdProfile): Map<String, Int> = when (drill) {
        DrillType.STANDING_POSTURE_HOLD,
        DrillType.FOREARM_PLANK,
        DrillType.HOLLOW_BODY_HOLD,
        DrillType.WALL_FACING_HANDSTAND_HOLD,
        DrillType.L_SIT_HOLD -> standing(metrics, profile)
        DrillType.PUSH_UP,
        DrillType.WALL_PUSH_UP,
        DrillType.INCLINE_OR_KNEE_PUSH_UP,
        DrillType.STANDARD_PUSH_UP,
        DrillType.PARALLEL_BAR_DIP -> pushUp(metrics, profile)
        DrillType.SIT_UP,
        DrillType.GLUTE_BRIDGE,
        DrillType.HANGING_KNEE_RAISE -> sitUp(metrics, profile)
        DrillType.BODYWEIGHT_SQUAT,
        DrillType.REVERSE_LUNGE,
        DrillType.BURPEE -> back(metrics, profile)
        DrillType.PULL_UP_OR_ASSISTED_PULL_UP -> elevatedPike(metrics, profile)
        DrillType.CHEST_TO_WALL_HANDSTAND -> chest(metrics, profile)
        DrillType.BACK_TO_WALL_HANDSTAND -> back(metrics, profile)
        DrillType.PIKE_PUSH_UP -> pike(metrics, profile)
        DrillType.ELEVATED_PIKE_PUSH_UP -> elevatedPike(metrics, profile)
        DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP -> negative(metrics, profile)
        else -> chest(metrics, profile)
    }

    private fun standing(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "line_quality" to scoreFromDeviation(m.bodyLineDeviationNorm, p.bodyLineGoodNorm, p.bodyLinePoorNorm),
        "shoulder_stack" to scoreFromDeviation(abs(m.stackOffsetsNorm["shoulder_stack_offset"] ?: 0f), p.stackExcellentNorm, p.stackPoorNorm),
        "rib_pelvis_control" to (100 - m.bananaProxyScore).coerceIn(0, 100),
        "knee_lockout" to m.kneeExtensionScore,
        "stillness" to ((1f - (m.pathMetrics["path_variance"] ?: 0.15f)) * 100f).toInt().coerceIn(0, 100),
    )

    private fun chest(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "line_quality" to scoreFromDeviation(m.bodyLineDeviationNorm, p.bodyLineGoodNorm, p.bodyLinePoorNorm),
        "shoulder_openness" to m.shoulderOpennessScore,
        "scapular_elevation" to m.scapularElevationProxyScore,
        "rib_pelvis_control" to (100 - m.bananaProxyScore).coerceIn(0, 100),
        "leg_tension" to m.kneeExtensionScore,
    )

    private fun back(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "shoulder_push" to m.scapularElevationProxyScore,
        "reduced_arch" to (100 - m.bananaProxyScore).coerceIn(0, 100),
        "hip_stack" to scoreFromDeviation(abs(m.stackOffsetsNorm["hip_stack_offset"] ?: 0f), p.stackExcellentNorm, p.stackPoorNorm),
        "wall_reliance" to wallReliance(m, p),
        "leg_tension" to m.kneeExtensionScore,
    )

    private fun pushUp(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "torso_line" to scoreFromDeviation(m.bodyLineDeviationNorm, p.bodyLineGoodNorm, p.bodyLinePoorNorm),
        "depth" to ((m.pathMetrics["depth_norm"] ?: 0.5f) * 100f).toInt().coerceIn(0, 100),
        "lockout" to if ((m.jointAngles["elbow_angle"] ?: 0f) >= p.lockoutDeg) 100 else 55,
        "elbow_path" to scoreElbowPath(m),
        "tempo_control" to scoreTempo(m, p),
    )

    private fun sitUp(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "trunk_range" to ((m.pathMetrics["depth_norm"] ?: 0.45f) * 100f).toInt().coerceIn(0, 100),
        "controlled_descent" to scoreTempo(m, p),
        "tempo_control" to scoreTempo(m, p),
        "knee_stability" to m.kneeExtensionScore,
        "symmetry" to scoreFromDeviation(abs(m.stackOffsetsNorm["hip_stack_offset"] ?: 0f), p.stackExcellentNorm, p.stackPoorNorm),
    )

    private fun pike(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "hip_height" to scoreHipHeight(m, p),
        "shoulder_loading" to scoreFromDeviation(abs(m.stackOffsetsNorm["shoulder_stack_offset"] ?: 0f), p.stackExcellentNorm, p.stackPoorNorm),
        "head_path" to scoreHeadPath(m, p),
        "elbow_path" to scoreElbowPath(m),
        "tempo_control" to scoreTempo(m, p),
    )

    private fun elevatedPike(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "loading_angle" to scoreFromDeviation(abs(m.stackOffsetsNorm["shoulder_stack_offset"] ?: 0f), p.stackExcellentNorm * 0.85f, p.stackPoorNorm),
        "depth" to ((m.pathMetrics["depth_norm"] ?: 0.6f) * 100f).toInt().coerceIn(0, 100),
        "pressing_path" to ((1f - (m.pathMetrics["path_variance"] ?: 0.4f)) * 100f).toInt().coerceIn(0, 100),
        "lockout" to if ((m.jointAngles["elbow_angle"] ?: 0f) >= p.lockoutDeg) 100 else 55,
        "tempo_control" to scoreTempo(m, p),
    )

    private fun negative(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "top_position" to if ((m.jointAngles["elbow_angle"] ?: 0f) >= p.lockoutDeg) 100 else 60,
        "descent_control" to scoreTempo(m, p),
        "path_consistency" to ((1f - (m.pathMetrics["path_variance"] ?: 0.4f)) * 100f).toInt().coerceIn(0, 100),
        "line_retention" to scoreFromDeviation(m.bodyLineDeviationNorm, p.bodyLineGoodNorm, p.bodyLinePoorNorm),
        "bottom_position" to ((m.pathMetrics["depth_norm"] ?: 0.5f) * 100f).toInt().coerceIn(0, 100),
    )

    private fun scoreFromDeviation(value: Float, good: Float, poor: Float): Int = when {
        value <= good -> 100
        value >= poor -> 20
        else -> (100 - ((value - good) / (poor - good) * 80f)).toInt().coerceIn(20, 99)
    }

    private fun scoreHipHeight(m: DerivedMetrics, p: DrillThresholdProfile): Int {
        val h = m.pathMetrics["hip_above_shoulder_norm"] ?: 0f
        return (h / p.hipAboveShoulderNormMin * 100f).toInt().coerceIn(0, 100)
    }

    private fun scoreHeadPath(m: DerivedMetrics, p: DrillThresholdProfile): Int {
        val f = abs(m.pathMetrics["head_forward_norm"] ?: 0f)
        return if (f <= p.headForwardNormMax) 100 else (100 - ((f - p.headForwardNormMax) * 240f).toInt()).coerceIn(0, 100)
    }

    private fun scoreElbowPath(m: DerivedMetrics): Int {
        val elbowAngle = m.jointAngles["elbow_angle"] ?: 150f
        return (100 - abs(150f - elbowAngle) * 1.2f).toInt().coerceIn(0, 100)
    }

    private fun scoreTempo(m: DerivedMetrics, p: DrillThresholdProfile): Int {
        val descent = m.tempoMetrics["descent_sec"] ?: return 70
        return when {
            descent >= p.descentGoodSec -> 100
            descent >= p.descentAcceptableSec -> 75
            descent < p.descentPoorSec -> 25
            else -> 50
        }
    }

    private fun wallReliance(m: DerivedMetrics, p: DrillThresholdProfile): Int {
        val ankleNearWall = (m.pathMetrics["ankle_wall_norm"] ?: 0.3f) < p.wallNearNorm
        val linePoor = m.bodyLineDeviationNorm > p.bodyLineWarnNorm
        return when {
            ankleNearWall && linePoor -> 30
            ankleNearWall -> 65
            else -> 90
        }
    }
}
