package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.model.ReferenceTemplateRecord
import org.json.JSONObject
import java.util.UUID

class ReferenceTemplateBuilder {
    fun buildFromSingleReference(
        drillId: String,
        displayName: String,
        sourceProfileId: String,
        snapshot: StoredProfileSnapshot,
        createdAtMs: Long = System.currentTimeMillis(),
        sourceType: String = "REFERENCE_UPLOAD",
        sourceSessionId: Long? = null,
        isBaseline: Boolean = false,
    ): ReferenceTemplateRecord {
        return ReferenceTemplateRecord(
            id = "template-${UUID.randomUUID()}",
            drillId = drillId,
            displayName = displayName,
            templateType = "SINGLE_REFERENCE",
            sourceType = sourceType,
            sourceSessionId = sourceSessionId,
            title = displayName,
            phasePosesJson = snapshot.phaseDurationsMs.keys.joinToString("|"),
            keyframesJson = "",
            fpsHint = null,
            durationMs = snapshot.phaseDurationsMs.values.sum().takeIf { it > 0L },
            updatedAtMs = createdAtMs,
            isBaseline = isBaseline,
            sourceProfileIdsJson = sourceProfileId,
            checkpointJson = JSONObject().apply {
                put("phaseTimingsMs", JSONObject(snapshot.phaseDurationsMs.mapValues { it.value }))
            }.toString(),
            toleranceJson = JSONObject().apply {
                put("featureMeans", JSONObject(snapshot.featureMeans.mapValues { it.value }))
                put("stabilityJitter", JSONObject(snapshot.stabilityJitter.mapValues { it.value }))
            }.toString(),
            createdAtMs = createdAtMs,
        )
    }
}
