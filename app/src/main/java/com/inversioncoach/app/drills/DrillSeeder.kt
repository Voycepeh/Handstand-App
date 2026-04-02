package com.inversioncoach.app.drills

import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.DrillCatalog
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.model.CalibrationConfigRecord
import com.inversioncoach.app.model.DrillDefinitionRecord
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

object DrillSeeder {
    private const val CURRENT_SEED_VERSION = 3

    private data class SeedCatalogMapping(
        val seedId: String,
        val catalogId: String,
        val legacyDrillType: String? = null,
    )

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

    private val seedMappings: List<SeedCatalogMapping> = listOf(
        SeedCatalogMapping(seedId = "seed_push_up", catalogId = "push_up"),
        SeedCatalogMapping(seedId = "seed_bar_dip", catalogId = "bar_dip"),
        SeedCatalogMapping(seedId = "seed_pike_push_up", catalogId = "pike_push_up"),
        SeedCatalogMapping(seedId = "seed_elevated_pike_push_up", catalogId = "elevated_pike_push_up"),
        SeedCatalogMapping(seedId = "seed_pull_up", catalogId = "pull_up"),
        SeedCatalogMapping(seedId = "seed_inverted_row", catalogId = "inverted_row"),
        SeedCatalogMapping(seedId = "seed_bodyweight_squat", catalogId = "bodyweight_squat"),
        SeedCatalogMapping(seedId = "seed_forward_lunge", catalogId = "forward_lunge"),
        SeedCatalogMapping(seedId = "seed_pistol_squat", catalogId = "pistol_squat"),
        SeedCatalogMapping(seedId = "seed_front_plank", catalogId = "front_plank"),
        SeedCatalogMapping(seedId = "seed_side_plank", catalogId = "side_plank"),
        SeedCatalogMapping(seedId = "seed_leg_raise", catalogId = "hanging_leg_raise"),
        SeedCatalogMapping(seedId = "seed_hollow_hold", catalogId = "hollow_hold"),
        SeedCatalogMapping(seedId = "seed_free_handstand", catalogId = "handstand_hold", legacyDrillType = "FREE_HANDSTAND"),
        SeedCatalogMapping(seedId = "seed_wall_handstand", catalogId = "handstand_push_up", legacyDrillType = "WALL_HANDSTAND_PUSH_UP"),
    )

    fun seedDrills(nowMs: Long, catalog: DrillCatalog? = null): List<DrillDefinitionRecord> =
        seedCatalog(catalog).map { it.toRecord(nowMs) }

