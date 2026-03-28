package com.inversioncoach.app.drills

import com.inversioncoach.app.model.CalibrationConfigRecord
import com.inversioncoach.app.model.DrillDefinitionRecord
import org.json.JSONObject

object DrillSeeder {
    fun seedDrills(nowMs: Long): List<DrillDefinitionRecord> = listOf(
        DrillDefinitionRecord(
            id = "seed_free_handstand",
            name = "Free Handstand",
            description = "Seeded baseline free handstand drill.",
            movementMode = DrillMovementMode.HOLD,
            cameraView = DrillCameraView.FREESTYLE,
            phaseSchemaJson = "setup|stack|hold",
            keyJointsJson = "shoulders|hips|ankles",
            normalizationBasisJson = "hips",
            cueConfigJson = "legacyDrillType:FREE_HANDSTAND",
            sourceType = DrillSourceType.SEEDED,
            status = DrillStatus.READY,
            version = 1,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        ),
        DrillDefinitionRecord(
            id = "seed_wall_handstand",
            name = "Wall Handstand",
            description = "Seeded wall handstand drill.",
            movementMode = DrillMovementMode.HOLD,
            cameraView = DrillCameraView.LEFT,
            phaseSchemaJson = "setup|stack|hold",
            keyJointsJson = "shoulders|hips|ankles",
            normalizationBasisJson = "hips",
            cueConfigJson = "legacyDrillType:WALL_HANDSTAND",
            sourceType = DrillSourceType.SEEDED,
            status = DrillStatus.READY,
            version = 1,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        ),
    )

    fun seedCalibration(nowMs: Long): List<CalibrationConfigRecord> = listOf(
        CalibrationConfigRecord(
            id = "seed_calibration_free_handstand",
            drillId = "seed_free_handstand",
            displayName = "Default Free Handstand Calibration",
            configJson = JSONObject().apply {
                put("angleThreshold", 12)
                put("stabilityTolerance", 0.08)
                put("scoreWeightAlignment", 0.35)
                put("scoreWeightTiming", 0.4)
                put("scoreWeightStability", 0.25)
            }.toString(),
            scoringVersion = 1,
            featureVersion = 1,
            isActive = true,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        ),
        CalibrationConfigRecord(
            id = "seed_calibration_wall_handstand",
            drillId = "seed_wall_handstand",
            displayName = "Default Wall Handstand Calibration",
            configJson = JSONObject().apply {
                put("angleThreshold", 10)
                put("stabilityTolerance", 0.06)
                put("scoreWeightAlignment", 0.35)
                put("scoreWeightTiming", 0.4)
                put("scoreWeightStability", 0.25)
            }.toString(),
            scoringVersion = 1,
            featureVersion = 1,
            isActive = true,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        ),
    )
}
