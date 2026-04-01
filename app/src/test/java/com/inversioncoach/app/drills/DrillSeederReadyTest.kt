package com.inversioncoach.app.drills

import com.inversioncoach.app.model.DrillDefinitionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillSeederReadyTest {
    @Test
    fun seededCatalog_containsIntendedDrillSet() {
        val seeded = DrillSeeder.seedDrills(0L)
        assertEquals(15, seeded.size)

        val ids = seeded.map { it.id }.toSet()
        assertTrue(
            ids.containsAll(
                setOf(
                    "seed_push_up",
                    "seed_bar_dip",
                    "seed_pike_push_up",
                    "seed_elevated_pike_push_up",
                    "seed_pull_up",
                    "seed_inverted_row",
                    "seed_bodyweight_squat",
                    "seed_forward_lunge",
                    "seed_pistol_squat",
                    "seed_front_plank",
                    "seed_side_plank",
                    "seed_leg_raise",
                    "seed_hollow_hold",
                    "seed_free_handstand",
                    "seed_wall_handstand",
                ),
            ),
        )
        assertTrue(seeded.all { it.status == DrillStatus.READY })
        assertTrue(seeded.all { it.sourceType == DrillSourceType.SEEDED })
        assertTrue(seeded.all { it.cueConfigJson.contains("seedKey:${it.id}") })
        assertTrue(seeded.all { it.cueConfigJson.contains("seedSource:system") })
        assertTrue(seeded.none { it.name.isBlank() || it.name.contains("draft", ignoreCase = true) })
    }

    @Test
    fun reconcileSeededDrills_cleanInstall_insertsAllSeeds() {
        val writes = DrillSeeder.reconcileSeededDrills(existing = emptyList(), nowMs = 100L)
        assertEquals(15, writes.size)
    }

    @Test
    fun reconcileSeededDrills_upgradeFromLegacyTwoSeededDrills_repairsAndAddsMissing() {
        val legacy = listOf(
            legacySeed(id = "seed_free_handstand", name = "Free Handstand", movementMode = DrillMovementMode.HOLD),
            legacySeed(id = "seed_wall_handstand", name = "Wall Handstand", movementMode = DrillMovementMode.HOLD),
        )

        val writes = DrillSeeder.reconcileSeededDrills(existing = legacy, nowMs = 1234L)

        assertEquals(15, writes.size)
        val repairedWall = writes.first { it.id == "seed_wall_handstand" }
        assertEquals("Handstand Push Up", repairedWall.name)
        assertEquals(DrillMovementMode.REP, repairedWall.movementMode)
    }

    @Test
    fun reconcileSeededDrills_isIdempotent() {
        val firstPass = DrillSeeder.reconcileSeededDrills(existing = emptyList(), nowMs = 1L)
        val secondPass = DrillSeeder.reconcileSeededDrills(existing = firstPass, nowMs = 2L)
        assertTrue(secondPass.isEmpty())
    }

    @Test
    fun reconcileSeededDrills_neverOverwritesUserDrillWithSeedId() {
        val userDrill = legacySeed(
            id = "seed_push_up",
            name = "My Custom Push-up",
            sourceType = DrillSourceType.USER_CREATED,
        )
        val writes = DrillSeeder.reconcileSeededDrills(existing = listOf(userDrill), nowMs = 44L)

        assertFalse(writes.any { it.id == "seed_push_up" })
        assertEquals(14, writes.size)
    }

    private fun legacySeed(
        id: String,
        name: String,
        movementMode: String = DrillMovementMode.REP,
        sourceType: String = DrillSourceType.SEEDED,
    ): DrillDefinitionRecord = DrillDefinitionRecord(
        id = id,
        name = name,
        description = "legacy",
        movementMode = movementMode,
        cameraView = DrillCameraView.LEFT,
        phaseSchemaJson = "setup|stack|hold",
        keyJointsJson = "shoulders|hips|ankles",
        normalizationBasisJson = "hips",
        cueConfigJson = "legacy",
        sourceType = sourceType,
        status = DrillStatus.READY,
        version = 1,
        createdAtMs = 0L,
        updatedAtMs = 0L,
    )
}