    fun reconcileSeededDrills(
        existing: List<DrillDefinitionRecord>,
        nowMs: Long,
        catalog: DrillCatalog? = null,
    ): List<DrillDefinitionRecord> {
        val existingById = existing.associateBy { it.id }
        return seedCatalog(catalog).mapNotNull { seed ->
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

    private fun seedCatalog(catalog: DrillCatalog?): List<SeedCatalogEntry> {
        val drillsById = catalog?.drills?.associateBy { it.id }.orEmpty()
        return seedMappings.mapNotNull { mapping ->
            val drill = drillsById[mapping.catalogId] ?: return@mapNotNull null
            drill.toSeedCatalogEntry(mapping)
        }.ifEmpty {
            legacySeedCatalog
        }
    }

    private fun DrillTemplate.toSeedCatalogEntry(mapping: SeedCatalogMapping): SeedCatalogEntry {
        val legacyToken = mapping.legacyDrillType?.let { listOf("legacyDrillType:$it") }.orEmpty()
        val cueConfig = seededCueConfig(
            seedKey = mapping.seedId,
            values = listOf(
                "seedSource:system",
                "seedCatalogId:$id",
                "comparisonMode:${comparisonMode.name}",
                "studioPayload:${encodeStudioPayload(this)}",
            ) + legacyToken,
        )
        return SeedCatalogEntry(
            id = mapping.seedId,
            name = title,
            description = description,
            movementMode = if (movementType == CatalogMovementType.REP) DrillMovementMode.REP else DrillMovementMode.HOLD,
            cameraView = cameraView.toLegacyCameraView(),
            phaseSchemaJson = phases.sortedBy { it.order }.joinToString("|") { phase -> phase.id },
            keyJointsJson = keyJoints.joinToString("|"),
            normalizationBasisJson = normalizationBasis.name,
            cueConfigJson = cueConfig,
        )
    }

    private fun encodeStudioPayload(drill: DrillTemplate): String {
        val json = JSONObject().apply {
            put("cameraView", drill.cameraView.name)
            put("supportedViews", JSONArray().apply { drill.supportedViews.forEach { put(it.name) } })
            put("comparisonMode", drill.comparisonMode.name)
            put("keyJoints", JSONArray().apply { drill.keyJoints.forEach(::put) })
            put("normalizationBasis", drill.normalizationBasis.name)
            put("phases", JSONArray().apply {
                drill.phases.sortedBy { it.order }.forEach { phase ->
                    put(
                        JSONObject().apply {
                            put("id", phase.id)
                            put("label", phase.label)
                            put("order", phase.order)
                            put("windowStart", phase.progressWindow.start)
                            put("windowEnd", phase.progressWindow.end)
                        },
                    )
                }
            })
            put("phasePoses", JSONArray().apply {
                drill.skeletonTemplate.phasePoses.forEach { pose ->
                    put(
                        JSONObject().apply {
                            put("phaseId", pose.phaseId)
                            put("name", pose.name)
                            put("holdDurationMs", pose.holdDurationMs)
                            put("transitionDurationMs", pose.transitionDurationMs)
                            put("joints", JSONObject(pose.joints.mapValues { (_, p) -> JSONArray().put(p.x).put(p.y) }))
                        },
                    )
                }
            })
            put("keyframes", JSONArray().apply {
                drill.skeletonTemplate.keyframes.forEach { frame ->
                    put(
                        JSONObject().apply {
                            put("progress", frame.progress)
                            put("joints", JSONObject(frame.joints.mapValues { (_, p) -> JSONArray().put(p.x).put(p.y) }))
                        },
                    )
                }
            })
            put("metricThresholds", JSONObject(drill.calibration.metricThresholds))
            put("fpsHint", drill.skeletonTemplate.framesPerSecond)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
    }

    private fun CameraView.toLegacyCameraView(): String = when (this) {
        CameraView.LEFT_PROFILE -> DrillCameraView.LEFT
        CameraView.RIGHT_PROFILE -> DrillCameraView.RIGHT
        CameraView.FRONT -> DrillCameraView.FRONT
        CameraView.SIDE -> DrillCameraView.LEFT
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

    private fun seededCueConfig(seedKey: String, values: List<String>): String =
        (listOf("seedKey:$seedKey") + values).joinToString("|")

    private val legacySeedCatalog: List<SeedCatalogEntry> = listOf(
        seededRep("seed_push_up", "Push-up", "Classic horizontal pressing strength drill."),
        seededRep("seed_bar_dip", "Bar Dip", "Vertical pressing dip with shoulder and triceps focus."),
        seededRep("seed_pike_push_up", "Pike Push Up", "Shoulder-dominant pressing pattern."),
        seededRep("seed_elevated_pike_push_up", "Elevated Pike Push Up", "Progressed pike push-up with feet elevated."),
        seededRep("seed_pull_up", "Pull-up", "Vertical pulling movement for upper body pulling strength."),
        seededRep("seed_inverted_row", "Inverted Row", "Horizontal pulling movement using bodyweight."),
        seededRep("seed_bodyweight_squat", "Bodyweight Squat", "Foundational bilateral squat pattern."),
        seededRep("seed_forward_lunge", "Forward Lunge", "Single-leg pattern emphasizing control and stability."),
        seededRep("seed_pistol_squat", "Pistol Squat", "Advanced single-leg squat requiring balance and strength."),
        seededHold("seed_front_plank", "Front Plank", "Isometric core brace with neutral spine."),
        seededHold("seed_side_plank", "Side Plank", "Lateral core stability hold."),
        seededRep("seed_leg_raise", "Leg Raise", "Anterior core flexion and hip flexor control drill."),
        seededHold("seed_hollow_hold", "Hollow Hold", "Gymnastics hollow-body bracing position."),
        seededHold("seed_free_handstand", "Handstand Hold", "Freestanding handstand stability hold.", legacyDrillType = "FREE_HANDSTAND"),
        seededRep("seed_wall_handstand", "Handstand Push Up", "Inverted pressing drill for handstand push-up progression.", legacyDrillType = "WALL_HANDSTAND_PUSH_UP"),
    )

    private fun seededRep(
        id: String,
        name: String,
        description: String,
        legacyDrillType: String? = null,
    ): SeedCatalogEntry = SeedCatalogEntry(
        id = id,
        name = name,
        description = description,
        movementMode = DrillMovementMode.REP,
        cameraView = DrillCameraView.LEFT,
        phaseSchemaJson = "setup|eccentric|concentric",
        keyJointsJson = "shoulders|hips|knees",
        normalizationBasisJson = "hips",
        cueConfigJson = seededCueConfig(
            seedKey = id,
            values = listOfNotNull("seedSource:legacy", "comparisonMode:POSE_TIMELINE", legacyDrillType?.let { "legacyDrillType:$it" }),
        ),
    )

    private fun seededHold(
        id: String,
        name: String,
        description: String,
        legacyDrillType: String? = null,
    ): SeedCatalogEntry = SeedCatalogEntry(
        id = id,
        name = name,
        description = description,
        movementMode = DrillMovementMode.HOLD,
        cameraView = DrillCameraView.LEFT,
        phaseSchemaJson = "setup|stack|hold",
        keyJointsJson = "shoulders|hips|ankles",
        normalizationBasisJson = "hips",
        cueConfigJson = seededCueConfig(
            seedKey = id,
            values = listOfNotNull("seedSource:legacy", "comparisonMode:POSE_TIMELINE", legacyDrillType?.let { "legacyDrillType:$it" }),
        ),
    )

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
