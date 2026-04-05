package com.inversioncoach.app.drillpackage

import com.inversioncoach.app.drillpackage.mapping.DrillCatalogPortableMapper
import com.inversioncoach.app.drillpackage.model.PortableDrill
import com.inversioncoach.app.drillpackage.model.PortablePhase
import com.inversioncoach.app.drillpackage.model.PortableViewType
import com.inversioncoach.app.drills.catalog.CalibrationTemplate
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogAnalysisPlane
import com.inversioncoach.app.drills.catalog.CatalogComparisonMode
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.CatalogNormalizationBasis
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class DrillCatalogPortableMapperTest {
    @Test
    fun normalizesCatalogLeftAndRightProfilesToPortableSide() {
        val left = baseCatalogDrill(cameraView = CameraView.LEFT_PROFILE)
        val right = baseCatalogDrill(cameraView = CameraView.RIGHT_PROFILE)

        assertEquals(PortableViewType.SIDE, DrillCatalogPortableMapper.toPortableDrill(left).cameraView)
        assertEquals(PortableViewType.SIDE, DrillCatalogPortableMapper.toPortableDrill(right).cameraView)
    }

    @Test(expected = IllegalStateException::class)
    fun failsFastWhenBackViewCannotMapToLegacyCatalogCamera() {
        val portable = basePortableDrill(cameraView = PortableViewType.BACK)
        DrillCatalogPortableMapper.toCatalogDrill(portable)
    }

    private fun baseCatalogDrill(cameraView: CameraView): DrillTemplate = DrillTemplate(
        id = "d1",
        title = "Drill",
        description = "",
        family = "core",
        movementType = CatalogMovementType.HOLD,
        tags = emptyList(),
        cameraView = cameraView,
        supportedViews = listOf(cameraView),
        analysisPlane = CatalogAnalysisPlane.SAGITTAL,
        comparisonMode = CatalogComparisonMode.POSE_TIMELINE,
        normalizationBasis = CatalogNormalizationBasis.HIPS,
        phases = listOf(com.inversioncoach.app.drills.catalog.DrillPhaseTemplate("setup", "Setup", 0)),
        skeletonTemplate = SkeletonTemplate(id = "s", loop = true, framesPerSecond = 15, keyframes = emptyList()),
        calibration = CalibrationTemplate(metricThresholds = emptyMap(), phaseWindows = mapOf("setup" to PhaseWindow(0f, 1f))),
    )

    private fun basePortableDrill(cameraView: PortableViewType): PortableDrill = PortableDrill(
        id = "d1",
        title = "Drill",
        description = "",
        family = "core",
        movementType = "HOLD",
        cameraView = cameraView,
        supportedViews = listOf(cameraView),
        comparisonMode = "POSE_TIMELINE",
        normalizationBasis = "HIPS",
        keyJoints = emptyList(),
        tags = emptyList(),
        phases = listOf(PortablePhase("setup", "Setup", 0)),
        poses = emptyList(),
        metricThresholds = emptyMap(),
    )
}
