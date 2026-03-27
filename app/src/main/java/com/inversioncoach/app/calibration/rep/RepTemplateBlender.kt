package com.inversioncoach.app.calibration.rep

import com.inversioncoach.app.calibration.RepTemplate
import com.inversioncoach.app.calibration.RepTemplateSource
import com.inversioncoach.app.calibration.TemporalMetricProfile

class RepTemplateBlender {
    fun blend(baseline: RepTemplate, learned: RepTemplate, learnedWeight: Float = 0.3f): RepTemplate {
        val clampedWeight = learnedWeight.coerceIn(0f, 1f)
        val baselineByKey = baseline.temporalMetrics.associateBy { it.metricKey }
        val learnedByKey = learned.temporalMetrics.associateBy { it.metricKey }

        val merged = baselineByKey.keys.union(learnedByKey.keys).mapNotNull { key ->
            val base = baselineByKey[key]
            val learnedMetric = learnedByKey[key]
            when {
                base != null && learnedMetric != null -> {
                    val seriesLength = minOf(base.meanSeries.size, learnedMetric.meanSeries.size)
                    if (seriesLength <= 0) {
                        null
                    } else {
                        TemporalMetricProfile(
                            metricKey = key,
                            meanSeries = List(seriesLength) { idx ->
                                val a = base.meanSeries[idx]
                                val b = learnedMetric.meanSeries[idx]
                                a * (1f - clampedWeight) + b * clampedWeight
                            },
                            toleranceSeries = List(seriesLength) { idx ->
                                base.toleranceSeries.getOrNull(idx)
                                    ?: learnedMetric.toleranceSeries.getOrNull(idx)
                                    ?: 0.05f
                            },
                        )
                    }
                }
                base != null -> base
                learnedMetric != null -> learnedMetric
                else -> null
            }
        }

        return baseline.copy(
            temporalMetrics = merged,
            source = RepTemplateSource.BLENDED,
        )
    }
}
