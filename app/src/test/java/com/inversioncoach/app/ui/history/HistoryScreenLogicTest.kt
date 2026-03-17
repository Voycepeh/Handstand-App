package com.inversioncoach.app.ui.history

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryScreenLogicTest {

    @Test
    fun historyCardDurationTextRendersPersistedDuration() {
        val session = SessionRecord(
            title = "Freestyle Live Coaching session",
            drillType = DrillType.FREESTYLE_HANDSTAND,
            startedAtMs = 1_000L,
            completedAtMs = 44_000L,
            overallScore = 0,
            strongestArea = "-",
            limitingFactor = "-",
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

        assertEquals("Duration: 00:43", historyCardDurationText(session))
    }
}
