package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.drills.catalog.AnalysisPlane
import com.inversioncoach.app.drills.catalog.CalibrationTemplate
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.CatalogNormalizationBasis
import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import com.inversioncoach.app.model.ReferenceTemplateRecord
import com.inversioncoach.app.ui.drillstudio.DrillStudioPosePresets
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceTemplateDraftPersistenceTest {
    @Test
    fun updateTemplateFromDraftPreservesSourceMetadataAndWritesPhasePayloads() {
        val now = 5_000L
        val existing = referenceRecord(isBaseline = false)

        val updated = buildUpdatedTemplateRecord(
            existing = existing,
            draft = draftTemplate(),
            displayName = "Edited Template",
            setAsBaseline = true,
            now = now,
        )

        assertEquals(existing.createdAtMs, updated.createdAtMs)
        assertEquals(existing.sourceType, updated.sourceType)
        assertEquals(existing.sourceSessionId, updated.sourceSessionId)
        assertTrue(updated.isBaseline)
        assertEquals(now, updated.updatedAtMs)
        assertEquals("Edited Template", updated.displayName)
        assertPhaseAndKeyframePayload(updated)
    }

    @Test
    fun saveAsNewTemplateCopiesParentMetadataUnderSameDrill() {
        val now = 9_000L
        val parent = referenceRecord(isBaseline = true)
        val created = buildNewTemplateRecord(
            drillId = parent.drillId,
            draft = draftTemplate(),
            displayName = "Variant Template",
            parent = parent,
            setAsBaseline = false,
            now = now,
        )

        assertEquals(parent.drillId, created.drillId)
        assertEquals(parent.sourceType, created.sourceType)
        assertEquals(parent.sourceSessionId, created.sourceSessionId)
        assertEquals(now, created.createdAtMs)
        assertEquals(now, created.updatedAtMs)
        assertFalse(created.isBaseline)
        assertPhaseAndKeyframePayload(created)
    }

    @Test
    fun saveAsNewTemplateCanRequestBaselineSwitch() {
        val created = buildNewTemplateRecord(
            drillId = "drill-1",
            draft = draftTemplate(),
            displayName = "Baseline Variant",
            parent = null,
            setAsBaseline = true,
            now = 1_000L,
        )

        assertTrue(created.isBaseline)
    }

    private fun assertPhaseAndKeyframePayload(record: ReferenceTemplateRecord) {
        val phases = JSONObject(record.phasePosesJson).getJSONArray("phases")
        val keyframes = JSONObject(record.keyframesJson).getJSONArray("keyframes")
        assertEquals(2, phases.length())
        assertEquals("setup", phases.getJSONObject(0).getString("phaseId"))
        assertEquals("hold", phases.getJSONObject(1).getString("phaseId"))
        assertTrue(keyframes.length() >= 2)
        assertEquals("setup", keyframes.getJSONObject(0).getString("phaseId"))
    }

    private fun draftTemplate(): DrillTemplate {
        val joints = DrillStudioPosePresets.neutralUpright.joints
        return DrillTemplate(
            id = "draft",
            title = "Draft Title",
            description = "",
            family = "Custom",
            movementType = CatalogMovementType.HOLD,
            tags = listOf("template"),
            cameraView = CameraView.LEFT_PROFILE,
            supportedViews = listOf(CameraView.LEFT_PROFILE),
            analysisPlane = AnalysisPlane.SAGITTAL,
            comparisonMode = ComparisonMode.POSE_TIMELINE,
            keyJoints = listOf("shoulder_left"),
            normalizationBasis = CatalogNormalizationBasis.HIPS,
            phases = listOf(
                DrillPhaseTemplate("setup", "Setup", 1, PhaseWindow(0f, 0.5f)),
                DrillPhaseTemplate("hold", "Hold", 2, PhaseWindow(0.5f, 1f)),
            ),
            skeletonTemplate = SkeletonTemplate(
                id = "skeleton",
                loop = true,
                framesPerSecond = 30,
                phasePoses = listOf(
                    PhasePoseTemplate("setup", "Setup", joints, holdDurationMs = 600, transitionDurationMs = 400),
                    PhasePoseTemplate("hold", "Hold", joints, holdDurationMs = 900, transitionDurationMs = 500),
                ),
                keyframes = listOf(
                    SkeletonKeyframeTemplate(0f, joints),
                    SkeletonKeyframeTemplate(0.6f, joints),
                    SkeletonKeyframeTemplate(1f, joints),
                ),
            ),
            calibration = CalibrationTemplate(
                metricThresholds = emptyMap(),
                phaseWindows = mapOf(
                    "setup" to PhaseWindow(0f, 0.5f),
                    "hold" to PhaseWindow(0.5f, 1f),
                ),
            ),
        )
    }

    private fun referenceRecord(isBaseline: Boolean): ReferenceTemplateRecord = ReferenceTemplateRecord(
        id = "template-1",
        drillId = "drill-1",
        displayName = "Original",
        templateType = "SINGLE_REFERENCE",
        sourceType = "REFERENCE_UPLOAD",
        sourceSessionId = 22L,
        sourceProfileIdsJson = "profile-1",
        checkpointJson = "{}",
        toleranceJson = "{}",
        createdAtMs = 100L,
        updatedAtMs = 100L,
        isBaseline = isBaseline,
    )
}
