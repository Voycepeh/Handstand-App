package com.inversioncoach.app.drills

import com.inversioncoach.app.model.DrillDefinitionRecord

object DrillDefinitionValidator {
    fun validate(record: DrillDefinitionRecord): List<String> {
        val errors = mutableListOf<String>()
        if (record.name.isBlank()) errors += "Name is required."
        if (record.movementMode !in setOf(DrillMovementMode.REP, DrillMovementMode.HOLD)) {
            errors += "Movement mode must be REP or HOLD."
        }
        if (record.cameraView !in setOf(
                DrillCameraView.LEFT,
                DrillCameraView.RIGHT,
                DrillCameraView.FRONT,
                DrillCameraView.BACK,
                DrillCameraView.SIDE,
                DrillCameraView.FREESTYLE,
            )
        ) {
            errors += "Camera view is required and must be valid."
        }
        val hasPhase = record.phaseSchemaJson.split('|').any { it.isNotBlank() }
        if (!hasPhase) errors += "At least one phase is required."
        val hasJoint = record.keyJointsJson.split('|').any { it.isNotBlank() }
        if (!hasJoint) errors += "At least one key joint is required."
        if (record.normalizationBasisJson.isBlank()) errors += "Normalization basis is required."
        return errors
    }
}
