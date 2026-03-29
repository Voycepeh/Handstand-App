package com.inversioncoach.app.drills.catalog

import android.content.Context

class DrillCatalogRepository(private val context: Context) {
    private val drills: List<DrillTemplate> by lazy {
        val raw = context.assets.open("drill_catalog/drill_catalog_v1.json").bufferedReader().use { it.readText() }
        DrillCatalogJson.decodeCatalog(raw)
    }

    fun getAllDrills(): List<DrillTemplate> = drills

    fun getDrillById(id: String): DrillTemplate? = drills.firstOrNull { it.id == id }
}
