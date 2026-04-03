package com.inversioncoach.app.ui.drillstudio

import android.content.Context
import android.net.Uri
import com.inversioncoach.app.drills.DrillCueConfigCodec
import com.inversioncoach.app.model.DrillDefinitionRecord
import org.json.JSONObject
import java.io.File
import java.util.Base64

class DrillPackageManager(private val context: Context) {
    private val exportDir = context.filesDir.resolve("drill_studio/exports").apply { mkdirs() }

    fun export(record: DrillDefinitionRecord): File {
        val sanitizedCueConfig = sanitizeCueConfig(record.cueConfigJson)
        val payload = decodeStudioPayload(sanitizedCueConfig)
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAtMs", System.currentTimeMillis())
            put("drill", JSONObject().apply {
                put("id", record.id)
                put("name", record.name)
                put("description", record.description)
                put("movementMode", record.movementMode)
                put("cameraView", record.cameraView)
                put("phaseSchemaJson", record.phaseSchemaJson)
                put("keyJointsJson", record.keyJointsJson)
                put("normalizationBasisJson", record.normalizationBasisJson)
                put("cueConfigJson", sanitizedCueConfig)
                put("sourceType", record.sourceType)
                put("status", record.status)
                put("version", record.version)
            })
            payload?.let {
                put("studioPayload", JSONObject(String(Base64.getUrlDecoder().decode(DrillCueConfigCodec.parse(sanitizedCueConfig).studioPayload))))
            }
        }
        val file = exportDir.resolve("${record.id}_${System.currentTimeMillis()}.drill.json")
        file.writeText(json.toString(2))
        return file
    }

    fun import(uri: Uri): DrillDefinitionRecord {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Unable to read drill package")
        val json = JSONObject(text)
        val drill = json.getJSONObject("drill")
        val now = System.currentTimeMillis()
        val sanitizedCueConfig = sanitizeCueConfig(drill.getString("cueConfigJson"))
        return DrillDefinitionRecord(
            id = drill.getString("id"),
            name = drill.getString("name"),
            description = drill.optString("description"),
            movementMode = drill.getString("movementMode"),
            cameraView = drill.getString("cameraView"),
            phaseSchemaJson = drill.getString("phaseSchemaJson"),
            keyJointsJson = drill.getString("keyJointsJson"),
            normalizationBasisJson = drill.optString("normalizationBasisJson"),
            cueConfigJson = sanitizedCueConfig,
            sourceType = drill.optString("sourceType", "USER_CREATED"),
            status = drill.optString("status", "READY"),
            version = drill.optInt("version", 1),
            createdAtMs = now,
            updatedAtMs = now,
        )
    }

    private fun sanitizeCueConfig(cueConfigJson: String): String {
        val tokens = DrillCueConfigCodec.parse(cueConfigJson)
        val encodedPayload = tokens.studioPayload ?: return cueConfigJson
        val payloadJson = runCatching {
            JSONObject(String(Base64.getUrlDecoder().decode(encodedPayload)))
        }.getOrElse { return cueConfigJson }

        payloadJson.optJSONArray("phasePoses")?.let { array ->
            for (index in 0 until array.length()) {
                val phasePose = array.optJSONObject(index) ?: continue
                val authoring = phasePose.optJSONObject("authoring") ?: continue
                authoring.put("sourceImageUri", JSONObject.NULL)
            }
        }
        val sanitizedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toString().toByteArray())
        return DrillCueConfigCodec.merge(
            existingCueConfig = cueConfigJson,
            comparisonMode = tokens.comparisonMode,
            studioPayload = sanitizedPayload,
            legacyDrillType = tokens.legacyDrillType,
        )
    }
}
