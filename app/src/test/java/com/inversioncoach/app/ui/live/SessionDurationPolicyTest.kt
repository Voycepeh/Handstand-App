package com.inversioncoach.app.ui.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDurationPolicyTest {

    @Test
    fun shortSessionRejectedWhenBelowMinimumThreshold() {
        assertTrue(shouldDiscardSessionForShortDuration(elapsedSessionMs = 2_500L, minSessionDurationSeconds = 3))
    }

    @Test
    fun validSessionSavedWhenMeetingMinimumThreshold() {
        assertFalse(shouldDiscardSessionForShortDuration(elapsedSessionMs = 3_000L, minSessionDurationSeconds = 3))
    }
}
