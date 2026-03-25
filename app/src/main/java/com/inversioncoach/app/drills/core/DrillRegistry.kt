package com.inversioncoach.app.drills.core

import com.inversioncoach.app.biomechanics.ConfiguredDrillAnalyzer
import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.biomechanics.DrillModeConfig
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.motion.DrillCatalog
import com.inversioncoach.app.motion.MovementPattern

class DrillRegistry(
    private val biomechanicalAnalyzerFactory: (DrillModeConfig) -> com.inversioncoach.app.biomechanics.DrillAnalyzer = { config ->
        ConfiguredDrillAnalyzer(config)
    },
) {
    private val analyzerCache = mutableMapOf<DrillType, com.inversioncoach.app.biomechanics.DrillAnalyzer>()

    fun definitionFor(drillType: DrillType): DrillDefinition {
        val config = DrillConfigs.byTypeOrNull(drillType)
        return DrillDefinition(
            drillType = drillType,
            movementType = DrillCatalog.byType(drillType).movementPattern.toMovementType(),
            viewRequirement = if (config?.sideViewPrimary == false) ViewRequirement.ANY else ViewRequirement.SIDE_VIEW,
        )
    }

    fun analyzerFor(config: DrillModeConfig): com.inversioncoach.app.biomechanics.DrillAnalyzer {
        return analyzerCache.getOrPut(config.type) { biomechanicalAnalyzerFactory(config) }
    }

    fun analyzerFor(drillType: DrillType): com.inversioncoach.app.biomechanics.DrillAnalyzer {
        return analyzerFor(DrillConfigs.requireByType(drillType))
    }
}

private fun MovementPattern.toMovementType(): MovementType = when (this) {
    MovementPattern.VERTICAL_PUSH -> MovementType.VERTICAL_PUSH
    else -> MovementType.VERTICAL_PUSH
}
