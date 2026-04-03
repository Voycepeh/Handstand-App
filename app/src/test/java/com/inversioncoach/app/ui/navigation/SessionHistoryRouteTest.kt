package com.inversioncoach.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionHistoryRouteTest {
    @Test
    fun routePatternRemainsStable() {
        assertEquals(
            "session-history?drillId={drillId}&mode={mode}",
            SessionHistoryRoutes.routePattern,
        )
    }

    @Test
    fun modeParsingFallsBackToHistory() {
        assertEquals(SessionHistoryMode.HISTORY, SessionHistoryMode.fromRoute(null))
        assertEquals(SessionHistoryMode.HISTORY, SessionHistoryMode.fromRoute("unexpected"))
        assertEquals(SessionHistoryMode.COMPARE, SessionHistoryMode.fromRoute("compare"))
    }
}
