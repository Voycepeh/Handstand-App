package com.inversioncoach.app.ui.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveOverlayProjectionSourceAuditTest {

    private fun source(): String {
        val path = File("src/main/java/com/inversioncoach/app/overlay/OverlayRenderer.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/inversioncoach/app/overlay/OverlayRenderer.kt")
        return path.readText()
    }

    @Test
    fun livePreviewOverlayProjectionDoesNotApplyAnalyzerRotationTwice() {
        val src = source()
        assertTrue(src.contains("sourceRotationDegrees = 0"))
        assertFalse(src.contains("sourceRotationDegrees = frame?.analysisRotationDegrees"))
    }
}
