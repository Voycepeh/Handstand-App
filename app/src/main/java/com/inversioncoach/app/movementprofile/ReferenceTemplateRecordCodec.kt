package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.model.ReferenceTemplateRecord
import org.json.JSONObject

object ReferenceTemplateRecordCodec {
    fun toDefinition(record: ReferenceTemplateRecord): ReferenceTemplateDefinition? = runCatching {
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
            phaseTimingMs = phase.toLongMap(),
            alignmentTargets = alignment.toFloatMap(),
            stabilityTargets = stability.toFloatMap(),
            assetPath = "",
        )
    }.getOrNull()

    fun sourceProfileIds(record: ReferenceTemplateRecord): List<String> =
        record.sourceProfileIdsJson
            .split('|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
}

private fun JSONObject.toLongMap(): Map<String, Long> {
    val out = linkedMapOf<String, Long>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        out[key] = optLong(key)
    }
    return out
}

private fun JSONObject.toFloatMap(): Map<String, Float> {
    val out = linkedMapOf<String, Float>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        out[key] = optDouble(key, 0.0).toFloat()
    }
    return out
}
