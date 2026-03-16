package com.inversioncoach.app.motion

import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillCatalogSchemaTest {
    @Test
    fun wave1_drills_have_required_schema_fields() {
        val pushUp = DrillCatalog.byType(DrillType.STANDARD_PUSH_UP)
        assertEquals("Push-Up", pushUp.displayName)
        assertEquals(RepMode.REP_BASED, pushUp.repMode)
        assertTrue(pushUp.requiredLandmarks.isNotEmpty())
        assertTrue(pushUp.mainPhases.isNotEmpty())
        assertTrue(pushUp.postureRulePlaceholders.isNotEmpty())
    }

    @Test
    fun handstand_drills_use_expected_tracking_modes() {
        assertEquals(RepMode.HOLD_BASED, DrillCatalog.byType(DrillType.FREESTANDING_HANDSTAND_FUTURE).repMode)
        assertEquals(RepMode.HOLD_BASED, DrillCatalog.byType(DrillType.CHEST_TO_WALL_HANDSTAND).repMode)

        assertEquals(RepMode.REP_BASED, DrillCatalog.byType(DrillType.PIKE_PUSH_UP).repMode)
        assertEquals(RepMode.REP_BASED, DrillCatalog.byType(DrillType.ELEVATED_PIKE_PUSH_UP).repMode)
        assertEquals(RepMode.REP_BASED, DrillCatalog.byType(DrillType.PUSH_UP).repMode)
        assertEquals(RepMode.REP_BASED, DrillCatalog.byType(DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP).repMode)
    }

}