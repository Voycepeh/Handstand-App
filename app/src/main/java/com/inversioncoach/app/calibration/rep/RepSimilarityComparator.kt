package com.inversioncoach.app.calibration.rep

import com.inversioncoach.app.calibration.RepTemplate
import com.inversioncoach.app.model.PoseFrame
import kotlin.math.abs

class RepSimilarityComparator(
    private val extractor: RepMetricExtractor = RepMetricExtractor(),
    private val normalizer: RepTimeNormalizer = RepTimeNormalizer(),
) {
    fun similarityScore(
        repFrames: List<PoseFrame>,
        template: RepTemplate,
    ): Int {
        if (repFrames.isEmpty()) return 0
        val snapshots = repFrames.map { extractor.extract(it) }
        val scores = template.temporalMetrics.mapNotNull { metric ->
            val actual = snapshots.mapNotNull { it.values[metric.metricKey] }
            if (actual.isEmpty()) return@mapNotNull null
            val normalizedActual = normalizer.normalize(actual)

            val comparedLength = minOf(
                normalizedActual.size,
                metric.meanSeries.size,
                metric.toleranceSeries.size,
            )
            if (comparedLength <= 0) return@mapNotNull null

            val pointScores = List(comparedLength) { idx ->
                val a = normalizedActual[idx]
                val mean = metric.meanSeries[idx]
                val tolerance = metric.toleranceSeries[idx].coerceAtLeast(0.01f)
                val diff = abs(a - mean)
                when {
                    diff <= tolerance -> 100f
                    diff <= tolerance * 2f -> 75f
                    else -> 40f
                }
            }
            pointScores.average().toFloat()
        }

        if (scores.isEmpty()) return 0
        return scores.average().toInt().coerceIn(0, 100)
    }
}
