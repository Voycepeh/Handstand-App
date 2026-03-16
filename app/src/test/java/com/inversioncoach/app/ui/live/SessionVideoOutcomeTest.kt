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
            exportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
        )

        assertEquals("file:///raw.mp4", outcome.rawVideoUri)
        assertNull(outcome.annotatedVideoUri)
        assertEquals(AnnotatedExportStatus.ANNOTATED_FAILED, outcome.annotatedExportStatus)
    }
    @Test
    fun processingAnnotatedExportWithRawKeepsProcessing() {
        val outcome = resolveSessionVideoOutcome(
            rawVideoUri = "file:///raw.mp4",
            annotatedVideoUri = null,
            exportStatus = AnnotatedExportStatus.PROCESSING,
        )

        assertEquals(AnnotatedExportStatus.PROCESSING, outcome.annotatedExportStatus)
    }

    @Test
    fun slowProcessingAnnotatedExportWithRawKeepsSlowProcessing() {
        val outcome = resolveSessionVideoOutcome(
            rawVideoUri = "file:///raw.mp4",
            annotatedVideoUri = null,
            exportStatus = AnnotatedExportStatus.PROCESSING_SLOW,
        )

        assertEquals(AnnotatedExportStatus.PROCESSING_SLOW, outcome.annotatedExportStatus)
    }

}
