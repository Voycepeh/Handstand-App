package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.drills.catalog.AnalysisPlane
import com.inversioncoach.app.drills.catalog.CalibrationTemplate
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import com.inversioncoach.app.model.ReferenceTemplateRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceTemplateDraftMapperTest {
    @Test
    fun mapsTemplateRecordIntoEditableDraftPreservingPhaseOrderAndProgress() {
        val record = referenceRecord(
            phasePosesJson = """{"phases":[{"phaseId":"setup","sequenceIndex":0,"durationMs":1000},{"phaseId":"stack","sequenceIndex":1,"durationMs":2000},{"phaseId":"hold","sequenceIndex":2,"durationMs":3000}]}""",
            keyframesJson = """{"keyframes":[{"phaseId":"setup","progress":0.0},{"phaseId":"stack","progress":0.3},{"phaseId":"hold","progress":0.8}]}""",
        )

        val result = ReferenceTemplateDraftMapper.toDraft(record, seed = seedDrill())

        assertEquals(listOf("setup", "stack", "hold"), result.draft.phases.map { it.id })
        assertEquals(3, result.draft.skeletonTemplate.phasePoses.size)
        assertEquals(0.3f, result.draft.calibration.phaseWindows.getValue("stack").start)
        assertEquals(0.8f, result.draft.calibration.phaseWindows.getValue("stack").end)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun reconstructsSafelyWhenTemplateJsonIsIncomplete() {
        val record = referenceRecord(
            phasePosesJson = "",
            keyframesJson = "",
        )

        val result = ReferenceTemplateDraftMapper.toDraft(record, seed = seedDrill())

        assertEquals(seedDrill().phases.map { it.id }, result.draft.phases.map { it.id })
        assertTrue(result.warnings.isNotEmpty())
        assertTrue(result.draft.skeletonTemplate.keyframes.isNotEmpty())
    }

    @Test
    fun resolveInitialDraftFallsBackToSeedWhenTemplateParsingFails() {
        val seed = seedDrill()
        val result = resolveInitialDraft(
            mode = "drill",
            templateRecord = referenceRecord(),
            seed = seed,
            mapper = { _, _ -> error("bad template") },
        )

        assertTrue(result.statusMessage?.contains("Template parsing failed") == true)
        assertEquals(seed.id, result.draft.sourceSeedId)
    }

    private fun seedDrill(): DrillTemplate = DrillTemplate(
        id = "seed_wall_handstand",
        title = "Wall Handstand",
        description = "",
        family = "Seed",
        movementType = CatalogMovementType.HOLD,
        tags = listOf("seed"),
        cameraView = CameraView.LEFT_PROFILE,
        supportedViews = listOf(CameraView.LEFT_PROFILE),
        analysisPlane = AnalysisPlane.SAGITTAL,
        comparisonMode = ComparisonMode.POSE_TIMELINE,
        keyJoints = listOf("shoulder_left", "shoulder_right", "hip_left", "hip_right"),
        phases = listOf(
            DrillPhaseTemplate("setup", "Setup", 1, PhaseWindow(0f, 0.3f)),
            DrillPhaseTemplate("stack", "Stack", 2, PhaseWindow(0.3f, 0.7f)),
            DrillPhaseTemplate("hold", "Hold", 3, PhaseWindow(0.7f, 1f)),
        ),
        skeletonTemplate = SkeletonTemplate(
            id = "seed_skeleton",
            loop = true,
            framesPerSecond = 24,
            keyframes = listOf(
                SkeletonKeyframeTemplate(0f, DrillStudioPosePresets.neutralUpright.joints),
                SkeletonKeyframeTemplate(1f, DrillStudioPosePresets.neutralUpright.joints),
            ),
        ),
        calibration = CalibrationTemplate(metricThresholds = emptyMap(), phaseWindows = emptyMap()),
    )

    private fun referenceRecord(
        phasePosesJson: String = "{}",
        keyframesJson: String = "{}",
    ): ReferenceTemplateRecord = ReferenceTemplateRecord(
        id = "template-1",
        drillId = "seed_wall_handstand",
        displayName = "Template 1",
        templateType = "SINGLE_REFERENCE",
        phasePosesJson = phasePosesJson,
        keyframesJson = keyframesJson,
        sourceProfileIdsJson = "",
        checkpointJson = "{}",
        toleranceJson = "{}",
        createdAtMs = 1L,
    )
}
