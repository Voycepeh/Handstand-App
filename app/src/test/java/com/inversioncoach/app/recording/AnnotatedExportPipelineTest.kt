package com.inversioncoach.app.recording

import com.inversioncoach.app.model.AnnotatedExportFailureReason
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.overlay.DrillCameraSide
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnnotatedExportPipelineTest {

    @Test
    fun marksFailedWhenOverlayFramesAreEmpty() {
        val statuses = mutableListOf<AnnotatedExportStatus>()
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///annotated.mp4" },
            updateExportStatus = { _, status -> statuses += status },
            renderAnnotatedVideo = { _, _, _, _, _ -> "file:///rendered.mp4" },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayFrames = emptyList(),
            )
        }

        assertNull(exported.persistedUri)
        assertEquals(AnnotatedExportFailureReason.OVERLAY_FRAMES_EMPTY.name, exported.failureReason)
        assertEquals(listOf(AnnotatedExportStatus.ANNOTATED_FAILED), statuses)
    }

    @Test
    fun successfulExportMarksReadyAndReturnsPersistedUri() {
        val statuses = mutableListOf<AnnotatedExportStatus>()
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, status -> statuses += status },
            renderAnnotatedVideo = { _, _, _, _, _ -> "file:///rendered_annotated.mp4" },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayFrames = listOf(testFrame(1000L)),
            )
        }

        assertEquals("file:///persisted_annotated.mp4", exported.persistedUri)
        assertNull(exported.failureReason)
        assertEquals(AnnotatedExportPipeline.VerificationStatus.PASSED, exported.verificationStatus)
        assertEquals(listOf(AnnotatedExportStatus.PROCESSING, AnnotatedExportStatus.ANNOTATED_READY), statuses)
    }

    @Test
    fun timeoutMapsToExportTimedOutReason() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            exportTimeoutMs = 25L,
            renderAnnotatedVideo = { _, _, _, _, _ ->
                delay(100L)
                "file:///rendered_annotated.mp4"
            },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayFrames = listOf(testFrame(1000L)),
            )
        }

        assertEquals(AnnotatedExportFailureReason.EXPORT_TIMED_OUT.name, exported.failureReason)
    }

    @Test
    fun exceptionsMapToTypedExceptionReason() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _ -> throw IllegalStateException("boom") },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayFrames = listOf(testFrame(1000L)),
            )
        }

        assertEquals("EXCEPTION_IllegalStateException", exported.failureReason)
    }

    @Test
    fun cancellationMapsToExportCancelledReason() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _ -> throw CancellationException("cancel") },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayFrames = listOf(testFrame(1000L)),
            )
        }

        assertEquals(AnnotatedExportFailureReason.EXPORT_CANCELLED.name, exported.failureReason)
    }

    @Test
    fun nullPersistedOutputFailsVerification() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> null },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _ -> "file:///rendered_annotated.mp4" },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayFrames = listOf(testFrame(1000L)),
            )
        }

        assertEquals(AnnotatedExportFailureReason.OUTPUT_URI_NULL.name, exported.failureReason)
    }

    @Test
    fun zeroBytePersistedOutputFailsVerification() {
        val zeroByte = File.createTempFile("annotated_zero", ".mp4")
        zeroByte.writeBytes(byteArrayOf())
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> zeroByte.toURI().toString() },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _ -> "file:///rendered_annotated.mp4" },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayFrames = listOf(testFrame(1000L)),
            )
        }

        assertEquals(AnnotatedExportFailureReason.OUTPUT_FILE_ZERO_BYTES.name, exported.failureReason)
        assertTrue(zeroByte.exists())
    }

    private fun testFrame(timestampMs: Long) = AnnotatedOverlayFrame(
        timestampMs = timestampMs,
        landmarks = emptyList(),
        smoothedLandmarks = emptyList(),
        confidence = 0.9f,
        sessionMode = SessionMode.DRILL,
        drillCameraSide = DrillCameraSide.LEFT,
        bodyVisible = true,
        showSkeleton = true,
        showIdealLine = true,
        mirrorMode = false,
    )
}
