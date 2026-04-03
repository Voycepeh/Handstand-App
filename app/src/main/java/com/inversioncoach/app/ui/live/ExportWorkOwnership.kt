package com.inversioncoach.app.ui.live

import com.inversioncoach.app.ui.upload.UploadJobCoordinator

object ExportWorkOwnership {
    /**
     * Live and uploaded exports use different in-memory trackers, so stale-recovery must consult both
     * to avoid falsely terminalizing legitimate in-flight export work.
     */
    fun activeSessionIds(): Set<Long> = buildSet {
        UploadJobCoordinator.currentSessionId()?.let { add(it) }
        addAll(AnnotatedExportJobTracker.activeSessionIds())
    }

    fun hasActiveExportWork(sessionId: Long): Boolean = activeSessionIds().contains(sessionId)
}
