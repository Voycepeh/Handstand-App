package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.drills.catalog.AnalysisPlane
import com.inversioncoach.app.drills.catalog.CameraView
import kotlin.test.Test
import kotlin.test.assertEquals

class DrillStudioViewRulesTest {
    @Test
    fun `front view maps to frontal analysis plane`() {
        assertEquals(AnalysisPlane.FRONTAL, analysisPlaneForPrimaryView(CameraView.FRONT))
    }

    @Test
    fun `side and profile views map to sagittal analysis plane`() {
        val sideLikeViews = listOf(
            CameraView.SIDE,
            CameraView.LEFT_PROFILE,
            CameraView.RIGHT_PROFILE,
        )

        sideLikeViews.forEach { view ->
            assertEquals(
                AnalysisPlane.SAGITTAL,
                analysisPlaneForPrimaryView(view),
                "Expected $view to map to SAGITTAL",
            )
        }
    }
}
