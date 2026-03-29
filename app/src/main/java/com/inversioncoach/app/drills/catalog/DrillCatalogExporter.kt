package com.inversioncoach.app.drills.catalog

object DrillCatalogExporter {
    fun export(catalog: DrillCatalog): String = DrillCatalogJson.encode(catalog)
}
