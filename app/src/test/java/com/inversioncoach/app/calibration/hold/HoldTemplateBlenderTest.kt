package com.inversioncoach.app.calibration.hold

import com.inversioncoach.app.calibration.HoldMetricTemplate
import com.inversioncoach.app.calibration.HoldTemplate
import com.inversioncoach.app.calibration.HoldTemplateSource
import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldTemplateBlenderTest {
    private val blender = HoldTemplateBlender()

    @Test
    fun blendsBaselineAndLearnedMetricsCorrectly() {
        val baseline = HoldTemplate(
            drillType = DrillType.FREE_HANDSTAND,
            profileVersion = 1,
            metrics = listOf(HoldMetricTemplate("wrist_shoulder_offset", 0.05f, 0.04f, 0.08f, 1f)),
            minStableDurationMs = 2000L,
            source = HoldTemplateSource.DEFAULT_BASELINE,
        )
        val learned = baseline.copy(
            profileVersion = 2,
            metrics = listOf(HoldMetricTemplate("wrist_shoulder_offset", 0.10f, 0.04f, 0.08f, 1f)),
            source = HoldTemplateSource.STABLE_HOLD_CAPTURE,
        )

        val blended = blender.blend(baseline, learned, learnedWeight = 0.3f)

        assertEquals(HoldTemplateSource.BLENDED, blended.source)
        assertEquals(2, blended.profileVersion)
        assertEquals(0.065f, blended.metrics.first().targetValue, 0.0001f)
        assertTrue(blended.metrics.first().goodTolerance == baseline.metrics.first().goodTolerance)
    }
}
