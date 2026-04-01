package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.drills.catalog.AnalysisPlane
import com.inversioncoach.app.drills.catalog.CalibrationTemplate
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.CatalogNormalizationBasis
import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillStudioPersistenceMappingTest {

    @Test
    fun mapperRoundTrip_preservesMetadataPhasesAndPoses() {
        val draft = sampleDraft()

        val persisted = draft.toDrillDefinitionRecord(existingId = "drill_123", existing = null, ready = true)
        val reloaded = persisted.toDrillTemplate(seed = null)

        assertEquals("drill_123", persisted.id)
        assertEquals(draft.title, reloaded.title)
        assertEquals(draft.description, reloaded.description)
        assertEquals(draft.movementType, reloaded.movementType)
        assertEquals(draft.cameraView, reloaded.cameraView)
        assertEquals(draft.supportedViews, reloaded.supportedViews)
        assertEquals(draft.comparisonMode, reloaded.comparisonMode)
        assertEquals(draft.keyJoints, reloaded.keyJoints)
        assertEquals(draft.normalizationBasis, reloaded.normalizationBasis)
        assertEquals(draft.phases.map { it.label }, reloaded.phases.map { it.label })
        assertEquals(
            draft.skeletonTemplate.phasePoses.map { it.holdDurationMs to it.transitionDurationMs },
            reloaded.skeletonTemplate.phasePoses.map { it.holdDurationMs to it.transitionDurationMs },
        )
        assertEquals(
            draft.skeletonTemplate.phasePoses.first().joints,
            reloaded.skeletonTemplate.phasePoses.first().joints,
        )
        assertEquals(draft.calibration.metricThresholds, reloaded.calibration.metricThresholds)
    }

    @Test
    fun editSave_keepsSameDrillIdAndUpdatesFields() {
        val original = sampleDraft(title = "Old Name")
        val created = original.toDrillDefinitionRecord(existingId = "drill_existing", existing = null, ready = true)

        val edited = sampleDraft(title = "New Name", description = "Updated", firstPhaseLabel = "Kick up")
        val updated = edited.toDrillDefinitionRecord(existingId = created.id, existing = created, ready = true)

        assertEquals("drill_existing", updated.id)
        assertEquals("New Name", updated.name)
        assertEquals("Updated", updated.description)
        val reloaded = updated.toDrillTemplate(seed = null)
        assertEquals("New Name", reloaded.title)
        assertEquals("Kick up", reloaded.phases.first().label)
    }

    @Test
    fun persistedPayload_decodesFromCueConfig() {
        val record = sampleDraft().toDrillDefinitionRecord(existingId = "d1", existing = null, ready = true)

        val decoded = decodeStudioPayload(record.cueConfigJson)

        assertTrue(decoded != null)
        assertEquals(2, decoded?.phases?.size)
        assertEquals(2, decoded?.phasePoses?.size)
    }

    @Test
    fun editSaveReopen_existingPersistedDrill_preservesPoseJointsAndKeyframesExactly() {
        val original = sampleDraft()
        val created = original.toDrillDefinitionRecord(existingId = "drill_pose_regression", existing = null, ready = true)

        val edited = original.copy(
            phases = listOf(
                DrillPhaseTemplate(id = "p1", label = "Kick up", order = 1, progressWindow = PhaseWindow(0f, 0.45f)),
                DrillPhaseTemplate(id = "p2", label = "Stack", order = 2, progressWindow = PhaseWindow(0.45f, 1f)),
            ),
            skeletonTemplate = original.skeletonTemplate.copy(
                phasePoses = listOf(
                    PhasePoseTemplate(
                        phaseId = "p1",
                        name = "Kick up",
                        joints = mapOf(
                            "wrist_left" to JointPoint(0.23f, 0.34f),
                            "hip_right" to JointPoint(0.73f, 0.12f),
                        ),
                        holdDurationMs = 420,
                        transitionDurationMs = 810,
                    ),
                    PhasePoseTemplate(
                        phaseId = "p2",
                        name = "Stack",
                        joints = mapOf(
                            "wrist_left" to JointPoint(0.41f, 0.87f),
                            "hip_right" to JointPoint(0.62f, 0.31f),
                        ),
                        holdDurationMs = 690,
                        transitionDurationMs = 950,
                    ),
                ),
                keyframes = listOf(
                    SkeletonKeyframeTemplate(
                        0f,
                        mapOf("wrist_left" to JointPoint(0.23f, 0.34f), "hip_right" to JointPoint(0.73f, 0.12f)),
                    ),
                    SkeletonKeyframeTemplate(
                        1f,
                        mapOf("wrist_left" to JointPoint(0.41f, 0.87f), "hip_right" to JointPoint(0.62f, 0.31f)),
                    ),
                ),
            ),
        )
        val saved = edited.toDrillDefinitionRecord(
            existingId = created.id,
            existing = created.copy(cueConfigJson = "${created.cueConfigJson}|unknownKey:keep_me"),
            ready = true,
        )

        val reopened = saved.toDrillTemplate(seed = null)

        assertEquals("drill_pose_regression", reopened.id)
        assertEquals(edited.skeletonTemplate.phasePoses, reopened.skeletonTemplate.phasePoses)
        assertEquals(edited.skeletonTemplate.keyframes, reopened.skeletonTemplate.keyframes)
        assertTrue(saved.cueConfigJson.contains("unknownKey:keep_me"))
    }

    @Test
    fun save_preservesUnknownCueConfigTokens() {
        val existing = sampleDraft().toDrillDefinitionRecord(existingId = "drill_with_cue_tokens", existing = null, ready = true)
            .copy(cueConfigJson = "legacyDrillType:FREE_HANDSTAND|customFlag:enabled|customThreshold:0.8|comparisonMode:OVERLAY")

        val updated = sampleDraft(title = "Retained Cue Config").toDrillDefinitionRecord(
            existingId = existing.id,
            existing = existing,
            ready = true,
        )

        assertTrue(updated.cueConfigJson.contains("customFlag:enabled"))
        assertTrue(updated.cueConfigJson.contains("customThreshold:0.8"))
        assertNotNull(decodeStudioPayload(updated.cueConfigJson))
    }

    private fun sampleDraft(
        title: String = "Handstand Flow",
        description: String = "Desc",
        firstPhaseLabel: String = "Setup",
    ): DrillTemplate = DrillTemplate(
        id = "draft_1",
        title = title,
        description = description,
        family = "Custom",
        movementType = CatalogMovementType.REP,
        cameraView = CameraView.RIGHT_PROFILE,
        supportedViews = listOf(CameraView.RIGHT_PROFILE, CameraView.FRONT),
        analysisPlane = AnalysisPlane.SAGITTAL,
        comparisonMode = ComparisonMode.OVERLAY,
        keyJoints = listOf("shoulder_left", "hip_right"),
        normalizationBasis = CatalogNormalizationBasis.SHOULDERS,
        phases = listOf(
            DrillPhaseTemplate(id = "p1", label = firstPhaseLabel, order = 1, progressWindow = PhaseWindow(0f, 0.5f)),
            DrillPhaseTemplate(id = "p2", label = "Hold", order = 2, progressWindow = PhaseWindow(0.5f, 1f)),
        ),
        skeletonTemplate = SkeletonTemplate(
            id = "s1",
            loop = true,
            framesPerSecond = 24,
            phasePoses = listOf(
                PhasePoseTemplate(
                    phaseId = "p1",
                    name = firstPhaseLabel,
                    joints = mapOf("wrist_left" to JointPoint(0.1f, 0.2f)),
                    holdDurationMs = 300,
                    transitionDurationMs = 700,
                ),
                PhasePoseTemplate(
                    phaseId = "p2",
                    name = "Hold",
                    joints = mapOf("wrist_left" to JointPoint(0.4f, 0.8f)),
                    holdDurationMs = 500,
                    transitionDurationMs = 900,
                ),
            ),
            keyframes = listOf(
                SkeletonKeyframeTemplate(0f, mapOf("wrist_left" to JointPoint(0.1f, 0.2f))),
                SkeletonKeyframeTemplate(1f, mapOf("wrist_left" to JointPoint(0.4f, 0.8f))),
            ),
        ),
        calibration = CalibrationTemplate(metricThresholds = mapOf("stability" to 0.75f), phaseWindows = emptyMap()),
    )
}
