package com.inversioncoach.app.drills.core

import com.inversioncoach.app.biomechanics.DrillAnalyzer as LegacyAnalyzer
import com.inversioncoach.app.biomechanics.DrillModeConfig
import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertSame
import org.junit.Test

class DrillRegistryTest {
    @Test
    fun analyzerFor_config_returnsSameCachedAnalyzerForType() {
        val created = mutableMapOf<DrillType, LegacyAnalyzer>()
        val registry = DrillRegistry(
            biomechanicalAnalyzerFactory = { config ->
                created.getOrPut(config.type) {
                    object : LegacyAnalyzer {
                        override fun analyzeFrame(frame: com.inversioncoach.app.model.PoseFrame) = null
                        override fun finalizeRep(timestampMs: Long) = null
                        override fun finalizeSession(): com.inversioncoach.app.biomechanics.SessionAnalysis {
                            error("not needed")
                        }
                    }
                }
            },
        )

        val firstConfig = DrillModeConfig(
            type = DrillType.FREE_HANDSTAND,
            label = "test",
            metrics = emptyList(),
            faults = emptyMap(),
            cuePriority = emptyList(),
            calibration = com.inversioncoach.app.biomechanics.DrillProfiles.forDrill(DrillType.FREE_HANDSTAND, emptyList()),
        )
        val secondConfig = firstConfig.copy(label = "test-2")

        val first = registry.analyzerFor(firstConfig)
        val second = registry.analyzerFor(secondConfig)

        assertSame(first, second)
    }
}
