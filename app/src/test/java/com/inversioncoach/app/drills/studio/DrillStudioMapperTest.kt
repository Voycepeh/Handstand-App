package com.inversioncoach.app.drills.studio

import com.inversioncoach.app.drills.catalog.CatalogAnalysisPlane
import com.inversioncoach.app.drills.catalog.CatalogCameraView
import com.inversioncoach.app.drills.catalog.CatalogComparisonMode
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.motion.SkeletonAnimationSpec
import com.inversioncoach.app.motion.SkeletonKeyframe
import org.junit.Assert.assertEquals
import org.junit.Test

class DrillStudioMapperTest {
    private val template = DrillTemplate(
        id = "drill-1",
        title = "Drill One",
        family = "family",
        movementType = CatalogMovementType.REP,
        cameraView = CatalogCameraView.SIDE,
        supportedViews = listOf(CatalogCameraView.SIDE, CatalogCameraView.FRONT),
        analysisPlane = CatalogAnalysisPlane.SAGITTAL,
        comparisonMode = CatalogComparisonMode.OVERLAY,
        phases = listOf(
            DrillPhaseTemplate("p1", "Phase 1", 0, PhaseWindow(0f, 0.4f)),
            DrillPhaseTemplate("p2", "Phase 2", 1, PhaseWindow(0.4f, 1f)),
        ),
        metricThresholds = mapOf("depth_ratio" to 0.8f),
        animationSpec = SkeletonAnimationSpec(id = "anim", keyframes = listOf(SkeletonKeyframe("start", 0f, emptyMap()), SkeletonKeyframe("middle", 0.5f, emptyMap()), SkeletonKeyframe("end", 1f, emptyMap()))),
    )

    @Test
    fun catalogToStudio_preservesPhaseMetadata() {
        val studio = DrillStudioMapper.fromCatalog(template)

        assertEquals("drill-1", studio.id)
        assertEquals(2, studio.phases.size)
        assertEquals(0.4f, studio.phases.first().progressWindow.end)
        assertEquals("start", studio.phases.first().anchorKeyframeName)
    }

    @Test
    fun studioToCatalog_roundTripsPhaseWindows() {
        val studio = DrillStudioMapper.fromCatalog(template)
        val catalog = DrillStudioMapper.toCatalog(studio)

        assertEquals(template.cameraView, catalog.cameraView)
        assertEquals(template.comparisonMode, catalog.comparisonMode)
        assertEquals(template.phases.map { it.progressWindow.end }, catalog.phases.map { it.progressWindow.end })
    }
}
