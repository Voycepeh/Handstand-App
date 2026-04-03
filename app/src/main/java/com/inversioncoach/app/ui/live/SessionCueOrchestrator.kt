package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.CoachingCue
import com.inversioncoach.app.model.SessionStartupState

/**
 * Centralizes speech cue gating for startup countdown and active-session coaching.
 * Playback layers should deliver cues only, without business rules.
 */
class SessionCueOrchestrator(
    private val duplicateCueGuardMs: Long = DUPLICATE_CUE_GUARD_MS,
) {
    private var lastSpokenCueId: String? = null
    private var lastSpokenAtMs: Long = 0L
    private var startupAnnounced = false

    fun resetForStartup() {
        startupAnnounced = false
        lastSpokenCueId = null
        lastSpokenAtMs = 0L
    }

    fun countdownCue(
        startupState: SessionStartupState,
        remainingSeconds: Int?,
        nowMs: Long = System.currentTimeMillis(),
    ): CoachingCue? {
        if (startupState != SessionStartupState.COUNTDOWN) return null
        val remaining = remainingSeconds ?: return null
        if (remaining <= 0) return null
        val cue = CoachingCue(
            id = "session_countdown_$remaining",
            text = remaining.toString(),
            severity = 0,
            generatedAtMs = nowMs,
        )
        return gate(cue, nowMs)
    }

    fun sessionStartedCue(
        startupState: SessionStartupState,
        nowMs: Long = System.currentTimeMillis(),
    ): CoachingCue? {
        if (startupState != SessionStartupState.ACTIVE || startupAnnounced) return null
        val cue = CoachingCue(
            id = SESSION_STARTED_CUE_ID,
            text = SESSION_STARTED_CUE_TEXT,
            severity = 0,
            generatedAtMs = nowMs,
        )
        val accepted = gate(cue, nowMs) ?: return null
        startupAnnounced = true
        return accepted
    }

    fun activeSessionCue(
        cue: CoachingCue?,
        startupState: SessionStartupState,
        nowMs: Long = System.currentTimeMillis(),
    ): CoachingCue? {
        if (startupState != SessionStartupState.ACTIVE) return null
        val value = cue ?: return null
        return gate(value, nowMs)
    }

    private fun gate(cue: CoachingCue, nowMs: Long): CoachingCue? {
        if (lastSpokenCueId == cue.id && nowMs - lastSpokenAtMs < duplicateCueGuardMs) return null
        lastSpokenCueId = cue.id
        lastSpokenAtMs = nowMs
        return cue
    }

    companion object {
        private const val DUPLICATE_CUE_GUARD_MS = 800L
        private const val SESSION_STARTED_CUE_ID = "session_initiated"
        private const val SESSION_STARTED_CUE_TEXT = "Session initiated."
    }
}
