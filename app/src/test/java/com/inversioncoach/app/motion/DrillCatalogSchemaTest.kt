package com.inversioncoach.app.motion

import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Test

class DrillCatalogSchemaTest {
    @Test
    fun pushUpVariants_areRepBased() {
        listOf(
            DrillType.PIKE_PUSH_UP,
            DrillType.ELEVATED_PIKE_PUSH_UP,
            DrillType.HANDSTAND_PUSH_UP,
            DrillType.WALL_HANDSTAND_PUSH_UP,
        ).forEach { drillType ->
            assertEquals(RepMode.REP_BASED, DrillCatalog.byType(drillType).repMode)
        }
    }

    @Test
    fun handstandHoldVariants_areHoldBased() {
        listOf(
            DrillType.FREE_HANDSTAND,
            DrillType.WALL_HANDSTAND,
        ).forEach { drillType ->
            assertEquals(RepMode.HOLD_BASED, DrillCatalog.byType(drillType).repMode)
        }
    }
}
