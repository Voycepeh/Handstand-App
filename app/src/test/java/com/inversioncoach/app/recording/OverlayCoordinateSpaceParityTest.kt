package com.inversioncoach.app.recording

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.model.SmoothedPoseFrame
import com.inversioncoach.app.overlay.DrillCameraSide
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayCoordinateSpaceParityTest {
    @Test
    fun liveOverlayTimelinePreservesSmoothedPoseCoordinates() {
        val smoothed = SmoothedPoseFrame(
            timestampMs = 1_250L,
            joints = listOf(
                JointPoint(name = "left_shoulder", x = 0.42f, y = 0.31f, z = 0f, visibility = 0.95f),
                JointPoint(name = "left_hip", x = 0.44f, y = 0.62f, z = 0f, visibility = 0.95f),
            ),
            confidence = 0.92f,
            analysisWidth = 720,
            analysisHeight = 1280,
            analysisRotationDegrees = 0,
        )

        val overlayFrame = OverlayStabilizer().stabilize(
            frame = smoothed,
            sessionMode = SessionMode.DRILL,
            drillCameraSide = DrillCameraSide.LEFT,
            showIdealLine = true,
            showSkeleton = true,
        )
        val timeline = overlayFrame.toTimelineFrame(sessionId = 7L, sessionStartedAtMs = 1_000L)
        val leftShoulder = timeline.landmarks.first { it.name == "left_shoulder" }

        assertEquals(0.42f, leftShoulder.x, 0.0001f)
        assertEquals(0.31f, leftShoulder.y, 0.0001f)
    }

    @Test
    fun uploadRotationMappingKeepsPortraitAndLandscapeInSameUprightSpace() {
        // Canonical upright target point in export space.
        val uprightX = 0.20f
        val uprightY = 0.30f

        val portraitPoint = JointPoint(
            name = "left_shoulder",
            x = uprightX,
            y = uprightY,
            z = 0f,
            visibility = 1f,
        )
        val landscapeEncodedPoint = JointPoint(
            name = "left_shoulder",
            // Inverse of 90° normalization mapping: out=(1-y, x)
            x = uprightY,
            y = 1f - uprightX,
            z = 0f,
            visibility = 1f,
        )

        val portraitMapped = mapOverlayPointToExportSpace(
            point = portraitPoint,
            transform = ExportTransform(
                sourceMetadataRotationDegrees = 0,
                renderRotationDegrees = 0,
                outputWidth = 720,
                outputHeight = 1280,
            ),
        )
        val landscapeMapped = mapOverlayPointToExportSpace(
            point = landscapeEncodedPoint,
            transform = ExportTransform(
                sourceMetadataRotationDegrees = 90,
                renderRotationDegrees = 90,
                outputWidth = 720,
                outputHeight = 1280,
            ),
        )

        assertEquals(uprightX, portraitMapped.x, 0.0001f)
        assertEquals(uprightY, portraitMapped.y, 0.0001f)
        assertEquals(uprightX, landscapeMapped.x, 0.0001f)
        assertEquals(uprightY, landscapeMapped.y, 0.0001f)
    }
}
