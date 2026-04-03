package com.inversioncoach.app.ui.upload

import android.net.Uri
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
}
