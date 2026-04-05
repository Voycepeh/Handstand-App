package com.inversioncoach.app.ui.live


object ExportWorkOwnership {
    /**
     * Uploaded-video analysis is now app-owned and durable via WorkManager;
     * this ownership helper remains scoped to annotated export jobs only.
     */
    fun activeSessionIds(): Set<Long> = AnnotatedExportJobTracker.activeSessionIds()

    fun hasActiveExportWork(sessionId: Long): Boolean = activeSessionIds().contains(sessionId)
}
