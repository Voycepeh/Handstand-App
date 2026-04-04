package com.inversioncoach.app.ui.common

import com.inversioncoach.app.media.SessionMediaOwnership
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource

/**
 * Results routing is always allowed for live sessions, but uploaded sessions
 * should only enter normal Results flow after a terminal completed outcome.
 */
fun SessionRecord.canOpenResultsRoute(): Boolean {
    if (sessionSource != SessionSource.UPLOADED_VIDEO) return true
    if (annotatedExportStatus == AnnotatedExportStatus.ANNOTATED_READY) return true
    val rawOnlyTerminal = annotatedExportStatus in setOf(
        AnnotatedExportStatus.ANNOTATED_FAILED,
        AnnotatedExportStatus.CANCELLED,
        AnnotatedExportStatus.SKIPPED,
    )
    return rawOnlyTerminal && SessionMediaOwnership.rawReplayPlayable(this)
}
