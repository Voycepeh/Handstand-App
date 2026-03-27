package com.inversioncoach.app.calibration.hold

import com.inversioncoach.app.calibration.HoldMetricTemplate
import com.inversioncoach.app.calibration.HoldTemplate
import com.inversioncoach.app.calibration.HoldTemplateSource

class HoldTemplateBlender {
    fun blend(baseline: HoldTemplate, learned: HoldTemplate, learnedWeight: Float = 0.3f): HoldTemplate {
        val baselineByKey = baseline.metrics.associateBy { it.metricKey }
        val learnedByKey = learned.metrics.associateBy { it.metricKey }

        val merged = baselineByKey.keys.union(learnedByKey.keys).mapNotNull { key ->
            val base = baselineByKey[key]
            val learnedMetric = learnedByKey[key]
            when {
                base != null && learnedMetric != null -> HoldMetricTemplate(
                    metricKey = key,
                    targetValue = base.targetValue * (1f - learnedWeight) + learnedMetric.targetValue * learnedWeight,
                    goodTolerance = base.goodTolerance,
                    warnTolerance = base.warnTolerance,
                    weight = base.weight,
                )

                base != null -> base
                learnedMetric != null -> learnedMetric
                else -> null
            }
        }

        return baseline.copy(
            profileVersion = maxOf(baseline.profileVersion, learned.profileVersion),
            metrics = merged,
            source = HoldTemplateSource.BLENDED,
        )
    }
}
