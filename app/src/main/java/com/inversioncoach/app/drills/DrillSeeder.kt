package com.inversioncoach.app.drills

import com.inversioncoach.app.model.CalibrationConfigRecord
import com.inversioncoach.app.model.DrillDefinitionRecord
import org.json.JSONObject

object DrillSeeder {
    private const val CURRENT_SEED_VERSION = 2

    data class SeedCatalogEntry(
        val id: String,
        val name: String,
        val description: String,
        val movementMode: String,
        val cameraView: String,
        val phaseSchemaJson: String,
        val keyJointsJson: String,
        val normalizationBasisJson: String,
        val cueConfigJson: String,
        val status: String = DrillStatus.READY,
        val version: Int = CURRENT_SEED_VERSION,
    ) {
        fun toRecord(nowMs: Long): DrillDefinitionRecord = DrillDefinitionRecord(
            id = id,
            name = name,
            description = description,
            movementMode = movementMode,
            cameraView = cameraView,
            phaseSchemaJson = phaseSchemaJson,
            keyJointsJson = keyJointsJson,
            normalizationBasisJson = normalizationBasisJson,
            cueConfigJson = cueConfigJson,
            sourceType = DrillSourceType.SEEDED,
            status = status,
            version = version,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
    }

    val seedCatalog: List<SeedCatalogEntry> = listOf(
        // Push
        seededRep("seed_push_up", "Push-up", "Classic horizontal pressing strength drill."),
        seededRep("seed_bar_dip", "Bar Dip", "Vertical pressing dip with shoulder and triceps focus."),
        seededRep("seed_pike_push_up", "Pike Push Up", "Shoulder-dominant pressing pattern.", cameraView = DrillCameraView.LEFT),
        seededRep("seed_elevated_pike_push_up", "Elevated Pike Push Up", "Progressed pike push-up with feet elevated.", cameraView = DrillCameraView.LEFT),
        // Pull
        seededRep("seed_pull_up", "Pull-up", "Vertical pulling movement for upper body pulling strength."),
        seededRep("seed_inverted_row", "Inverted Row", "Horizontal pulling movement using bodyweight."),
        // Legs
        seededRep("seed_bodyweight_squat", "Bodyweight Squat", "Foundational bilateral squat pattern."),
        seededRep("seed_forward_lunge", "Forward Lunge", "Single-leg pattern emphasizing control and stability."),
        seededRep("seed_pistol_squat", "Pistol Squat", "Advanced single-leg squat requiring balance and strength."),
        // Core
        seededHold("seed_front_plank", "Front Plank", "Isometric core brace with neutral spine."),
        seededHold("seed_side_plank", "Side Plank", "Lateral core stability hold.", keyJointsJson = "shoulders|hips|ankles"),
        seededRep("seed_leg_raise", "Leg Raise", "Anterior core flexion and hip flexor control drill."),
        seededHold("seed_hollow_hold", "Hollow Hold", "Gymnastics hollow-body bracing position."),
        // Inversion (preserve legacy IDs for upgrade reconciliation)
        seededHold(
            id = "seed_free_handstand",
            name = "Handstand Hold",
            description = "Freestanding handstand stability hold.",
            cameraView = DrillCameraView.FREESTYLE,
            cueConfigJson = seededCueConfig("seed_free_handstand", "legacyDrillType:FREE_HANDSTAND", "comparisonMode:POSE_TIMELINE"),
        ),
        seededRep(
            id = "seed_wall_handstand",
            name = "Handstand Push Up",
            description = "Inverted pressing drill for handstand push-up progression.",
            cameraView = DrillCameraView.LEFT,
            cueConfigJson = seededCueConfig("seed_wall_handstand", "legacyDrillType:WALL_HANDSTAND_PUSH_UP", "comparisonMode:POSE_TIMELINE"),
        ),
    )

    fun seedDrills(nowMs: Long): List<DrillDefinitionRecord> = seedCatalog.map { it.toRecord(nowMs) }

    fun reconcileSeededDrills(
        existing: List<DrillDefinitionRecord>,
        nowMs: Long,
    ): List<DrillDefinitionRecord> {
        val existingById = existing.associateBy { it.id }
        return seedCatalog.mapNotNull { seed ->
            val seededRecord = seed.toRecord(nowMs)
            val current = existingById[seed.id]
            when {
                current == null -> seededRecord
                current.sourceType != DrillSourceType.SEEDED -> null
                shouldUpdateSeededRecord(current, seededRecord) -> seededRecord.copy(createdAtMs = current.createdAtMs)
                else -> null
            }
        }
    }

    private fun shouldUpdateSeededRecord(
        current: DrillDefinitionRecord,
        seeded: DrillDefinitionRecord,
    ): Boolean =
        current.name != seeded.name ||
            current.description != seeded.description ||
            current.movementMode != seeded.movementMode ||
            current.cameraView != seeded.cameraView ||
            current.phaseSchemaJson != seeded.phaseSchemaJson ||
            current.keyJointsJson != seeded.keyJointsJson ||
            current.normalizationBasisJson != seeded.normalizationBasisJson ||
            current.cueConfigJson != seeded.cueConfigJson ||
            current.status != seeded.status ||
            current.version != seeded.version

    private fun seededRep(
        id: String,
        name: String,
        description: String,
        cameraView: String = DrillCameraView.LEFT,
        phaseSchemaJson: String = "setup|eccentric|concentric",
        keyJointsJson: String = "shoulders|hips|knees",
        normalizationBasisJson: String = "hips",
        cueConfigJson: String = seededCueConfig(id, "comparisonMode:POSE_TIMELINE"),
    ): SeedCatalogEntry = SeedCatalogEntry(
        id = id,
        name = name,
        description = description,
        movementMode = DrillMovementMode.REP,
        cameraView = cameraView,
        phaseSchemaJson = phaseSchemaJson,
        keyJointsJson = keyJointsJson,
        normalizationBasisJson = normalizationBasisJson,
        cueConfigJson = cueConfigJson,
    )

    private fun seededHold(
        id: String,
        name: String,
        description: String,
        cameraView: String = DrillCameraView.LEFT,
        phaseSchemaJson: String = "setup|stack|hold",
        keyJointsJson: String = "shoulders|hips|ankles",
        normalizationBasisJson: String = "hips",
        cueConfigJson: String = seededCueConfig(id, "comparisonMode:POSE_TIMELINE"),
    ): SeedCatalogEntry = SeedCatalogEntry(
        id = id,
        name = name,
        description = description,
        movementMode = DrillMovementMode.HOLD,
        cameraView = cameraView,
        phaseSchemaJson = phaseSchemaJson,
        keyJointsJson = keyJointsJson,
        normalizationBasisJson = normalizationBasisJson,
        cueConfigJson = cueConfigJson,
    )

    private fun seededCueConfig(seedKey: String, vararg values: String): String =
        (listOf("seedKey:$seedKey", "seedSource:system") + values.toList()).joinToString("|")

    fun seedCalibration(nowMs: Long): List<CalibrationConfigRecord> = listOf(
        CalibrationConfigRecord(
            id = "seed_calibration_free_handstand",
            drillId = "seed_free_handstand",
            displayName = "Default Handstand Hold Calibration",
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
            displayName = "Default Handstand Push Up Calibration",
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
