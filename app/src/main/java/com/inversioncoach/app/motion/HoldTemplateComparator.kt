package com.inversioncoach.app.motion

import com.inversioncoach.app.calibration.HoldTemplate
import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.calibration.hold.HoldMetricExtractor
import com.inversioncoach.app.model.PoseFrame
import kotlin.math.abs

class HoldTemplateComparator(
    private val extractor: HoldMetricExtractor = HoldMetricExtractor(),
) {
    fun similarityScore(
        frame: PoseFrame,
        template: HoldTemplate,
        bodyProfile: UserBodyProfile?,
    ): Int {
        val snapshot = extractor.extract(frame, bodyProfile)
        val scored = template.metrics.mapNotNull { metric ->
            val actual = snapshot.values[metric.metricKey] ?: return@mapNotNull null
            metric.weight to scoreMetric(
                actual = actual,
                target = metric.targetValue,
                goodTolerance = metric.goodTolerance,
                warnTolerance = metric.warnTolerance,
            )
        }

        if (scored.isEmpty()) return 0
        val totalWeight = scored.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        val weightedScore = scored.sumOf { (weight, score) ->
            (weight * score).toDouble()
        }.toFloat() / totalWeight
        return weightedScore.toInt().coerceIn(0, 100)
    }

    private fun scoreMetric(
        actual: Float,
        target: Float,
        goodTolerance: Float,
        warnTolerance: Float,
    ): Float {
        val diff = abs(actual - target)
        return when {
            diff <= goodTolerance -> 100f
            diff <= warnTolerance -> 75f
            else -> 40f
        }
    }
}
