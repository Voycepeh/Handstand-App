package com.inversioncoach.app.drills.catalog

import android.content.Context

class DrillCatalogRepository(
    private val context: Context,
) {
    fun loadCatalog(assetPath: String = DEFAULT_ASSET_PATH): DrillCatalog {
        val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return DrillCatalogJson.decode(raw)
    }

    fun exportCatalog(catalog: DrillCatalog): String = DrillCatalogExporter.export(catalog)


    fun getAllDrills(assetPath: String = DEFAULT_ASSET_PATH): List<DrillTemplate> = loadCatalog(assetPath).drills

    fun getDrillById(id: String, assetPath: String = DEFAULT_ASSET_PATH): DrillTemplate? =
        getAllDrills(assetPath).firstOrNull { it.id == id }

    companion object {
        const val DEFAULT_ASSET_PATH = "drill_catalog/drill_catalog_v1.json"
    }
}
