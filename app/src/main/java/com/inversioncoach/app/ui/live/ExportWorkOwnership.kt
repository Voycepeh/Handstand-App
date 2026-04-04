package com.inversioncoach.app.ui.live


object ExportWorkOwnership {
    /**
     * Uploaded-video analysis is now in-app owned and non-durable, so only active in-process
     * annotated export jobs are considered during stale-state recovery.
     */
    fun activeSessionIds(): Set<Long> = AnnotatedExportJobTracker.activeSessionIds()

    fun hasActiveExportWork(sessionId: Long): Boolean = activeSessionIds().contains(sessionId)
}
