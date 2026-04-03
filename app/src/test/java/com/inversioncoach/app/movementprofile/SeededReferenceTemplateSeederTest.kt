package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.drills.catalog.DrillCatalogJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeededReferenceTemplateSeederTest {
    private fun loadCatalog() = DrillCatalogJson.decode(File("app/src/main/assets/drill_catalog/drill_catalog_v1.json").readText())

    @Test
    fun seedFromCatalog_createsExpectedHandstandBaselines() {
        val templates = SeededReferenceTemplateSeeder().seedFromCatalog(loadCatalog())
        val byId = templates.associateBy { it.id }

        assertEquals(setOf("free_handstand_baseline_v1", "wall_handstand_baseline_v1"), byId.keys)
        assertEquals("seed_free_handstand", byId.getValue("free_handstand_baseline_v1").drillId)
        assertEquals("seed_wall_handstand", byId.getValue("wall_handstand_baseline_v1").drillId)
        assertTrue(byId.getValue("free_handstand_baseline_v1").assetPath.startsWith("drill_catalog/drill_catalog_v1.json#"))
    }

    @Test
    fun seedFromCatalog_derivesPhaseTimingFromCatalogPhases() {
        val templates = SeededReferenceTemplateSeeder().seedFromCatalog(loadCatalog()).associateBy { it.id }

        val free = templates.getValue("free_handstand_baseline_v1")
        assertEquals(setOf("entry_setup", "stack_hold", "balance_adjust", "exit"), free.phaseTimingMs.keys)
        assertTrue(free.phaseTimingMs.values.all { it >= 300L })

        val wall = templates.getValue("wall_handstand_baseline_v1")
        assertEquals(setOf("stack_top", "descent", "bottom_tripod", "press", "restack"), wall.phaseTimingMs.keys)
        assertTrue(wall.phaseTimingMs.values.all { it >= 300L })
    }
    @Test
    fun seededTemplateRecord_roundTripsThroughCodec() {
        val template = SeededReferenceTemplateSeeder().seedFromCatalog(loadCatalog()).first { it.id == "free_handstand_baseline_v1" }

        val decoded = ReferenceTemplateRecordCodec.toDefinition(template.toRecord(updatedAtMs = 123L))
        assertEquals(template.phaseTimingMs, decoded?.phaseTimingMs)
        assertEquals(template.alignmentTargets, decoded?.alignmentTargets)
        assertEquals(template.stabilityTargets, decoded?.stabilityTargets)
    }

}
