package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseAuthoring
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillStudioImagePoseDetectorTest {
    @Test
    fun normalizePointToImageSpaceUsesFullImageDimensions() {
        val normalized = normalizePointToImageSpace(
            x = 500f,
            y = 250f,
            imageWidth = 1000,
            imageHeight = 500,
        )

        assertEquals(0.5f, normalized.x, 0.0001f)
        assertEquals(0.5f, normalized.y, 0.0001f)
    }

    @Test
    fun normalizePointToImageSpaceClampsOutOfBoundsCoordinates() {
        val normalized = normalizePointToImageSpace(
            x = 2000f,
            y = -25f,
            imageWidth = 1000,
            imageHeight = 500,
        )

        assertEquals(1f, normalized.x, 0.0001f)
        assertEquals(0f, normalized.y, 0.0001f)
    }

    @Test
    fun authoringStatusSummaryReflectsDetectionAndManualAdjustments() {
        val pose = PhasePoseTemplate(
            phaseId = "p1",
            name = "Phase 1",
            joints = mapOf("head" to JointPoint(0.5f, 0.1f)),
            authoring = PhasePoseAuthoring(
                sourceImageUri = "file://reference.jpg",
                detectedJoints = mapOf("head" to JointPoint(0.5f, 0.2f)),
                manualOffsets = mapOf("head" to JointPoint(0.0f, -0.1f)),
                qualityScore = 0.82f,
            ),
        )

        val summary = authoringStatusSummary(pose = pose, imageLoaded = true, isDetecting = false)

        assertTrue(summary.contains("source: detected + adjusted"))
        assertTrue(summary.contains("quality: 82%"))
    }
}
