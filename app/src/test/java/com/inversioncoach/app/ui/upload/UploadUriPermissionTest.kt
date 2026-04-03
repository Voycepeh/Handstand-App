package com.inversioncoach.app.ui.upload

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadUriPermissionTest {
    @Test
    fun persistPermissionOnlyForContentUris() {
        assertTrue(shouldPersistReadPermission(Uri.parse("content://media/external/video/123")))
        assertFalse(shouldPersistReadPermission(Uri.parse("file:///sdcard/Movies/input.mp4")))
        assertFalse(shouldPersistReadPermission(Uri.parse("https://example.com/video.mp4")))
    }

    @Test
    fun uploadUriSchemeValidationAcceptsSafeLocalSources() {
        assertTrue(isSupportedUploadUri(Uri.parse("content://media/external/video/123")))
        assertTrue(isSupportedUploadUri(Uri.parse("file:///sdcard/Movies/input.mp4")))
        assertFalse(isSupportedUploadUri(Uri.parse("https://example.com/video.mp4")))
    }

    @Test
    fun sanitizeUploadFrameRateFallsBackForInvalidValues() {
        assertEquals(30, sanitizeUploadFrameRate(0))
        assertEquals(30, sanitizeUploadFrameRate(999))
        assertEquals(24, sanitizeUploadFrameRate(24))
    }
}
