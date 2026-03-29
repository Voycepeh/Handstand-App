package com.inversioncoach.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DrillStudioRouteTest {
    @Test
    fun drillStudioRouteIsStable() {
        assertEquals("drill-studio?mode={mode}&drillId={drillId}", Route.DrillStudio.value)
    }

    @Test
    fun drillStudioCreateRoutes() {
        assertEquals("drill-studio?mode=create&drillId=", Route.DrillStudio.createNew())
        assertEquals("drill-studio?mode=drill&drillId=wall_handstand", Route.DrillStudio.createForDrill("wall_handstand"))
    }

    @Test
    fun drillStudioRouteEncodesDrillId() {
        assertEquals(
            "drill-studio?mode=drill&drillId=free%20handstand",
            Route.DrillStudio.createForDrill("free handstand"),
        )
    }
}
