package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.ReferenceTemplateRecord
import com.inversioncoach.app.movementprofile.ReferenceTemplateDefinition
import org.json.JSONObject

internal object ReferenceTemplatePersistenceCodec {
    fun decodeReferenceProfileIds(sourceProfileIdsJson: String): List<String> =
        sourceProfileIdsJson.split('|').mapNotNull { token ->
            token.trim().takeIf { it.isNotEmpty() }
        }

    fun decodeTemplateDefinition(record: ReferenceTemplateRecord): ReferenceTemplateDefinition? = runCatching {
        val checkpoint = JSONObject(record.checkpointJson)
        val tolerance = JSONObject(record.toleranceJson)
        val phase = checkpoint.optJSONObject("phaseTimingsMs") ?: JSONObject()
        val alignment = tolerance.optJSONObject("alignmentTargets")
            ?: tolerance.optJSONObject("featureMeans")
            ?: JSONObject()
        val stability = tolerance.optJSONObject("stabilityTargets")
            ?: tolerance.optJSONObject("stabilityJitter")
            ?: JSONObject()

        ReferenceTemplateDefinition(
            id = record.id,
            templateName = record.displayName,
            drillId = record.drillId,
            description = record.displayName,
            phaseTimingMs = jsonLongMap(phase),
            alignmentTargets = jsonFloatMap(alignment),
            stabilityTargets = jsonFloatMap(stability),
            assetPath = "",
        )
    }.getOrNull()

    private fun jsonLongMap(obj: JSONObject): Map<String, Long> {
        val out = linkedMapOf<String, Long>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = obj.optLong(key)
        }
        return out
    }

    private fun jsonFloatMap(obj: JSONObject): Map<String, Float> {
        val out = linkedMapOf<String, Float>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = obj.optDouble(key, 0.0).toFloat()
        }
        return out
    }
}
