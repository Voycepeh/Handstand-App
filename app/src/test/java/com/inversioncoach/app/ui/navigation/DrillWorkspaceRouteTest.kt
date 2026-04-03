package com.inversioncoach.app.ui.navigation

import com.inversioncoach.app.ui.startdrill.StartDrillDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class DrillWorkspaceRouteTest {
    @Test
    fun startRouteSupportsLiveAndWorkspaceDestinations() {
        assertEquals("start?destination={destination}", Route.Start.value)
        assertEquals("start?destination=live", Route.Start.create(StartDrillDestination.LIVE))
        assertEquals("start?destination=workspace", Route.Start.create(StartDrillDestination.WORKSPACE))
    }

    @Test
    fun drillWorkspaceRouteIsStable() {
        assertEquals("drill-workspace/{drillId}", Route.DrillWorkspace.value)
    }

    @Test
    fun drillWorkspaceCreateEncodesDrillId() {
        assertEquals("drill-workspace/wall_handstand", Route.DrillWorkspace.create("wall_handstand"))
        assertEquals("drill-workspace/free%20handstand", Route.DrillWorkspace.create("free handstand"))
    }

    @Test
    fun drillScopedDestinationsRemainCanonical() {
        val drillId = "wall_handstand"
        assertEquals(
            "upload-video?drillId=wall_handstand&referenceTemplateId=&isReference=false&createNewDrillFromReference=false",
            Route.UploadVideoForDrill.create(drillId, null, false),
        )
        assertEquals(
            "session-history?drillId=wall_handstand&mode=history",
            Route.SessionHistory.create(drillId),
        )
        assertEquals(
            "session-history?drillId=wall_handstand&mode=compare",
            Route.SessionHistory.create(drillId, mode = SessionHistoryMode.COMPARE),
        )
    }
}
