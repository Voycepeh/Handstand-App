package com.inversioncoach.app.media

import android.net.Uri
import android.test.mock.MockContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class SessionMediaSourceOpenerTest {

    @Test
    fun resolveUri_handlesContentUri() {
        val opener = SessionMediaSourceOpener(context = MockContext())

        val resolved = opener.resolveUri("content://media/external/video/media/42")

        assertEquals("content", resolved?.scheme)
        assertEquals("content://media/external/video/media/42", resolved.toString())
    }

    @Test
    fun openInputStream_handlesFileUri() {
        val file = File.createTempFile("opener", ".mp4")
        file.writeText("hello")
        val opener = SessionMediaSourceOpener(context = MockContext())

        val stream = opener.openInputStream(file.toURI().toString())

        assertNotNull(stream)
        assertEquals("hello", stream!!.bufferedReader().readText())
        file.delete()
    }

    @Test
    fun openInputStream_handlesPlainAbsolutePath() {
        val file = File.createTempFile("opener_path", ".mp4")
        file.writeText("plain")
        val opener = SessionMediaSourceOpener(context = MockContext())

        val stream = opener.openInputStream(file.absolutePath)

        assertNotNull(stream)
        assertEquals("plain", stream!!.bufferedReader().readText())
        file.delete()
    }

    @Test
    fun openInputStream_handlesContentUriWithInjectedResolver() {
        val opener = SessionMediaSourceOpener(
            context = MockContext(),
            openContentInputStream = { uri: Uri ->
                if (uri.toString() == "content://app/session/1") ByteArrayInputStream("content".toByteArray()) else null
            },
        )

        val stream = opener.openInputStream("content://app/session/1")

        assertEquals("content", stream!!.bufferedReader().readText())
    }

    @Test
    fun resolverAndOpenerStayAligned_forPlainPath() {
        val file = File.createTempFile("resolver_alignment", ".mp4")
        file.writeText("video")
        val opener = SessionMediaSourceOpener(context = MockContext())
        val resolver = SessionMediaResolver(assetExists = opener::isReadable)

        val result = resolver.resolve(session(rawUri = file.absolutePath, annotatedUri = null))

        val raw = result.raw as SessionArtifact.Available
        assertTrue(opener.isReadable(raw.uri))
        file.delete()
    }

    private fun session(rawUri: String?, annotatedUri: String?) = SessionMediaResolverTest.sessionForTest(
        rawUri = rawUri,
        annotatedUri = annotatedUri,
    )
}
