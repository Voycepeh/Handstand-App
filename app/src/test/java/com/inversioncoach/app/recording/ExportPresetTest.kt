package com.inversioncoach.app.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportPresetTest {
    @Test
    fun presetDefaultsMatchExpectedPerformanceProfiles() {
        assertEquals(24, ExportPreset.FAST.outputFps)
        assertEquals(720, ExportPreset.BALANCED.targetHeight)
        assertEquals(1080, ExportPreset.HIGH_QUALITY.targetHeight)
    }
}
