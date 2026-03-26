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
import org.junit.Assert.assertFalse
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
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> ComposerResult("file:///rendered.mp4", null) },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(emptyList()),
            )
        }

        assertNull(exported.persistedUri)
        assertEquals(AnnotatedExportFailureReason.OVERLAY_TIMELINE_EMPTY.name, exported.failureReason)
        assertEquals(listOf(AnnotatedExportStatus.VALIDATING_INPUT, AnnotatedExportStatus.ANNOTATED_FAILED), statuses)
        assertFalse(exported.started)
    }

    @Test
    fun rawVideoMissingFailsBeforeExportStart() {
        val statuses = mutableListOf<AnnotatedExportStatus>()
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///annotated.mp4" },
            updateExportStatus = { _, status -> statuses += status },
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> ComposerResult("file:///rendered.mp4", null) },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertEquals(AnnotatedExportFailureReason.EXPORT_NOT_STARTED.name, exported.failureReason)
        assertFalse(exported.started)
        assertEquals(listOf(AnnotatedExportStatus.VALIDATING_INPUT, AnnotatedExportStatus.ANNOTATED_FAILED), statuses)
    }

    @Test
    fun successfulExportMarksReadyAndReturnsPersistedUri() {
        val statuses = mutableListOf<AnnotatedExportStatus>()
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, status -> statuses += status },
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> ComposerResult("file:///rendered_annotated.mp4", null) },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertEquals("file:///persisted_annotated.mp4", exported.persistedUri)
        assertNull(exported.failureReason)
        assertEquals(AnnotatedExportPipeline.VerificationStatus.PASSED, exported.verificationStatus)
        assertEquals(listOf(AnnotatedExportStatus.VALIDATING_INPUT, AnnotatedExportStatus.PROCESSING, AnnotatedExportStatus.ANNOTATED_READY), statuses)
    }

    @Test
    fun timeoutMapsToExportTimedOutReasonAfterWorkStarts() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            exportTimeoutMs = 25L,
            stallWindowMs = 1L,
            renderAnnotatedVideo = { _, _, _, _, _, _, onTelemetry ->
                onTelemetry(AnnotatedExportTelemetry(exportStartedAtMs = 1L, decoderInitializedAtMs = 2L))
                delay(100L)
                ComposerResult("file:///rendered_annotated.mp4", null)
            },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertEquals(AnnotatedExportFailureReason.EXPORT_TIMEOUT.name, exported.failureReason)
        assertTrue(exported.started)
    }

    @Test
    fun timeoutDoesNotStartBeforeExportWorkActuallyStarts() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            exportTimeoutMs = 25L,
            stallWindowMs = 1L,
            renderAnnotatedVideo = { _, _, _, _, _, _, _ ->
                delay(100L)
                ComposerResult("file:///rendered_annotated.mp4", null)
            },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertEquals("file:///persisted_annotated.mp4", exported.persistedUri)
        assertNull(exported.failureReason)
        assertFalse(exported.started)
    }


    @Test
    fun failureBeforeDecoderInitIsNotStarted() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _, _, _ ->
                ComposerResult(null, AnnotatedExportFailureReason.DECODER_INIT_FAILED.name)
            },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertEquals(AnnotatedExportFailureReason.DECODER_INIT_FAILED.name, exported.failureReason)
        assertFalse(exported.started)
    }

    @Test
    fun failureAfterDecoderInitIsMarkedStarted() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _, _, onTelemetry ->
                onTelemetry(AnnotatedExportTelemetry(exportStartedAtMs = 1L, decoderInitializedAtMs = 2L, firstFrameDecodedAtMs = 3L))
                ComposerResult(null, AnnotatedExportFailureReason.EXPORT_FAILED_AFTER_START.name)
            },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertEquals(AnnotatedExportFailureReason.EXPORT_FAILED_AFTER_START.name, exported.failureReason)
        assertTrue(exported.started)
    }

    @Test
    fun reportsExportStartedAndOverlayConsumedViaTelemetry() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _, _, onTelemetry ->
                onTelemetry(
                    AnnotatedExportTelemetry(
                        exportStartedAtMs = 10L,
                        decoderInitializedAtMs = 11L,
                        firstFrameDecodedAtMs = 12L,
                        decodedFrameCount = 15,
                        renderedFrameCount = 15,
                        firstFrameRenderedAtMs = 13L,
                        firstFrameEncodedAtMs = 14L,
                        encodedFrameCount = 15,
                        droppedFrameCount = 1,
                        overlayFramesAvailable = 6,
                        overlayFramesConsumed = 6,
                        outputBytesWritten = 1024L,
                        exportCompletedAtMs = 20L,
                    ),
                )
                ComposerResult("file:///rendered_annotated.mp4", null)
            },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertTrue(exported.started)
        assertEquals(6, exported.telemetry?.overlayFramesConsumed)
        assertEquals(15, exported.telemetry?.encodedFrameCount)
        assertEquals(13L, exported.telemetry?.firstFrameRenderedAtMs)
        assertEquals(14L, exported.telemetry?.firstFrameEncodedAtMs)
        assertEquals(1, exported.telemetry?.droppedFrameCount)
        assertEquals(1, exported.telemetry?.usableOverlayFrameCount)
    }

    @Test
    fun computeDynamicTimeoutScalesWithDurationAndResolution() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> ComposerResult("file:///rendered_annotated.mp4", null) },
        )

        val smallBudget = pipeline.computeDynamicTimeoutMs(
            rawDurationMs = 5_000L,
            width = 640,
            height = 480,
            overlayFrameCount = 20,
        )
        val largeBudget = pipeline.computeDynamicTimeoutMs(
            rawDurationMs = 60_000L,
            width = 1920,
            height = 1080,
            overlayFrameCount = 1_000,
        )

        assertTrue(largeBudget > smallBudget)
    }

    @Test
    fun watchdogDoesNotTimeoutWhenProgressAdvances() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            exportTimeoutMs = 40L,
            stallWindowMs = 40L,
            renderAnnotatedVideo = { _, _, _, _, _, onProgress, onTelemetry ->
                onTelemetry(AnnotatedExportTelemetry(exportStartedAtMs = 1L, decoderInitializedAtMs = 2L))
                repeat(3) { idx ->
                    delay(30L)
                    onProgress(idx + 1, 3)
                    onTelemetry(
                        AnnotatedExportTelemetry(
                            exportStartedAtMs = 1L,
                            decoderInitializedAtMs = 2L,
                            decodedFrameCount = idx + 1,
                            renderedFrameCount = idx + 1,
                            encodedFrameCount = idx + 1,
                            outputBytesWritten = (idx + 1) * 100L,
                        ),
                    )
                }
                ComposerResult("file:///rendered_annotated.mp4", null)
            },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L), testFrame(1100L), testFrame(1200L))),
            )
        }

        assertEquals("file:///persisted_annotated.mp4", exported.persistedUri)
        assertNull(exported.failureReason)
    }

    @Test
    fun frozenSnapshotOverlayCountUsedInTelemetry() {
        val callbackTelemetries = mutableListOf<AnnotatedExportTelemetry>()
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, timeline, _, _, onTelemetry ->
                val telemetry = AnnotatedExportTelemetry(
                    exportStartedAtMs = 1L,
                    decoderInitializedAtMs = 2L,
                    overlayFramesAvailable = timeline.frames.size,
                )
                onTelemetry(telemetry)
                callbackTelemetries += telemetry.copy()
                ComposerResult("file:///rendered_annotated.mp4", null)
            },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L), testFrame(1010L), testFrame(1020L))),
            )
        }

        assertTrue(callbackTelemetries.isNotEmpty())
        assertEquals(3, callbackTelemetries.last().overlayFramesAvailable)
        assertEquals(3, exported.telemetry?.usableOverlayFrameCount)
        assertEquals(exported.telemetry?.usableOverlayFrameCount, exported.telemetry?.rawOverlayFrameCount)
    }

    @Test
    fun freezeSnapshotNormalizesDuplicateAndDenseFrames() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> ComposerResult("file:///rendered_annotated.mp4", null) },
        )
        val denseTimeline = testTimeline(
            listOf(
                testFrame(1_000L),
                testFrame(1_000L),
                testFrame(1_010L),
                testFrame(1_020L),
                testFrame(1_200L),
            ),
        )

        val frozen = pipeline.freezeSnapshotForExport(denseTimeline, rawDurationMsHint = null)

        assertEquals(5, frozen.rawOverlayFrameCount)
        assertTrue(frozen.usableOverlayFrameCount < frozen.rawOverlayFrameCount)
        assertEquals(1_000L, frozen.overlayTimeline.frames.first().timestampMs)
        assertEquals(1_200L, frozen.overlayTimeline.frames.last().timestampMs)
    }

    @Test
    fun freezeSnapshotPrefersProvidedRawDurationHint() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> ComposerResult("file:///rendered_annotated.mp4", null) },
        )
        val timeline = testTimeline(listOf(testFrame(1_000L), testFrame(4_000L)))

        val frozen = pipeline.freezeSnapshotForExport(timeline, rawDurationMsHint = 2_000L)

        assertEquals(2_000L, frozen.rawDurationMs)
        assertEquals(1, frozen.usableOverlayFrameCount)
        assertEquals(1_000L, frozen.overlayTimeline.frames.first().timestampMs)
    }

    @Test
    fun exceptionsMapToTypedExceptionReason() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> throw IllegalStateException("boom") },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertEquals(AnnotatedExportFailureReason.RENDER_PIPELINE_EXCEPTION.name, exported.failureReason)
    }

    @Test
    fun cancellationMapsToExportCancelledReason() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> "file:///persisted_annotated.mp4" },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> throw CancellationException("cancel") },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertEquals(AnnotatedExportFailureReason.EXPORT_CANCELLED.name, exported.failureReason)
    }

    @Test
    fun nullPersistedOutputFailsVerification() {
        val pipeline = AnnotatedExportPipeline(
            persistAnnotatedVideo = { _, _ -> null },
            updateExportStatus = { _, _ -> },
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> ComposerResult("file:///rendered_annotated.mp4", null) },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
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
            renderAnnotatedVideo = { _, _, _, _, _, _, _ -> ComposerResult("file:///rendered_annotated.mp4", null) },
        )

        val exported = runBlocking {
            pipeline.export(
                sessionId = 7L,
                rawVideoUri = "file:///raw.mp4",
                drillType = DrillType.WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayTimeline = testTimeline(listOf(testFrame(1000L))),
            )
        }

        assertEquals(AnnotatedExportFailureReason.OUTPUT_FILE_ZERO_BYTES.name, exported.failureReason)
        assertTrue(zeroByte.exists())
    }

    private fun testTimeline(frames: List<AnnotatedOverlayFrame>) = OverlayTimeline(
        startedAtMs = 0L,
        sampleIntervalMs = 80L,
        frames = frames.map { it.toTimelineFrame(sessionId = 7L, sessionStartedAtMs = 0L) },
    )

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
