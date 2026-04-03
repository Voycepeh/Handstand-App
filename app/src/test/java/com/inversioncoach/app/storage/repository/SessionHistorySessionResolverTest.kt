package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionHistorySessionResolverTest {
    @Test
    fun resolvedDrillIdUsesExplicitDrillIdFirst() {
        val session = session(drillId = "explicit_drill", metricsJson = "drillId:fallback_drill")
        assertEquals("explicit_drill", session.resolvedDrillId())
    }

    @Test
    fun resolvedDrillIdFallsBackToMetricsToken() {
        val session = session(drillId = null, metricsJson = "foo:bar|drillId:metric_drill")
        assertEquals("metric_drill", session.resolvedDrillId())
    }

    @Test
    fun filterForDrillTreatsBlankAsAllSessions() {
        val sessions = listOf(
            session(id = 1L, drillId = "a"),
            session(id = 2L, drillId = "b"),
        )
        assertEquals(listOf(1L, 2L), sessions.filterForDrill("").map { it.id })
        assertEquals(listOf(1L), sessions.filterForDrill("a").map { it.id })
    }

    @Test
    fun parseInlineMetricReturnsNullWhenMissing() {
        assertNull("nope:value".parseInlineMetric("drillId"))
    }

    private fun session(
        id: Long = 7L,
        drillId: String? = null,
        metricsJson: String = "",
    ): SessionRecord = SessionRecord(
        id = id,
        title = "session",
        drillType = DrillType.FREESTYLE_HANDSTAND,
        startedAtMs = 100L,
        completedAtMs = 200L,
        overallScore = 0,
        strongestArea = "",
        limitingFactor = "",
        issues = "",
        wins = "",
        metricsJson = metricsJson,
        annotatedVideoUri = null,
        rawVideoUri = null,
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "",
        drillId = drillId,
    )
}
