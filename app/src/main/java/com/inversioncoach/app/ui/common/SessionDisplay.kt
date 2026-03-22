package com.inversioncoach.app.ui.common

import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.sessionMode
import java.text.DateFormat
import java.util.Date

private const val NOT_TRACKED = "Not tracked"

data class SessionSummaryDisplay(
    val wins: String,
    val issues: String,
    val improvement: String,
)

data class SessionMetrics(
    val trackingMode: String? = null,
    val validReps: Int? = null,
    val rawRepAttempts: Int? = null,
    val alignedDurationMs: Long? = null,
    val bestAlignedStreakMs: Long? = null,
    val sessionTrackedMs: Long? = null,
    val alignmentRate: Float? = null,
    val avgAlignment: Int? = null,
    val avgStability: Int? = null,
    val acceptedReps: Int? = null,
    val rejectedReps: Int? = null,
    val avgRepScore: Int? = null,
    val bestRepScore: Int? = null,
    val repFailureReason: String? = null,
)

fun formatSessionDateTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "-"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMs))
}

fun formatSessionDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun computeSessionDurationMs(startedAtMs: Long, completedAtMs: Long): Long {
    if (startedAtMs <= 0L || completedAtMs <= 0L) return 0L
    return (completedAtMs - startedAtMs).coerceAtLeast(0L)
}

fun parseSessionMetrics(metricsJson: String): SessionMetrics {
    val pairs = metricsJson.split('|')
        .mapNotNull { token ->
            val idx = token.indexOf(':')
            if (idx <= 0) null else token.substring(0, idx) to token.substring(idx + 1)
        }
        .toMap()
    return SessionMetrics(
        trackingMode = pairs["trackingMode"],
        validReps = pairs["validReps"]?.toIntOrNull(),
        rawRepAttempts = pairs["rawRepAttempts"]?.toIntOrNull(),
        alignedDurationMs = pairs["alignedDurationMs"]?.toLongOrNull(),
        bestAlignedStreakMs = pairs["bestAlignedStreakMs"]?.toLongOrNull(),
        sessionTrackedMs = pairs["sessionTrackedMs"]?.toLongOrNull(),
        alignmentRate = pairs["alignmentRate"]?.toFloatOrNull(),
        avgAlignment = pairs["avgAlignment"]?.toIntOrNull(),
        avgStability = pairs["avgStability"]?.toIntOrNull(),
        acceptedReps = pairs["acceptedReps"]?.toIntOrNull(),
        rejectedReps = pairs["rejectedReps"]?.toIntOrNull(),
        avgRepScore = pairs["avgRepScore"]?.toIntOrNull(),
        bestRepScore = pairs["bestRepScore"]?.toIntOrNull(),
        repFailureReason = pairs["repFailureReason"],
    )
}

fun formatPrimaryPerformance(session: SessionRecord): String {
    val metrics = parseSessionMetrics(session.metricsJson)
    if (session.sessionSource == SessionSource.UPLOADED_VIDEO) {
        val hasHoldMetrics =
            metrics.alignedDurationMs != null &&
                metrics.bestAlignedStreakMs != null &&
                metrics.sessionTrackedMs != null &&
                metrics.alignmentRate != null &&
                metrics.avgStability != null
        val hasRepMetrics =
            (metrics.acceptedReps != null || metrics.validReps != null) &&
                metrics.rawRepAttempts != null &&
                metrics.rejectedReps != null &&
                metrics.avgRepScore != null
        return when {
            metrics.trackingMode == "HOLD_BASED" && hasHoldMetrics -> {
                val aligned = formatSessionDuration(metrics.alignedDurationMs ?: 0L)
                val best = formatSessionDuration(metrics.bestAlignedStreakMs ?: 0L)
                val total = formatSessionDuration(metrics.sessionTrackedMs ?: computeSessionDurationMs(session.startedAtMs, session.completedAtMs))
                "Hold: $aligned aligned • Best streak: $best • Session: $total • Align ${(((metrics.alignmentRate ?: 0f) * 100f).toInt())}% • Stability ${metrics.avgStability ?: 0}"
            }

            metrics.trackingMode == "REP_BASED" && hasRepMetrics -> {
                val accepted = metrics.acceptedReps ?: metrics.validReps ?: 0
                val rejected = metrics.rejectedReps ?: 0
                val raw = metrics.rawRepAttempts ?: (accepted + rejected)
                "Reps: $accepted accepted / $raw attempts • Rejected: $rejected • Avg rep ${metrics.avgRepScore ?: 0}"
            }

            session.annotatedExportStatus in setOf(
                AnnotatedExportStatus.NOT_STARTED,
                AnnotatedExportStatus.VALIDATING_INPUT,
                AnnotatedExportStatus.PROCESSING,
                AnnotatedExportStatus.PROCESSING_SLOW,
            ) -> "Upload analysis is still processing."

            session.annotatedExportStatus in setOf(
                AnnotatedExportStatus.ANNOTATED_FAILED,
                AnnotatedExportStatus.SKIPPED,
            ) -> "Upload analysis did not produce scored attempts."

            else -> "Upload analysis metrics are unavailable."
        }
    }
    return if (metrics.trackingMode == "HOLD_BASED") {
        val aligned = formatSessionDuration(metrics.alignedDurationMs ?: 0L)
        val best = formatSessionDuration(metrics.bestAlignedStreakMs ?: 0L)
        val total = formatSessionDuration(metrics.sessionTrackedMs ?: computeSessionDurationMs(session.startedAtMs, session.completedAtMs))
        "Hold: $aligned aligned • Best streak: $best • Session: $total • Align ${(((metrics.alignmentRate ?: 0f) * 100f).toInt())}% • Stability ${metrics.avgStability ?: 0}"
    } else {
        val accepted = metrics.acceptedReps ?: metrics.validReps ?: 0
        val rejected = metrics.rejectedReps ?: 0
        val raw = metrics.rawRepAttempts ?: (accepted + rejected)
        "Reps: $accepted accepted / $raw attempts • Rejected: $rejected • Avg rep ${metrics.avgRepScore ?: 0}"
    }
}

fun formatLimiterText(session: SessionRecord?): String {
    if (session == null) return "-"
    return when (session.sessionMode()) {
        SessionMode.FREESTYLE -> NOT_TRACKED
        SessionMode.DRILL -> session.limitingFactor
    }
}

fun buildSessionSummaryDisplay(session: SessionRecord?): SessionSummaryDisplay {
    if (session == null) {
        return SessionSummaryDisplay(
            wins = "No wins captured yet",
            issues = "No issues captured",
            improvement = "-",
        )
    }
    return when (session.sessionMode()) {
        SessionMode.FREESTYLE -> SessionSummaryDisplay(
            wins = NOT_TRACKED,
            issues = NOT_TRACKED,
            improvement = NOT_TRACKED,
        )

        SessionMode.DRILL -> SessionSummaryDisplay(
            wins = session.wins.ifBlank { "No wins captured yet" },
            issues = session.issues.ifBlank { "No issues captured" },
            improvement = session.topImprovementFocus.ifBlank { "-" },
        )
    }
}
