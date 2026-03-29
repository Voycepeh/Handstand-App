package com.inversioncoach.app.movementprofile

import android.content.Context
import com.inversioncoach.app.model.ReferenceTemplateRecord
import org.json.JSONObject

private const val TEMPLATE_DIR = "reference_templates"

class ReferenceTemplateLoader(private val context: Context) {
    fun loadBuiltInTemplates(): List<ReferenceTemplateDefinition> {
        val assets = context.assets
        val files = assets.list(TEMPLATE_DIR)?.filter { it.endsWith(".json") }?.sorted().orEmpty()
        return files.map { fileName ->
            val assetPath = "$TEMPLATE_DIR/$fileName"
            val json = assets.open(assetPath).bufferedReader().use { it.readText() }
            parseTemplate(json = JSONObject(json), assetPath = assetPath)
        }
    }

    private fun parseTemplate(json: JSONObject, assetPath: String): ReferenceTemplateDefinition {
        val id = json.getString("id")
        val drillId = json.optString("drillId", when (json.optString("drillType")) {
            "FREE_HANDSTAND" -> "seed_free_handstand"
            "WALL_HANDSTAND" -> "seed_wall_handstand"
            else -> "seed_free_handstand"
        })
        return ReferenceTemplateDefinition(
            id = id,
            templateName = json.getString("templateName"),
            drillId = drillId,
            description = json.optString("description", ""),
            phaseTimingMs = json.optJSONObject("phaseTimingsMs").toLongMap(),
            alignmentTargets = json.optJSONObject("alignmentTargets").toFloatMap(),
            stabilityTargets = json.optJSONObject("stabilityTargets").toFloatMap(),
            assetPath = assetPath,
        )
    }
}

fun ReferenceTemplateDefinition.toRecord(updatedAtMs: Long): ReferenceTemplateRecord = ReferenceTemplateRecord(
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

private fun JSONObject?.toLongMap(): Map<String, Long> {
    if (this == null) return emptyMap()
    val keys = keys()
    val map = linkedMapOf<String, Long>()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = optLong(key)
    }
    return map
}

private fun JSONObject?.toFloatMap(): Map<String, Float> {
    if (this == null) return emptyMap()
    val keys = keys()
    val map = linkedMapOf<String, Float>()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = optDouble(key, 0.0).toFloat()
    }
    return map
}
