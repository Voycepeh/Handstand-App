package com.inversioncoach.app.drills.catalog

import android.content.Context

class DrillCatalogRepository(
    private val context: Context,
) {
    @Volatile
    private var cachedCatalog: Pair<String, DrillCatalog>? = null

    fun loadCatalog(assetPath: String = DEFAULT_ASSET_PATH): DrillCatalog {
        val existing = cachedCatalog
        if (existing != null && existing.first == assetPath) return existing.second

        val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val decoded = DrillCatalogJson.decode(raw)
        cachedCatalog = assetPath to decoded
        return decoded
    }

    fun exportCatalog(catalog: DrillCatalog): String = DrillCatalogExporter.export(catalog)

    fun getAllDrills(assetPath: String = DEFAULT_ASSET_PATH): List<DrillTemplate> = loadCatalog(assetPath).drills

    fun getDrillById(id: String, assetPath: String = DEFAULT_ASSET_PATH): DrillTemplate? =
        getAllDrills(assetPath).firstOrNull { it.id == id }

    companion object {
        const val DEFAULT_ASSET_PATH = "drill_catalog/drill_catalog_v1.json"
    }
}
