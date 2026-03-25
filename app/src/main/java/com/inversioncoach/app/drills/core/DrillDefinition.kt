package com.inversioncoach.app.drills.core

import com.inversioncoach.app.model.DrillType

data class DrillDefinition(
    val drillType: DrillType,
    val movementType: MovementType,
    val viewRequirement: ViewRequirement,
)
