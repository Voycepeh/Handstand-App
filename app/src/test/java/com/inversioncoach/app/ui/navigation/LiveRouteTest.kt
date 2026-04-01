package com.inversioncoach.app.ui.navigation

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.LiveSessionOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRouteTest {
    @Test
    fun liveRouteCarriesSelectedDrillId() {
        val route = Route.Live.create(
            drillType = DrillType.FREE_HANDSTAND,
            options = LiveSessionOptions(selectedDrillId = "drill custom/id"),
        )

        assertEquals(
            "live/FREE_HANDSTAND/true/true/true/true/true/true/LEFT/FREESTYLE?selectedDrillId=drill%20custom%2Fid",
            route,
        )
    }
}
