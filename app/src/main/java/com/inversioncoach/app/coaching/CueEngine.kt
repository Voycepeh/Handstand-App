package com.inversioncoach.app.coaching

import com.inversioncoach.app.biomechanics.DrillModeConfig
import com.inversioncoach.app.model.AlignmentMetric
import com.inversioncoach.app.model.CoachingCue
import com.inversioncoach.app.model.CueStyle

class CueEngine {
    private val lastCueAt = mutableMapOf<String, Long>()
    private val issueStartAt = mutableMapOf<String, Long>()

    fun nextCue(
        config: DrillModeConfig,
        metrics: List<AlignmentMetric>,
        style: CueStyle,
        nowMs: Long = System.currentTimeMillis(),
        minSpacingMs: Long = 2000,
        persistMs: Long = 900,
    ): CoachingCue? {
        val sorted = metrics.sortedBy { it.score }
        val primary = sorted.firstOrNull() ?: return null
        val secondary = sorted.getOrNull(1)

        if (primary.score >= 85 && (secondary == null || secondary.score >= 82)) {
            return buildCue("encourage", styleText(style, "good"), nowMs, minSpacingMs, 1)
        }

        val issueKey = config.cuePriority.firstOrNull { wanted -> sorted.any { it.key == wanted } }
            ?: primary.key
        val chosen = sorted.firstOrNull { it.key == issueKey } ?: primary

        val issueStartedAt = issueStartAt.getOrPut(chosen.key) { nowMs }
        if (nowMs - issueStartedAt < persistMs) return null

        return when (chosen.key) {
            "scapular_elevation", "shoulder_push", "shoulder_openness" ->
                buildCue("shoulders", styleText(style, "shoulders"), nowMs, minSpacingMs, severity(chosen.score))

            "rib_pelvis_control", "reduced_arch" ->
                buildCue("ribs", styleText(style, "ribs"), nowMs, minSpacingMs, severity(chosen.score))

            "hip_stack", "hip_height", "line_retention", "line_quality" ->
                buildCue("hips", styleText(style, "hips"), nowMs, minSpacingMs, severity(chosen.score))

            "elbow_path" -> buildCue("elbows", "Keep elbows tracking", nowMs, minSpacingMs, severity(chosen.score))
            "tempo_control", "descent_control" -> buildCue("tempo", "Slow the descent", nowMs, minSpacingMs, severity(chosen.score))
            else -> buildCue("general", "Stay stacked", nowMs, minSpacingMs, severity(chosen.score))
        }
    }

    private fun styleText(style: CueStyle, focus: String): String = when (focus) {
        "good" -> when (style) {
            CueStyle.CONCISE -> "Good line, hold"
            CueStyle.TECHNICAL -> "Stable alignment. Maintain stack."
            CueStyle.ENCOURAGING -> "Better, hold that"
        }

        "shoulders" -> when (style) {
            CueStyle.CONCISE -> "Push taller through shoulders"
            CueStyle.TECHNICAL -> "Actively elevate shoulders and keep open angle."
            CueStyle.ENCOURAGING -> "Strong effort, push taller"
        }

        "ribs" -> when (style) {
            CueStyle.CONCISE -> "Tuck ribs"
            CueStyle.TECHNICAL -> "Reduce rib flare; keep pelvis controlled."
            CueStyle.ENCOURAGING -> "Nice, now ribs in"
        }

        else -> when (style) {
            CueStyle.CONCISE -> "Bring hips over hands"
            CueStyle.TECHNICAL -> "Shift hips toward vertical stack over wrists."
            CueStyle.ENCOURAGING -> "Small hip adjustment"
        }
    }

    private fun buildCue(id: String, text: String, nowMs: Long, minSpacingMs: Long, severity: Int): CoachingCue? {
        val last = lastCueAt[id] ?: 0L
        if (nowMs - last < minSpacingMs) return null
        lastCueAt[id] = nowMs
        return CoachingCue(id = id, text = text, severity = severity, generatedAtMs = nowMs)
    }

    private fun severity(score: Int): Int = ((100 - score) / 20).coerceIn(1, 5)
}
