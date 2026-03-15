package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.DrillType
import kotlin.math.abs

object DrillProfiles {
    private val baselineThreshold = DrillThresholdProfile(
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

    fun forDrill(drillType: DrillType, metrics: List<MetricWeight>): DrillCalibrationProfile {
        val thresholds = thresholdFor(drillType)
        val scoreWeights = metrics.associate { it.key to it.weight }
        return DrillCalibrationProfile(
            drillType = drillType,
            thresholds = thresholds,
            scoreWeights = scoreWeights,
            wallReferenceX = if (drillType == DrillType.BACK_TO_WALL_HANDSTAND) 0.95f else 0.95f,
            smoothingAlpha = 0.35f,
            issueActivationFrames = mapOf(
                IssueType.ELBOWS_FLARING to 5,
                IssueType.INSUFFICIENT_DEPTH to 5,
                IssueType.INCOMPLETE_LOCKOUT to 5,
            ),
        )
    }

    private fun thresholdFor(drill: DrillType): DrillThresholdProfile = when (drill) {
        DrillType.PUSH_UP -> baselineThreshold.copy(
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

        DrillType.CHEST_TO_WALL_HANDSTAND,
        DrillType.FREESTANDING_HANDSTAND_FUTURE -> baselineThreshold.copy(drillType = drill)

        DrillType.PIKE_PUSH_UP -> baselineThreshold.copy(drillType = drill, hipAboveShoulderNormMin = 0.18f)
        DrillType.ELEVATED_PIKE_PUSH_UP -> baselineThreshold.copy(drillType = drill, hipAboveShoulderNormMin = 0.20f)
        DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP -> baselineThreshold.copy(
            drillType = drill,
            descentGoodSec = 1.6f,
            descentAcceptableSec = 1.1f,
            descentPoorSec = 0.75f,
        )

        else -> baselineThreshold.copy(drillType = drill)
    }
}

class DrillMetricsCalculator {
    fun computeSubscores(drill: DrillType, metrics: DerivedMetrics, calibration: DrillCalibrationProfile): Map<String, Int> = when (drill) {
        DrillType.CHEST_TO_WALL_HANDSTAND,
        DrillType.FREESTANDING_HANDSTAND_FUTURE -> chest(metrics, calibration.thresholds)

        DrillType.PUSH_UP -> pushUp(metrics, calibration.thresholds)
        DrillType.PIKE_PUSH_UP -> pike(metrics, calibration.thresholds)
        DrillType.ELEVATED_PIKE_PUSH_UP -> elevatedPike(metrics, calibration.thresholds)
        DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP -> negative(metrics, calibration.thresholds)
        else -> throw IllegalArgumentException("Unsupported drill for score computation: $drill")
    }

    fun evaluateMetrics(drill: DrillType, metrics: DerivedMetrics, calibration: DrillCalibrationProfile): List<MetricDebugEvaluation> {
        val subscores = computeSubscores(drill, metrics, calibration)
        val issues = IssueClassifier().classify(drill, metrics, calibration, emptyMap(), metrics.timestampMs)
            .map { it.type }
            .toSet()
        return subscores.map { (metricKey, score) ->
            val rawValue = rawMetricValue(metricKey, metrics)
            MetricDebugEvaluation(
                metricKey = metricKey,
                rawValue = rawValue,
                thresholdBand = thresholdBand(metricKey, rawValue, calibration.thresholds),
                subScore = score,
                triggeredIssue = issues.firstOrNull { issue -> metricKey.contains(issue.name.lowercase().substringBefore('_')) },
            )
        }
    }

    private fun chest(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "line_quality" to scoreFromDeviation(m.bodyLineDeviationNorm, p.bodyLineGoodNorm, p.bodyLinePoorNorm),
        "shoulder_openness" to m.shoulderOpennessScore,
        "scapular_elevation" to m.scapularElevationProxyScore,
        "rib_pelvis_control" to (100 - m.bananaProxyScore).coerceIn(0, 100),
        "leg_tension" to m.kneeExtensionScore,
    )

    private fun pushUp(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "torso_line" to scoreFromDeviation(m.bodyLineDeviationNorm, p.bodyLineGoodNorm, p.bodyLinePoorNorm),
        "depth" to ((m.pathMetrics["depth_norm"] ?: 0.5f) * 100f).toInt().coerceIn(0, 100),
        "lockout" to if ((m.jointAngles["elbow_angle"] ?: 0f) >= p.lockoutDeg) 100 else 55,
        "elbow_path" to scoreElbowPath(m),
        "tempo_control" to scoreTempo(m, p),
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

    private fun rawMetricValue(metricKey: String, m: DerivedMetrics): Float = when (metricKey) {
        "line_quality", "torso_line", "line_retention" -> m.bodyLineDeviationNorm
        "depth", "bottom_position" -> m.pathMetrics["depth_norm"] ?: 0f
        "lockout", "top_position" -> m.jointAngles["elbow_angle"] ?: 0f
        "tempo_control", "descent_control" -> m.tempoMetrics["descent_sec"] ?: 0f
        "head_path" -> m.pathMetrics["head_forward_norm"] ?: 0f
        else -> m.stackOffsetsNorm["shoulder_stack_offset"] ?: 0f
    }

    private fun thresholdBand(metricKey: String, rawValue: Float, p: DrillThresholdProfile): String = when (metricKey) {
        "line_quality", "torso_line", "line_retention" -> when {
            rawValue <= p.bodyLineGoodNorm -> "good"
            rawValue <= p.bodyLineWarnNorm -> "warn"
            else -> "poor"
        }
        "tempo_control", "descent_control" -> when {
            rawValue >= p.descentGoodSec -> "good"
            rawValue >= p.descentAcceptableSec -> "acceptable"
            rawValue < p.descentPoorSec -> "poor"
            else -> "warn"
        }
        else -> "derived"
    }
}
