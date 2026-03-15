package com.inversioncoach.app.biomechanics

class ConfiguredDrillAnalyzer(
    config: DrillModeConfig,
) : BaseDrillAnalyzer(
    drillType = config.type,
    calibration = config.calibration,
)
