package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.DrillType
import kotlin.math.abs

private data class ScoreBand(val minInclusive: Float? = null, val maxInclusive: Float? = null, val score: Int, val label: String)

object DrillProfiles {
    private const val STRICTNESS_ANGLE_DELTA_DEG = 5f
    private const val STRICTNESS_STACK_DELTA_NORM = 0.02f

    private val baselineThreshold = DrillThresholdProfile(
        drillType = DrillType.CHEST_TO_WALL_HANDSTAND,
        holdStartStableMs = 700,
        visualPersistFrames = 5,
        spokenPersistFrames = 12,
        sameCueCooldownMs = 2000,
        sameIssueFamilyCooldownMs = 1500,
        encouragementCooldownMs = 4000,
        stackExcellentNorm = 0.06f,
        stackAcceptableNorm = 0.12f,
        stackPoorNorm = 0.16f,
        bodyLineGoodNorm = 0.08f,
        bodyLineWarnNorm = 0.15f,
        bodyLinePoorNorm = 0.20f,
        elbowExcellentDeg = 175f,
        elbowAcceptableDeg = 165f,
        elbowSoftDeg = 155f,
        kneeExcellentDeg = 175f,
        kneeAcceptableDeg = 165f,
        kneeSoftDeg = 155f,
        shoulderExcellentMinDeg = 175f,
        shoulderExcellentMaxDeg = 185f,
        shoulderAcceptableMinDeg = 165f,
        shoulderLimitedMinDeg = 155f,
        hipLineExcellentMinDeg = 170f,
        hipLineExcellentMaxDeg = 180f,
        hipLineAcceptableMinDeg = 160f,
        kneeGoodDeg = 170f,
        kneeWarnDeg = 160f,
        lockoutDeg = 170f,
        lockoutWarnDeg = 160f,
        elbowBottomFullDepthMinDeg = 80f,
        elbowBottomFullDepthMaxDeg = 100f,
        elbowBottomCollapseDeg = 75f,
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

    fun forDrill(
        drillType: DrillType,
        metrics: List<MetricWeight>,
        strictness: ThresholdStrictness = ThresholdStrictness.STANDARD,
    ): DrillCalibrationProfile {
        val thresholds = applyStrictness(thresholdFor(drillType), strictness)
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

    private fun applyStrictness(base: DrillThresholdProfile, strictness: ThresholdStrictness): DrillThresholdProfile {
        val direction = when (strictness) {
            ThresholdStrictness.BEGINNER -> 1f
            ThresholdStrictness.STANDARD -> 0f
            ThresholdStrictness.ADVANCED -> -1f
        }
        val angleDelta = STRICTNESS_ANGLE_DELTA_DEG * direction
        val stackDelta = STRICTNESS_STACK_DELTA_NORM * direction
        return base.copy(
            stackExcellentNorm = (base.stackExcellentNorm + stackDelta).coerceAtLeast(0.01f),
            stackAcceptableNorm = (base.stackAcceptableNorm + stackDelta).coerceAtLeast(base.stackExcellentNorm + 0.01f),
            stackPoorNorm = (base.stackPoorNorm + stackDelta).coerceAtLeast(base.stackAcceptableNorm + 0.01f),
            elbowExcellentDeg = (base.elbowExcellentDeg - angleDelta).coerceIn(160f, 179f),
            elbowAcceptableDeg = (base.elbowAcceptableDeg - angleDelta).coerceIn(150f, 175f),
            elbowSoftDeg = (base.elbowSoftDeg - angleDelta).coerceIn(140f, 170f),
            kneeExcellentDeg = (base.kneeExcellentDeg - angleDelta).coerceIn(160f, 179f),
            kneeAcceptableDeg = (base.kneeAcceptableDeg - angleDelta).coerceIn(150f, 175f),
            kneeSoftDeg = (base.kneeSoftDeg - angleDelta).coerceIn(140f, 170f),
            shoulderExcellentMinDeg = (base.shoulderExcellentMinDeg - angleDelta).coerceIn(160f, 180f),
            shoulderExcellentMaxDeg = (base.shoulderExcellentMaxDeg + angleDelta).coerceIn(180f, 190f),
            shoulderAcceptableMinDeg = (base.shoulderAcceptableMinDeg - angleDelta).coerceIn(150f, 175f),
            shoulderLimitedMinDeg = (base.shoulderLimitedMinDeg - angleDelta).coerceIn(140f, 170f),
            hipLineExcellentMinDeg = (base.hipLineExcellentMinDeg - angleDelta).coerceIn(155f, 175f),
            hipLineExcellentMaxDeg = (base.hipLineExcellentMaxDeg + angleDelta).coerceIn(175f, 185f),
            hipLineAcceptableMinDeg = (base.hipLineAcceptableMinDeg - angleDelta).coerceIn(145f, 170f),
            kneeGoodDeg = (base.kneeGoodDeg - angleDelta).coerceIn(160f, 180f),
            kneeWarnDeg = (base.kneeWarnDeg - angleDelta).coerceIn(150f, 175f),
            lockoutDeg = (base.lockoutDeg - angleDelta).coerceIn(160f, 180f),
            lockoutWarnDeg = (base.lockoutWarnDeg - angleDelta).coerceIn(150f, 175f),
            elbowBottomFullDepthMinDeg = (base.elbowBottomFullDepthMinDeg + angleDelta).coerceIn(70f, 90f),
            elbowBottomFullDepthMaxDeg = (base.elbowBottomFullDepthMaxDeg + angleDelta).coerceIn(90f, 110f),
            elbowBottomCollapseDeg = (base.elbowBottomCollapseDeg + angleDelta).coerceIn(65f, 85f),
        )
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
        "line_quality" to scoreLowerIsBetter(
            abs(m.bodyLineDeviationNorm),
            p.stackExcellentNorm,
            p.stackAcceptableNorm,
            poorThreshold = p.stackAcceptableNorm,
        ),
        "shoulder_openness" to scoreShoulderOpenness(m.jointAngles["shoulder_angle_proxy"] ?: p.shoulderAcceptableMinDeg, p),
        "scapular_elevation" to m.scapularElevationProxyScore,
        "rib_pelvis_control" to (100 - m.bananaProxyScore).coerceIn(0, 100),
        "leg_tension" to scoreHigherIsBetter(m.jointAngles["knee_angle"] ?: 180f, p.kneeExcellentDeg, p.kneeAcceptableDeg, p.kneeSoftDeg),
        "elbow_lock" to scoreHigherIsBetter(m.jointAngles["elbow_angle"] ?: 180f, p.elbowExcellentDeg, p.elbowAcceptableDeg, p.elbowSoftDeg),
        "hip_line" to scoreHipLine(m.jointAngles["hip_angle"] ?: 170f, p),
    )

    private fun pushUp(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "torso_line" to scoreFromDeviation(m.bodyLineDeviationNorm, p.bodyLineGoodNorm, p.bodyLinePoorNorm),
        "descent_quality" to scoreTempo(m, p),
        "bottom_depth_quality" to scoreBottomDepthQuality(m, p),
        "ascent_quality" to scoreAscentQuality(m),
        "top_lockout_quality" to scoreTopLockout(m, p),
        "flare_stability_quality" to scoreFlareStability(m),
    )

    private fun pike(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "hip_height" to scoreHipHeight(m, p),
        "shoulder_loading" to scoreFromDeviation(abs(m.stackOffsetsNorm["shoulder_stack_offset"] ?: 0f), p.stackExcellentNorm, p.stackPoorNorm),
        "head_path" to scoreHeadPath(m, p),
        "descent_quality" to scoreTempo(m, p),
        "bottom_depth_quality" to scoreBottomDepthQuality(m, p),
        "ascent_quality" to scoreAscentQuality(m),
        "top_lockout_quality" to scoreTopLockout(m, p),
        "flare_stability_quality" to scoreFlareStability(m),
    )

    private fun elevatedPike(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "loading_angle" to scoreFromDeviation(abs(m.stackOffsetsNorm["shoulder_stack_offset"] ?: 0f), p.stackExcellentNorm * 0.85f, p.stackPoorNorm),
        "pressing_path" to ((1f - (m.pathMetrics["path_variance"] ?: 0.4f)) * 100f).toInt().coerceIn(0, 100),
        "descent_quality" to scoreTempo(m, p),
        "bottom_depth_quality" to scoreBottomDepthQuality(m, p),
        "ascent_quality" to scoreAscentQuality(m),
        "top_lockout_quality" to scoreTopLockout(m, p),
        "flare_stability_quality" to scoreFlareStability(m),
    )

    private fun negative(m: DerivedMetrics, p: DrillThresholdProfile): Map<String, Int> = mapOf(
        "line_retention" to scoreFromDeviation(m.bodyLineDeviationNorm, p.bodyLineGoodNorm, p.bodyLinePoorNorm),
        "path_consistency" to ((1f - (m.pathMetrics["path_variance"] ?: 0.4f)) * 100f).toInt().coerceIn(0, 100),
        "descent_quality" to scoreTempo(m, p),
        "bottom_depth_quality" to scoreBottomDepthQuality(m, p),
        "ascent_quality" to scoreAscentQuality(m),
        "top_lockout_quality" to scoreTopLockout(m, p),
        "flare_stability_quality" to scoreFlareStability(m),
    )

    private fun scoreFromDeviation(value: Float, good: Float, poor: Float): Int = when {
        value <= good -> 100
        value >= poor -> 25
        else -> (100 - ((value - good) / (poor - good) * 75f)).toInt().coerceIn(25, 99)
    }

    private fun scoreLowerIsBetter(value: Float, excellentMax: Float, acceptableMax: Float, poorThreshold: Float): Int = when {
        value <= excellentMax -> 100
        value <= acceptableMax -> 80
        value <= poorThreshold -> 55
        else -> 25
    }

    private fun scoreHigherIsBetter(value: Float, excellentMin: Float, acceptableMin: Float, warningMin: Float): Int = when {
        value >= excellentMin -> 100
        value >= acceptableMin -> 80
        value >= warningMin -> 55
        else -> 25
    }

    private fun scoreShoulderOpenness(value: Float, p: DrillThresholdProfile): Int = when {
        value in p.shoulderExcellentMinDeg..p.shoulderExcellentMaxDeg -> 100
        value >= p.shoulderAcceptableMinDeg -> 80
        value >= p.shoulderLimitedMinDeg -> 55
        else -> 25
    }

    private fun scoreHipLine(value: Float, p: DrillThresholdProfile): Int = when {
        value in p.hipLineExcellentMinDeg..p.hipLineExcellentMaxDeg -> 100
        value >= p.hipLineAcceptableMinDeg -> 80
        else -> 25
    }

    private fun scoreHipHeight(m: DerivedMetrics, p: DrillThresholdProfile): Int {
        val h = m.pathMetrics["hip_above_shoulder_norm"] ?: 0f
        return (h / p.hipAboveShoulderNormMin * 100f).toInt().coerceIn(0, 100)
    }

    private fun scoreHeadPath(m: DerivedMetrics, p: DrillThresholdProfile): Int {
        val f = abs(m.pathMetrics["head_forward_norm"] ?: 0f)
        return if (f <= p.headForwardNormMax) 100 else (100 - ((f - p.headForwardNormMax) * 140f).toInt()).coerceIn(25, 100)
    }

    private fun scoreBottomDepthQuality(m: DerivedMetrics, p: DrillThresholdProfile): Int {
        val elbowAngle = m.jointAngles["elbow_angle"] ?: p.elbowBottomFullDepthMaxDeg
        val depthNorm = m.pathMetrics["depth_norm"] ?: 0.5f
        val elbowBandScore = when {
            elbowAngle in p.elbowBottomFullDepthMinDeg..p.elbowBottomFullDepthMaxDeg -> 100
            elbowAngle > p.elbowBottomFullDepthMaxDeg -> 55
            elbowAngle < p.elbowBottomCollapseDeg -> 55
            else -> 80
        }
        val depthBandScore = when {
            depthNorm >= 0.55f -> 100
            depthNorm >= 0.45f -> 80
            depthNorm >= 0.35f -> 55
            else -> 25
        }
        return ((elbowBandScore * 0.7f) + (depthBandScore * 0.3f)).toInt().coerceIn(25, 100)
    }

    private fun scoreAscentQuality(m: DerivedMetrics): Int {
        val ascent = m.tempoMetrics["ascent_sec"] ?: return 80
        return scoreByBands(
            ascent,
            listOf(
                ScoreBand(minInclusive = 0.55f, maxInclusive = 1.5f, score = 100, label = "excellent"),
                ScoreBand(minInclusive = 0.4f, maxInclusive = 1.8f, score = 80, label = "acceptable"),
                ScoreBand(minInclusive = 0.25f, maxInclusive = 2.2f, score = 55, label = "warning"),
                ScoreBand(score = 25, label = "poor"),
            ),
        )
    }

    private fun scoreTopLockout(m: DerivedMetrics, p: DrillThresholdProfile): Int {
        val elbowAngle = m.jointAngles["elbow_angle"] ?: 0f
        return when {
            elbowAngle >= p.lockoutDeg -> 100
            elbowAngle >= p.lockoutWarnDeg -> 55
            else -> 25
        }
    }

    private fun scoreFlareStability(m: DerivedMetrics): Int {
        val flare = m.pathMetrics["elbow_flare_proxy"] ?: 0f
        val variance = m.pathMetrics["path_variance"] ?: 0.2f
        return when {
            flare <= 0.10f && variance <= 0.12f -> 100
            flare <= 0.18f && variance <= 0.20f -> 80
            flare <= 0.25f && variance <= 0.30f -> 55
            else -> 25
        }
    }

    private fun scoreTempo(m: DerivedMetrics, p: DrillThresholdProfile): Int {
        val descent = m.tempoMetrics["descent_sec"] ?: return 80
        return when {
            descent >= p.descentGoodSec -> 100
            descent >= p.descentAcceptableSec -> 80
            descent < p.descentPoorSec -> 25
            else -> 55
        }
    }

    private fun scoreByBands(value: Float, bands: List<ScoreBand>): Int {
        val match = bands.firstOrNull { band ->
            val minOk = band.minInclusive?.let { value >= it } ?: true
            val maxOk = band.maxInclusive?.let { value <= it } ?: true
            minOk && maxOk
        } ?: bands.last()
        return match.score
    }

    private fun rawMetricValue(metricKey: String, m: DerivedMetrics): Float = when (metricKey) {
        "line_quality", "torso_line", "line_retention" -> m.bodyLineDeviationNorm
        "bottom_depth_quality" -> m.jointAngles["elbow_angle"] ?: 0f
        "top_lockout_quality" -> m.jointAngles["elbow_angle"] ?: 0f
        "descent_quality" -> m.tempoMetrics["descent_sec"] ?: 0f
        "ascent_quality" -> m.tempoMetrics["ascent_sec"] ?: 0f
        "head_path" -> m.pathMetrics["head_forward_norm"] ?: 0f
        "flare_stability_quality" -> m.pathMetrics["elbow_flare_proxy"] ?: 0f
        else -> m.stackOffsetsNorm["shoulder_stack_offset"] ?: 0f
    }

    private fun thresholdBand(metricKey: String, rawValue: Float, p: DrillThresholdProfile): String = when (metricKey) {
        "line_quality", "torso_line", "line_retention" -> when {
            rawValue <= p.bodyLineGoodNorm -> "good"
            rawValue <= p.bodyLineWarnNorm -> "warn"
            else -> "poor"
        }
        "descent_quality" -> when {
            rawValue >= p.descentGoodSec -> "good"
            rawValue >= p.descentAcceptableSec -> "acceptable"
            rawValue < p.descentPoorSec -> "poor"
            else -> "warn"
        }
        "top_lockout_quality" -> when {
            rawValue >= p.lockoutDeg -> "excellent"
            rawValue >= p.lockoutWarnDeg -> "warning"
            else -> "incomplete"
        }
        "bottom_depth_quality" -> when {
            rawValue in p.elbowBottomFullDepthMinDeg..p.elbowBottomFullDepthMaxDeg -> "full_depth"
            rawValue > p.elbowBottomFullDepthMaxDeg -> "shallow"
            rawValue < p.elbowBottomCollapseDeg -> "collapse"
            else -> "warning"
        }
        else -> "derived"
    }
}
