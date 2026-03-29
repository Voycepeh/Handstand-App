package com.inversioncoach.app.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillStudioCodecTest {
    @Test
    fun seededRoundTrip_preservesEditableFields() {
        val seeded = DrillStudioCodec.fromSeeded(DrillCatalog.all.first()).copy(
            progressWindow = DrillStudioPhaseWindow(0.2f, 0.8f),
            metricThresholds = mapOf("tempoMinSec" to 0.7f),
        )

        val decoded = DrillStudioCodec.fromJson(DrillStudioCodec.toJson(seeded))

        assertEquals(0.2f, decoded.progressWindow.start)
        assertEquals(0.8f, decoded.progressWindow.end)
        assertEquals(0.7f, decoded.metricThresholds["tempoMinSec"])
        assertTrue(decoded.animationSpec.keyframes.isNotEmpty())
    }
}
