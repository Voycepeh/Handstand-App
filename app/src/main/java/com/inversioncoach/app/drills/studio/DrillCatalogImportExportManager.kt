package com.inversioncoach.app.drills.studio

import android.content.Context
import android.net.Uri
import java.io.File
import org.json.JSONObject

class DrillCatalogImportExportManager(private val context: Context, private val draftStore: DrillCatalogDraftStore) {
    private val exportDir = context.filesDir.resolve("drill_studio/exports").apply { mkdirs() }

    fun exportDraft(document: DrillStudioDocument): File {
        val file = exportDir.resolve("${document.id}_${System.currentTimeMillis()}.json")
        file.writeText(DrillStudioCodec.toJson(document).toString(2))
        return file
    }

    fun importDraft(uri: Uri): DrillStudioDocument {
        val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Could not read file")
        val parsed = DrillStudioCodec.fromJson(JSONObject(content))
        draftStore.saveDraft(parsed)
        return parsed
    }
}
