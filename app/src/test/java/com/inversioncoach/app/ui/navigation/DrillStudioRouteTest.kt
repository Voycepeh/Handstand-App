package com.inversioncoach.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DrillStudioRouteTest {
    @Test
    fun drillStudioRouteIsStable() {
        assertEquals("drill-studio", Route.DrillStudio.value)
    }
}
