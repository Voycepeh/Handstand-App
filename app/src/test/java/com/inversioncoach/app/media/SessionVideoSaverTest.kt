package com.inversioncoach.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionVideoSaverTest {

    @Test
    fun buildFileName_formatsRawNameWithTimestampAndSuffix() {
        val fileName = SessionVideoSaver.buildFileName(
            sessionName = "Wall handstand drill",
            timestampMs = 1704110400000L,
            type = SessionMediaType.RAW,
        )

        assertEquals("Wall_handstand_drill_2024-01-01_12-00-00_raw.mp4", fileName)
    }

    @Test
    fun buildFileName_formatsAnnotatedNameWithSanitizedSessionName() {
        val fileName = SessionVideoSaver.buildFileName(
            sessionName = "  !@#  ",
            timestampMs = 1704110400000L,
            type = SessionMediaType.ANNOTATED,
        )

        assertTrue(fileName.endsWith("_annotated.mp4"))
        assertTrue(fileName.startsWith("session_2024-01-01_12-00-00"))
    }
}
