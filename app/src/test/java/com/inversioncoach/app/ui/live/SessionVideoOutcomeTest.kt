package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.AnnotatedExportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SessionVideoOutcomeTest {

    @Test
    fun failedAnnotatedExportKeepsRawAndMarksFailed() {
        val rawFile = File.createTempFile("raw_outcome", ".mp4")
        try {
            rawFile.writeText("raw")
            val outcome = resolveSessionVideoOutcome(
                rawVideoUri = rawFile.toURI().toString(),
                annotatedVideoUri = null,
            )

            assertEquals(rawFile.toURI().toString(), outcome.rawVideoUri)
            assertNull(outcome.annotatedVideoUri)
            assertEquals(AnnotatedExportStatus.FAILED, outcome.annotatedExportStatus)
        } finally {
            rawFile.delete()
        }
    }

    @Test
    fun processingStateNeverPersistsAsFinalOutcome() {
        val outcome = resolveSessionVideoOutcome(
            rawVideoUri = null,
            annotatedVideoUri = null,
        )

        assertEquals(AnnotatedExportStatus.FAILED, outcome.annotatedExportStatus)
    }
}
