package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.drills.catalog.DrillCatalog
import com.inversioncoach.app.drills.catalog.DrillTemplate
import org.json.JSONObject
import kotlin.math.roundToLong

private const val DRILL_CATALOG_ASSET_PATH = "drill_catalog/drill_catalog_v1.json"

class SeededReferenceTemplateSeeder {
    fun seedFromCatalog(catalog: DrillCatalog): List<ReferenceTemplateDefinition> {
        val drillsById = catalog.drills.associateBy { it.id }
        return mappings.mapNotNull { mapping ->
            val drill = drillsById[mapping.catalogDrillId] ?: return@mapNotNull null
            drill.toReferenceTemplate(mapping)
        }
    }

    private fun DrillTemplate.toReferenceTemplate(mapping: SeededReferenceMapping): ReferenceTemplateDefinition {
        val phaseTimingMs = phaseDurationsMs(totalDurationMs = mapping.totalDurationMs)
        val alignmentTargets = calibration.metricThresholds.takeIf { it.isNotEmpty() } ?: mapping.defaultAlignmentTargets
        return ReferenceTemplateDefinition(
            id = mapping.templateId,
            templateName = mapping.templateName,
            drillId = mapping.seedDrillId,
            description = mapping.description,
            phaseTimingMs = phaseTimingMs,
            alignmentTargets = alignmentTargets,
            stabilityTargets = mapping.defaultStabilityTargets,
            assetPath = "$DRILL_CATALOG_ASSET_PATH#${mapping.catalogDrillId}",
        )
    }

    private fun DrillTemplate.phaseDurationsMs(totalDurationMs: Long): Map<String, Long> {
        val sorted = phases.sortedBy { it.order }
        if (sorted.isEmpty()) return emptyMap()

        val generated = linkedMapOf<String, Long>()
        var assigned = 0L
        sorted.forEachIndexed { index, phase ->
            val duration = if (index == sorted.lastIndex) {
                (totalDurationMs - assigned).coerceAtLeast(300L)
            } else {
                val raw = ((phase.progressWindow.end - phase.progressWindow.start) * totalDurationMs).roundToLong()
                raw.coerceAtLeast(300L)
            }
            generated[phase.id] = duration
            assigned += duration
        }
        return generated
    }

    private data class SeededReferenceMapping(
        val catalogDrillId: String,
        val seedDrillId: String,
        val templateId: String,
        val templateName: String,
        val description: String,
        val totalDurationMs: Long,
        val defaultAlignmentTargets: Map<String, Float>,
        val defaultStabilityTargets: Map<String, Float>,
    )

    private val mappings = listOf(
        SeededReferenceMapping(
            catalogDrillId = "handstand_hold",
            seedDrillId = "seed_free_handstand",
            templateId = "free_handstand_baseline_v1",
            templateName = "Free Handstand Baseline",
            description = "Reference timing and control for a stable free handstand hold.",
            totalDurationMs = 7100L,
            defaultAlignmentTargets = mapOf(
                "alignment_score" to 0.88f,
                "trunk_lean" to 4.5f,
            ),
            defaultStabilityTargets = mapOf(
                "alignment_score" to 0.05f,
                "trunk_lean" to 2.0f,
            ),
        ),
        SeededReferenceMapping(
            catalogDrillId = "handstand_push_up",
            seedDrillId = "seed_wall_handstand",
            templateId = "wall_handstand_baseline_v1",
            templateName = "Wall Handstand Baseline",
            description = "Reference wall handstand control with slower transition and lower sway.",
            totalDurationMs = 8200L,
            defaultAlignmentTargets = mapOf(
                "alignment_score" to 0.9f,
                "trunk_lean" to 3.0f,
            ),
            defaultStabilityTargets = mapOf(
                "alignment_score" to 0.04f,
                "trunk_lean" to 1.5f,
            ),
        ),
    )
}

fun ReferenceTemplateDefinition.toRecord(updatedAtMs: Long): com.inversioncoach.app.model.ReferenceTemplateRecord = com.inversioncoach.app.model.ReferenceTemplateRecord(
    id = id,
    drillId = drillId,
    displayName = templateName,
    templateType = "SINGLE_REFERENCE",
    sourceProfileIdsJson = "",
    checkpointJson = JSONObject().apply {
        put("phaseTimingsMs", JSONObject(phaseTimingMs.mapValues { it.value }))
    }.toString(),
    toleranceJson = JSONObject().apply {
        put("alignmentTargets", JSONObject(alignmentTargets.mapValues { it.value }))
        put("stabilityTargets", JSONObject(stabilityTargets.mapValues { it.value }))
        put("sourceAssetPath", assetPath)
    }.toString(),
    createdAtMs = updatedAtMs,
)
