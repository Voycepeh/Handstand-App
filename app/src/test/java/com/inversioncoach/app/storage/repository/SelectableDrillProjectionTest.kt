package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.DrillDefinitionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class SelectableDrillProjectionTest {
    @Test
    fun projectionUsesDeterministicOrderingAndDeduplicatesById() {
        val drills = listOf(
            drill(id = "user-beta", name = "Beta"),
            drill(id = "seed-alpha", name = "Alpha", sourceType = "SEEDED"),
            drill(id = "seed-alpha", name = "Alpha overwritten", sourceType = "SEEDED", status = "ARCHIVED"),
        )

        val projected = projectSelectableDrills(drills)

        assertEquals(listOf("seed-alpha", "user-beta"), projected.map { it.id })
        assertEquals(listOf("Alpha overwritten", "Beta"), projected.map { it.name })
    }

    @Test
    fun projectionParsesSkeletonPreviewWhenStudioPayloadExists() {
        val keyframesJson = """
            {"keyframes":[
              {"progress":0.0,"joints":{"head":[0.5,0.1],"shoulder_left":[0.4,0.2]}},
              {"progress":1.0,"joints":{"head":[0.5,0.12],"shoulder_left":[0.4,0.22]}}
            ]}
        """.trimIndent()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(keyframesJson.toByteArray())
        val projected = projectSelectableDrills(
            listOf(drill(id = "seed-preview", name = "Preview", cueConfigJson = "legacyDrillType:FREE_HANDSTAND|studioPayload:$encoded")),
        )

        val preview = projected.single().previewSkeleton

        assertNotNull(preview)
        assertEquals(2, preview?.keyframes?.size)
    }

    @Test
    fun projectionFallsBackWhenStudioPayloadMissing() {
        val projected = projectSelectableDrills(listOf(drill(id = "plain", name = "Plain")))
        assertNull(projected.single().previewSkeleton)
    }

    private fun drill(
        id: String,
        name: String,
        sourceType: String = "SEEDED",
        status: String = "READY",
        cueConfigJson: String = "legacyDrillType:FREE_HANDSTAND|comparisonMode:POSE_TIMELINE",
    ) = DrillDefinitionRecord(
        id = id,
        name = name,
        description = "$name description",
        movementMode = "HOLD",
        cameraView = "SIDE",
        phaseSchemaJson = "setup|hold",
        keyJointsJson = "shoulder_left|shoulder_right",
        normalizationBasisJson = "HIPS",
        cueConfigJson = cueConfigJson,
        sourceType = sourceType,
        status = status,
        version = 1,
        createdAtMs = 1L,
        updatedAtMs = 2L,
    )
}
