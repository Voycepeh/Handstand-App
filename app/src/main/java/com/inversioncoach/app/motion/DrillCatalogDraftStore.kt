package com.inversioncoach.app.motion

import android.content.Context
import com.inversioncoach.app.model.DrillType
import org.json.JSONObject

class DrillCatalogDraftStore(private val context: Context) {
    private val storeFile = context.filesDir.resolve("drill_studio/drafts.json").apply { parentFile?.mkdirs() }

    data class DrillPickerItem(
        val id: String,
        val name: String,
        val seeded: Boolean,
        val hasDraft: Boolean,
    )

    fun listDrills(): List<DrillPickerItem> {
        val drafts = loadDrafts()
        val seeded = DrillCatalog.all.map { drill ->
            DrillPickerItem(
                id = drill.id.name,
                name = drill.displayName,
                seeded = true,
                hasDraft = drafts.has(drill.id.name),
            )
        }
        val custom = drafts.keys().asSequence()
            .filterNot { key -> DrillType.fromStoredName(key) != null }
            .mapNotNull { key ->
                val document = runCatching { DrillStudioCodec.fromJson(drafts.getJSONObject(key)) }.getOrNull() ?: return@mapNotNull null
                DrillPickerItem(
                    id = key,
                    name = document.displayName,
                    seeded = false,
                    hasDraft = true,
                )
            }
            .toList()
        return seeded + custom
    }

    fun loadForEditor(id: String): DrillStudioDocument {
        val drafts = loadDrafts()
        if (drafts.has(id)) {
            return DrillStudioCodec.fromJson(drafts.getJSONObject(id))
        }
        val seededType = DrillType.fromStoredName(id)
        if (seededType != null) {
            return DrillStudioCodec.fromSeeded(DrillCatalog.byType(seededType))
        }
        error("Unknown drill id: $id")
    }

    fun saveDraft(document: DrillStudioDocument) {
        val drafts = loadDrafts()
        drafts.put(document.id, DrillStudioCodec.toJson(document))
        storeFile.writeText(drafts.toString(2))
    }

    fun resetDraft(id: String) {
        val drafts = loadDrafts()
        drafts.remove(id)
        storeFile.writeText(drafts.toString(2))
    }

    fun duplicate(sourceId: String): DrillStudioDocument {
        val source = loadForEditor(sourceId)
        val newId = "custom_${System.currentTimeMillis()}"
        val duplicate = source.copy(
            id = newId,
            seededDrillType = null,
            displayName = "${source.displayName} Copy",
        )
        saveDraft(duplicate)
        return duplicate
    }

    private fun loadDrafts(): JSONObject {
        if (!storeFile.exists()) return JSONObject()
        val raw = storeFile.readText()
        if (raw.isBlank()) return JSONObject()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }
}
