package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.AnnotatedExportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionVideoOutcomeTest {

    @Test
    fun failedAnnotatedExportKeepsRawAndMarksFailed() {
        val outcome = resolveSessionVideoOutcome(
            rawVideoUri = "file:///raw.mp4",
            annotatedVideoUri = null,
            exportStatus = AnnotatedExportStatus.FAILED,
        )

        assertEquals("file:///raw.mp4", outcome.rawVideoUri)
        assertNull(outcome.annotatedVideoUri)
        assertEquals(AnnotatedExportStatus.FAILED, outcome.annotatedExportStatus)
    }
}
