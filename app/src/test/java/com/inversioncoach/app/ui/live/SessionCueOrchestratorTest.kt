package com.inversioncoach.app.ui.live

import com.google.common.truth.Truth.assertThat
import com.inversioncoach.app.model.CoachingCue
import com.inversioncoach.app.model.SessionStartupState
import org.junit.Test

class SessionCueOrchestratorTest {

    private val orchestrator = SessionCueOrchestrator(duplicateCueGuardMs = 800L)

    @Test
    fun countdownCueOnlyEmitsDuringCountdownAndPositiveRemaining() {
        val invalidState = orchestrator.countdownCue(
            startupState = SessionStartupState.IDLE,
            remainingSeconds = 3,
            nowMs = 1000L,
        )
        val zeroRemaining = orchestrator.countdownCue(
            startupState = SessionStartupState.COUNTDOWN,
            remainingSeconds = 0,
            nowMs = 1000L,
        )
        val valid = orchestrator.countdownCue(
            startupState = SessionStartupState.COUNTDOWN,
            remainingSeconds = 3,
            nowMs = 1000L,
        )

        assertThat(invalidState).isNull()
        assertThat(zeroRemaining).isNull()
        assertThat(valid?.id).isEqualTo("session_countdown_3")
        assertThat(valid?.text).isEqualTo("3")
    }

    @Test
    fun sessionStartedCueEmitsOncePerStartup() {
        val first = orchestrator.sessionStartedCue(
            startupState = SessionStartupState.ACTIVE,
            nowMs = 1000L,
        )
        val second = orchestrator.sessionStartedCue(
            startupState = SessionStartupState.ACTIVE,
            nowMs = 2000L,
        )

        orchestrator.resetForStartup()
        val afterReset = orchestrator.sessionStartedCue(
            startupState = SessionStartupState.ACTIVE,
            nowMs = 3000L,
        )

        assertThat(first?.id).isEqualTo("session_initiated")
        assertThat(second).isNull()
        assertThat(afterReset?.id).isEqualTo("session_initiated")
    }

    @Test
    fun activeSessionCueEnforcesStateAndDuplicateGuard() {
        val cue = CoachingCue(id = "hips", text = "Bring hips over hands", severity = 2, generatedAtMs = 1000L)

        val inactive = orchestrator.activeSessionCue(
            cue = cue,
            startupState = SessionStartupState.COUNTDOWN,
            nowMs = 1000L,
        )
        val first = orchestrator.activeSessionCue(
            cue = cue,
            startupState = SessionStartupState.ACTIVE,
            nowMs = 1000L,
        )
        val duplicateBlocked = orchestrator.activeSessionCue(
            cue = cue.copy(generatedAtMs = 1100L),
            startupState = SessionStartupState.ACTIVE,
            nowMs = 1500L,
        )
        val afterGuard = orchestrator.activeSessionCue(
            cue = cue.copy(generatedAtMs = 2200L),
            startupState = SessionStartupState.ACTIVE,
            nowMs = 2200L,
        )

        assertThat(inactive).isNull()
        assertThat(first?.id).isEqualTo("hips")
        assertThat(duplicateBlocked).isNull()
        assertThat(afterGuard?.id).isEqualTo("hips")
    }
}
