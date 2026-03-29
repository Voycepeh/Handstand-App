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

    companion object {
        const val DEFAULT_ASSET_PATH = "drill_catalog/drill_catalog_v1.json"
    }
}
