package com.inversioncoach.app.recording

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.overlay.DrillCameraSide
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
                drillType = DrillType.CHEST_TO_WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayFrames = emptyList(),
            )
        }

        assertNull(exported)
        assertEquals(listOf(AnnotatedExportStatus.FAILED), statuses)
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
                drillType = DrillType.CHEST_TO_WALL_HANDSTAND,
                drillCameraSide = DrillCameraSide.LEFT,
                overlayFrames = listOf(testFrame(1000L)),
            )
        }

        assertEquals("file:///persisted_annotated.mp4", exported)
        assertEquals(listOf(AnnotatedExportStatus.PROCESSING, AnnotatedExportStatus.READY), statuses)
    }

    private fun testFrame(timestampMs: Long) = AnnotatedOverlayFrame(
        timestampMs = timestampMs,
        landmarks = emptyList(),
        smoothedLandmarks = emptyList(),
        confidence = 0.9f,
        bodyVisible = true,
        drawSkeleton = true,
        drawIdealLine = true,
        orientation = SessionMode.DRILL,
        mirrorMode = false,
    )
}
