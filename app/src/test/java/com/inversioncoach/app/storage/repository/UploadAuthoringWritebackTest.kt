package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.CatalogNormalizationBasis
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.ui.drillstudio.toDrillTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadAuthoringWritebackTest {

    @Test
    fun `existing drill upload updates persisted authored drill content`() {
        val existing = seedDrill(id = "d1", status = DrillStatus.READY)
        val store = FakeStore(mapOf(existing.id to existing).toMutableMap())

        val resolved = ensureDrillForUploadWriteback(
            store = store,
            preferredDrill = null,
            preferredDrillId = existing.id,
            createDrillFromReferenceUpload = false,
            pendingDrillName = null,
        )

        val authoredTemplate = existing.toDrillTemplate(seed = null).copy(
            phases = listOf(
                DrillPhaseTemplate("setup", "Setup", 1, PhaseWindow(0f, 0.5f)),
                DrillPhaseTemplate("press", "Press", 2, PhaseWindow(0.5f, 1f)),
            ),
        )
        persistUploadAuthoredDrillTemplate(
            store = store,
            record = resolved!!.drill,
            template = authoredTemplate,
            ready = true,
        )

        val updated = store.drills[existing.id]!!
        assertTrue(updated.cueConfigJson.contains("studioPayload:"))
        assertEquals("Setup|Press", updated.phaseSchemaJson)
    }

    @Test
    fun `create new drill upload results in populated created drill`() {
        val store = FakeStore(mutableMapOf())
        val resolved = ensureDrillForUploadWriteback(
            store = store,
            preferredDrill = null,
            preferredDrillId = null,
            createDrillFromReferenceUpload = true,
            pendingDrillName = "My Upload Drill",
        )
        assertNotNull(resolved)
        assertTrue(resolved!!.created)

        val authoredTemplate = resolved.drill.toDrillTemplate(seed = null).copy(
            title = "My Upload Drill",
            movementType = CatalogMovementType.REP,
            cameraView = CameraView.SIDE,
            supportedViews = listOf(CameraView.SIDE),
            phases = listOf(
                DrillPhaseTemplate("phase_1", "Entry", 1, PhaseWindow(0f, 1f)),
            ),
        )
        persistUploadAuthoredDrillTemplate(
            store = store,
            record = resolved.drill,
            template = authoredTemplate,
            ready = false,
        )

        val created = store.drills[resolved.drill.id]!!
        assertEquals("My Upload Drill", created.name)
        assertEquals("Entry", created.phaseSchemaJson)
        assertTrue(created.cueConfigJson.contains("studioPayload:"))
    }

    private fun seedDrill(id: String, status: String): DrillDefinitionRecord = DrillDefinitionRecord(
        id = id,
        name = "Seed",
        description = "",
        movementMode = "HOLD",
        cameraView = "LEFT",
        phaseSchemaJson = "setup",
        keyJointsJson = "left_shoulder|right_shoulder",
        normalizationBasisJson = CatalogNormalizationBasis.HIPS.name,
        cueConfigJson = "legacyDrillType:FREE_HANDSTAND",
        sourceType = "USER_CREATED",
        status = status,
        version = 1,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )

    private class FakeStore(val drills: MutableMap<String, DrillDefinitionRecord>) : UploadAuthoringDrillStore {
        override suspend fun getDrill(drillId: String): DrillDefinitionRecord? = drills[drillId]
        override suspend fun createDrill(record: DrillDefinitionRecord) {
            drills[record.id] = record
        }

        override suspend fun updateDrill(record: DrillDefinitionRecord) {
            drills[record.id] = record
        }
    }
}
