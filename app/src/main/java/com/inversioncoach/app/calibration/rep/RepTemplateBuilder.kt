package com.inversioncoach.app.calibration.rep

import com.inversioncoach.app.calibration.RepTemplate
import com.inversioncoach.app.calibration.RepTemplateSource
import com.inversioncoach.app.calibration.TemporalMetricProfile
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.PoseFrame
import kotlin.math.abs

class RepTemplateBuilder(
    private val extractor: RepMetricExtractor = RepMetricExtractor(),
    private val normalizer: RepTimeNormalizer = RepTimeNormalizer(),
) {
    fun build(
        drillType: DrillType,
        profileVersion: Int,
        reps: List<List<PoseFrame>>,
    ): RepTemplate? {
        if (reps.isEmpty()) return null

        val repSnapshots = reps.map { rep -> rep.map { extractor.extract(it) } }
        val metricKeys = repSnapshots.flatMap { rep -> rep.flatMap { it.values.keys } }.distinct()

        val temporalMetrics = metricKeys.mapNotNull { key ->
            val normalizedSeries = repSnapshots.mapNotNull { rep ->
                val values = rep.mapNotNull { it.values[key] }
                if (values.isEmpty()) null else normalizer.normalize(values)
            }
            if (normalizedSeries.isEmpty()) return@mapNotNull null

            val meanSeries = List(normalizedSeries.first().size) { idx ->
                normalizedSeries.map { it[idx] }.average().toFloat()
            }
            val toleranceSeries = List(normalizedSeries.first().size) { idx ->
                val vals = normalizedSeries.map { it[idx] }
                val mean = vals.average().toFloat()
                vals.map { abs(it - mean) }.average().toFloat().coerceAtLeast(0.05f)
            }

            TemporalMetricProfile(
                metricKey = key,
                meanSeries = meanSeries,
                toleranceSeries = toleranceSeries,
            )
        }

        if (temporalMetrics.isEmpty()) return null

        return RepTemplate(
            drillType = drillType,
            profileVersion = profileVersion,
            temporalMetrics = temporalMetrics,
            expectedRepFrames = repSnapshots.map { it.size }.average().toInt().coerceAtLeast(1),
            minRomThreshold = null,
            source = RepTemplateSource.CLEAN_REP_CAPTURE,
        )
    }
}
