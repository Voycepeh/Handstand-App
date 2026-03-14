package com.inversioncoach.app.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueEventAggregatorTest {

    @Test
    fun mergesConsecutiveFaultsAndDebouncesTransientNoise() {
        val aggregator = IssueEventAggregator(minDurationMs = 600L, maxGapMs = 250L)

        aggregator.onIssue(ts = 1_000L, issue = "passive shoulders", severity = 1, cue = "Push tall")
        aggregator.onIssue(ts = 1_150L, issue = "passive shoulders", severity = 2, cue = null)
        aggregator.onIssue(ts = 1_280L, issue = "passive shoulders", severity = 1, cue = null)

        // transient event (too short) should be ignored
        aggregator.onIssue(ts = 2_000L, issue = "head forward", severity = 1, cue = "Neutral neck")

        val events = aggregator.flushAll(endMs = 2_100L)

        assertEquals(1, events.size)
        val event = events.first()
        assertEquals("passive shoulders", event.issue)
        assertEquals(1_000L, event.startMs)
        assertTrue(event.endMs >= 1_280L)
        assertEquals(2, event.peakSeverity)
        assertEquals("Push tall", event.representativeCue)
    }
}
