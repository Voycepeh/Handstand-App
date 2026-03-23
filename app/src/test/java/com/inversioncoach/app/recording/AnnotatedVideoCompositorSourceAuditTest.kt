package com.inversioncoach.app.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnnotatedVideoCompositorSourceAuditTest {

    private fun source(): String {
        val path = File("src/main/java/com/inversioncoach/app/recording/AnnotatedVideoCompositor.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/inversioncoach/app/recording/AnnotatedVideoCompositor.kt")
        return path.readText()
    }

    @Test
    fun exportPathDoesNotUseRetrieverOrImageReaderBitmapPrimaryComposition() {
        val exportSection = source().substringBefore("private fun verifyReadableOutput")
        assertFalse(exportSection.contains("getFrameAtTime("))
        assertFalse(exportSection.contains("ImageReader"))
        assertFalse(exportSection.contains("acquireLatestImage("))
        assertFalse(exportSection.contains("imageToBitmap("))
    }

    @Test
    fun exportPathUsesDecoderPtsAndEglPresentationTime() {
        val exportSection = source().substringBefore("private fun verifyReadableOutput")
        assertTrue(exportSection.contains("decoderInfo.presentationTimeUs"))
        assertTrue(exportSection.contains("EGLExt.eglPresentationTimeANDROID"))
    }

    @Test
    fun exportPathIsDecodeDrivenNotSyntheticFrameLoopDriven() {
        val exportSection = source().substringBefore("private fun verifyReadableOutput")
        assertFalse(exportSection.contains("for (idx in 0 until"))
    }

    @Test
    fun compositorUsesFrameAvailableListenerNotPollingSleepLoop() {
        val src = source()
        assertTrue(src.contains("setOnFrameAvailableListener"))
        assertFalse(src.contains("Thread.sleep("))
    }

    @Test
    fun muxStopIsGuardedAgainstDoubleStop() {
        val src = source()
        assertTrue(src.contains("var muxStopped = false"))
        assertTrue(src.contains("if (muxStarted && !muxStopped)"))
    }

    @Test
    fun glEglInitValidationIsExplicit() {
        val src = source()
        assertTrue(src.contains("eglInitialize"))
        assertTrue(src.contains("eglChooseConfig"))
        assertTrue(src.contains("glGetShaderiv"))
        assertTrue(src.contains("glGetProgramiv"))
    }

    @Test
    fun videoPathUsesTexMatrixOnlyAndDiagnosticsLogging() {
        val src = source()
        assertTrue(src.contains("private val videoTex: FloatBuffer = createOverlayTextureCoordinateBuffer()"))
        assertTrue(src.contains("createOverlayTextureCoordinateBuffer()"))
        assertTrue(src.contains("glUniformMatrix4fv(matrixLoc, 1, false, texMatrix, 0)"))
        assertTrue(src.contains("export_diagnostics_texture_transform"))
    }
}
