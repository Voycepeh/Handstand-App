package com.inversioncoach.app.motion

data class LiveCue(
    val text: String,
    val code: String,
    val issuedAtMs: Long,
)

class FeedbackEngine(
    private val cooldownMs: Long = 1800,
) {
    private var lastCueAtMs = 0L
    private var lastCueCode = ""

    fun selectCue(faults: List<FaultEvent>, nowMs: Long): LiveCue? {
        val top = faults.maxByOrNull { it.severity.ordinal } ?: return null
        if (nowMs - lastCueAtMs < cooldownMs && top.code == lastCueCode) return null

        lastCueAtMs = nowMs
        lastCueCode = top.code
        return LiveCue(top.message, top.code, nowMs)
    }
}
