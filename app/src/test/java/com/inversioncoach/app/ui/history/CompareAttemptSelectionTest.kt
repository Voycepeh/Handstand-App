package com.inversioncoach.app.ui.history

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareAttemptSelectionTest {
    @Test
    fun usesExistingComparedSessionAsAnchorWhenPresent() {
        val sessions = listOf(session(id = 1L, startedAtMs = 100L), session(id = 2L, startedAtMs = 200L))
        val selection = selectCompareAttemptTargets(sessions, comparedSessionIds = listOf(1L))
        assertEquals(1L, selection.anchorSessionId)
        assertTrue(selection.hasEnoughCandidates)
    }

    @Test
    fun fallsBackToMostRecentSessionWhenNoSavedAnchor() {
        val sessions = listOf(session(id = 1L, startedAtMs = 100L), session(id = 2L, startedAtMs = 200L))
        val selection = selectCompareAttemptTargets(sessions, comparedSessionIds = emptyList())
        assertEquals(2L, selection.anchorSessionId)
    }

    @Test
    fun reportsNotEnoughCandidatesWhenSingleSession() {
        val selection = selectCompareAttemptTargets(listOf(session(id = 1L, startedAtMs = 200L)), comparedSessionIds = emptyList())
        assertFalse(selection.hasEnoughCandidates)
    }

    private fun session(id: Long, startedAtMs: Long): SessionRecord = SessionRecord(
        id = id,
        title = "session-$id",
        drillType = DrillType.FREESTYLE_HANDSTAND,
        startedAtMs = startedAtMs,
        completedAtMs = startedAtMs + 1_000L,
        overallScore = 0,
        strongestArea = "",
        limitingFactor = "",
        issues = "",
        wins = "",
        metricsJson = "",
        annotatedVideoUri = null,
        rawVideoUri = null,
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "",
    )
}
