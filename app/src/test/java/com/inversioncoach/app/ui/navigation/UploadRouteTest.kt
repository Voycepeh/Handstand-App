package com.inversioncoach.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadRouteTest {
    @Test
    fun uploadRouteIsStable() {
        assertEquals("upload-video", Route.UploadVideo.value)
    }
}
