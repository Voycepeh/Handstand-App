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
}
