package com.inversioncoach.app.ui.history

import com.inversioncoach.app.model.SessionRecord

internal data class CompareAttemptSelection(
    val candidateSessionIds: Set<Long>,
    val anchorSessionId: Long?,
    val hasEnoughCandidates: Boolean,
)

internal fun selectCompareAttemptTargets(
    sessions: List<SessionRecord>,
    comparedSessionIds: List<Long>,
): CompareAttemptSelection {
    val candidates = sessions.sortedByDescending { it.startedAtMs }
    val candidateIds = candidates.map { it.id }.toSet()
    val anchor = comparedSessionIds.firstOrNull { candidateIds.contains(it) } ?: candidates.firstOrNull()?.id
    return CompareAttemptSelection(
        candidateSessionIds = candidateIds,
        anchorSessionId = anchor,
        hasEnoughCandidates = candidates.size >= 2,
    )
}
