package com.inversioncoach.app.drills.studio

import com.inversioncoach.app.drills.catalog.CatalogAnalysisPlane
import com.inversioncoach.app.drills.catalog.CatalogCameraView
import com.inversioncoach.app.drills.catalog.CatalogComparisonMode
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.motion.SkeletonAnimationSpec
import com.inversioncoach.app.motion.SkeletonKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillStudioCodecTest {
    @Test
    fun roundTrip_preservesMultiPhaseAndThresholds() {
        val document = DrillStudioDocument(
            id = "drill-1",
            seededCatalogDrillId = "drill-1",
            displayName = "Drill",
            family = "family",
            movementType = CatalogMovementType.HOLD,
            cameraView = CatalogCameraView.SIDE,
            supportedViews = listOf(CatalogCameraView.SIDE),
            analysisPlane = CatalogAnalysisPlane.SAGITTAL,
            comparisonMode = CatalogComparisonMode.OVERLAY,
            phases = listOf(
                DrillStudioPhase("p1", "Phase 1", 0, DrillStudioPhaseWindow(0f, 0.4f), anchorKeyframeName = "k"),
                DrillStudioPhase("p2", "Phase 2", 1, DrillStudioPhaseWindow(0.4f, 1f), thresholdOverrides = mapOf("depth_ratio" to 0.7f)),
            ),
            metricThresholds = mapOf("rep_tempo_lower" to 0.5f),
            animationSpec = SkeletonAnimationSpec(id = "anim", keyframes = listOf(SkeletonKeyframe("k", 0f, emptyMap()), SkeletonKeyframe("k2", 1f, emptyMap()))),
        )

        val decoded = DrillStudioCodec.fromJson(DrillStudioCodec.toJson(document))

        assertEquals(2, decoded.phases.size)
        assertEquals(1f, decoded.phases[1].progressWindow.end)
        assertEquals("k", decoded.phases[0].anchorKeyframeName)
        assertEquals(0.5f, decoded.metricThresholds["rep_tempo_lower"])
        assertTrue(decoded.animationSpec.keyframes.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDocument_rejectsBlankName() {
        val invalid = DrillStudioDocument(
            id = "id",
            seededCatalogDrillId = null,
            displayName = "",
            family = "f",
            movementType = CatalogMovementType.HOLD,
            cameraView = CatalogCameraView.SIDE,
            supportedViews = listOf(CatalogCameraView.SIDE),
            analysisPlane = CatalogAnalysisPlane.SAGITTAL,
            comparisonMode = CatalogComparisonMode.OVERLAY,
            phases = listOf(DrillStudioPhase("p1", "P1", 0, DrillStudioPhaseWindow(0f, 1f))),
            metricThresholds = emptyMap(),
            animationSpec = SkeletonAnimationSpec(id = "anim", keyframes = listOf(SkeletonKeyframe("k", 0f, emptyMap()))),
        )

        DrillStudioCodec.toJson(invalid)
    }
}
