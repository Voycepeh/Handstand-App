package com.inversioncoach.app.calibration.rep

import com.inversioncoach.app.calibration.RepTemplate
import com.inversioncoach.app.calibration.RepTemplateSource
import com.inversioncoach.app.calibration.TemporalMetricProfile
import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Test

class RepTemplateBlenderTest {
    @Test
    fun blendedMeanSeriesUsesConfiguredWeight() {
        val baseline = RepTemplate(
            drillType = DrillType.PIKE_PUSH_UP,
            profileVersion = 1,
            temporalMetrics = listOf(
                TemporalMetricProfile("elbow_flexion", listOf(10f, 20f), listOf(1f, 1f)),
            ),
            expectedRepFrames = 20,
            minRomThreshold = null,
            source = RepTemplateSource.DEFAULT_BASELINE,
        )
        val learned = baseline.copy(
            temporalMetrics = listOf(
                TemporalMetricProfile("elbow_flexion", listOf(30f, 40f), listOf(2f, 2f)),
            ),
            source = RepTemplateSource.CLEAN_REP_CAPTURE,
        )

        val blended = RepTemplateBlender().blend(baseline, learned, learnedWeight = 0.25f)

        assertEquals(listOf(15f, 25f), blended.temporalMetrics.first().meanSeries)
        assertEquals(RepTemplateSource.BLENDED, blended.source)
    }
}
