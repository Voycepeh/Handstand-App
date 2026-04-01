package com.inversioncoach.app.drills.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeededDrillCatalogV1Test {
    private fun loadCatalog(): DrillCatalog {
        val raw = File("app/src/main/assets/drill_catalog/drill_catalog_v1.json").readText()
        return DrillCatalogJson.decode(raw)
    }

    @Test
    fun seededCatalog_containsExpectedV1CalisthenicsDrills() {
        val catalog = loadCatalog()
        assertEquals(15, catalog.drills.size)

        val ids = catalog.drills.map { it.id }.toSet()
        assertEquals(
            setOf(
                "push_up",
                "bar_dip",
                "pike_push_up",
                "elevated_pike_push_up",
                "pull_up",
                "inverted_row",
                "bodyweight_squat",
                "forward_lunge",
                "pistol_squat",
                "front_plank",
                "side_plank",
                "hanging_leg_raise",
                "hollow_hold",
                "handstand_hold",
                "handstand_push_up",
            ),
            ids,
        )
    }

    @Test
    fun seededCatalog_drillsHavePhasesPhasePosesAndKeyframes() {
        val catalog = loadCatalog()

        catalog.drills.forEach { drill ->
            assertTrue("${drill.id} should have ordered phases", drill.phases.isNotEmpty())
            assertTrue("${drill.id} should have phase poses", drill.skeletonTemplate.phasePoses.isNotEmpty())
            assertTrue("${drill.id} should have keyframes", drill.skeletonTemplate.keyframes.isNotEmpty())
            assertEquals(
                "${drill.id} should have one pose per phase",
                drill.phases.map { it.id },
                drill.skeletonTemplate.phasePoses.map { it.phaseId },
            )
        }
    }

    @Test
    fun seededCatalog_phasePosesAreMovementSpecific_notSinglePlaceholderPose() {
        val catalog = loadCatalog()

        catalog.drills.forEach { drill ->
            val uniquePoseSignatures = drill.skeletonTemplate.phasePoses
                .map { pose -> pose.joints.toList().sortedBy { it.first }.joinToString("|") { (joint, point) -> "$joint:${point.x},${point.y}" } }
                .toSet()
            assertTrue(
                "${drill.id} should include distinct phase silhouettes",
                uniquePoseSignatures.size > 1,
            )

            if (drill.movementType == CatalogMovementType.REP) {
                val keyframes = drill.skeletonTemplate.keyframes
                assertTrue("${drill.id} should include a clean loop keyframe", keyframes.first().joints == keyframes.last().joints)
            }
        }
    }

    @Test
    fun seededCatalog_roundTripsThroughJsonCodec() {
        val catalog = loadCatalog()
        val reDecoded = DrillCatalogJson.decode(DrillCatalogJson.encode(catalog))

        assertEquals(catalog.drills.map { it.id }, reDecoded.drills.map { it.id })
        assertFalse(reDecoded.drills.first().skeletonTemplate.phasePoses.first().joints.isEmpty())
        assertFalse(reDecoded.drills.first().skeletonTemplate.keyframes.first().joints.isEmpty())
    }
}
