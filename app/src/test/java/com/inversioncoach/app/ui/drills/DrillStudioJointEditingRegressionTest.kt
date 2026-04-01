package com.inversioncoach.app.ui.drills

import com.inversioncoach.app.drills.catalog.CatalogAnalysisPlane
import com.inversioncoach.app.drills.catalog.CatalogCameraView
import com.inversioncoach.app.drills.catalog.CatalogComparisonMode
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.studio.DrillStudioDocument
import com.inversioncoach.app.drills.studio.DrillStudioPhase
import com.inversioncoach.app.drills.studio.DrillStudioPhaseWindow
import com.inversioncoach.app.motion.BodyJoint
import com.inversioncoach.app.motion.NormalizedPoint
import com.inversioncoach.app.motion.SkeletonAnimationSpec
import com.inversioncoach.app.motion.SkeletonKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillStudioJointEditingRegressionTest {

    @Test
    fun resolveFrameIndex_prefersAnchorKeyframeWhenPresent() {
        val draft = draftForRegression(
            phases = listOf(
                DrillStudioPhase(
                    id = "phase_hold",
                    label = "Hold",
                    order = 1,
                    progressWindow = DrillStudioPhaseWindow(0.70f, 0.90f),
                    anchorKeyframeName = "setup",
                ),
            ),
        )

        val frameIndex = resolveFrameIndexForPhase(draft, draft.phases.first())

        assertEquals(0, frameIndex)
    }

    @Test
    fun resolveFrameIndex_fallsBackToMidpointForLegacyOrMissingAnchor() {
        val phases = listOf(
            DrillStudioPhase(
                id = "legacy_phase",
                label = "Legacy",
                order = 1,
                progressWindow = DrillStudioPhaseWindow(0.42f, 0.58f),
                anchorKeyframeName = null,
            ),
            DrillStudioPhase(
                id = "bad_anchor_phase",
                label = "Bad anchor",
                order = 2,
                progressWindow = DrillStudioPhaseWindow(0.42f, 0.58f),
                anchorKeyframeName = "missing_name",
            ),
        )
        val draft = draftForRegression(phases = phases)

        val legacyIndex = resolveFrameIndexForPhase(draft, phases[0])
        val missingAnchorIndex = resolveFrameIndexForPhase(draft, phases[1])

        // Midpoint ~= 0.50, nearest keyframe is "stack" at 0.45 (index 1).
        assertEquals(1, legacyIndex)
        assertEquals(1, missingAnchorIndex)
    }

    @Test
    fun updateJoint_isNullSafeForSparseJointMapsAndInvalidFrameIndex() {
        val draft = draftForRegression(
            phases = listOf(
                DrillStudioPhase(
                    id = "phase_stack",
                    label = "Stack",
                    order = 1,
                    progressWindow = DrillStudioPhaseWindow(0.30f, 0.60f),
                ),
            ),
            keyframes = listOf(
                SkeletonKeyframe(
                    name = "stack",
                    progress = 0.45f,
                    joints = mapOf(BodyJoint.HEAD to NormalizedPoint(0.50f, 0.20f)),
                ),
            ),
        )

        val updated = updateJoint(
            draft = draft,
            frameIndex = 0,
            joint = BodyJoint.LEFT_WRIST,
            x = 0.33f,
            y = 0.77f,
        )
        val unchanged = updateJoint(
            draft = draft,
            frameIndex = 99,
            joint = BodyJoint.LEFT_WRIST,
            x = 0.22f,
            y = 0.66f,
        )

        val joints = updated.animationSpec.keyframes.first().joints
        assertTrue(joints.containsKey(BodyJoint.HEAD))
        assertTrue(joints.containsKey(BodyJoint.LEFT_WRIST))
        assertFalse(draft.animationSpec.keyframes.first().joints.containsKey(BodyJoint.LEFT_WRIST))
        assertEquals(0.33f, joints[BodyJoint.LEFT_WRIST]?.x ?: -1f, 0.0001f)
        assertEquals(draft, unchanged)
    }

    @Test
    fun selectedPhaseResolution_prefersSelectedPhaseIdOverFirstPhase() {
        val phases = listOf(
            DrillStudioPhase(
                id = "phase_1",
                label = "Setup",
                order = 1,
                progressWindow = DrillStudioPhaseWindow(0.00f, 0.20f),
                anchorKeyframeName = "setup",
            ),
            DrillStudioPhase(
                id = "phase_3",
                label = "Finish",
                order = 3,
                progressWindow = DrillStudioPhaseWindow(0.80f, 1.00f),
                anchorKeyframeName = "finish",
            ),
        )
        val draft = draftForRegression(phases = phases)

        val selectedPhase = resolveSelectedPhase(draft, "phase_3")
        val selectedFrame = selectedPhase?.let { resolveFrameIndexForPhase(draft, it) }

        assertEquals("phase_3", selectedPhase?.id)
        assertEquals(2, selectedFrame)
    }

    @Test
    fun resolveFrameIndexForSelectedPhase_resolvesFromSelectedPhaseIdInternally() {
        val draft = draftForRegression(
            phases = listOf(
                DrillStudioPhase("phase_1", "Setup", 1, DrillStudioPhaseWindow(0.00f, 0.20f), anchorKeyframeName = "setup"),
                DrillStudioPhase("phase_2", "Stack", 2, DrillStudioPhaseWindow(0.35f, 0.65f), anchorKeyframeName = "stack"),
            ),
        )

        val selectedIndex = resolveFrameIndexForSelectedPhase(draft, "phase_2")
        val invalidIndex = resolveFrameIndexForSelectedPhase(draft, "phase_missing")

        assertEquals(1, selectedIndex)
        assertEquals(-1, invalidIndex)
    }

    @Test
    fun updateJointForSelectedPhase_writesOnlySelectedPhaseAnchorKeyframe() {
        val phases = listOf(
            DrillStudioPhase(
                id = "phase_1",
                label = "Setup",
                order = 1,
                progressWindow = DrillStudioPhaseWindow(0.00f, 0.20f),
                anchorKeyframeName = "setup",
            ),
            DrillStudioPhase(
                id = "phase_2",
                label = "Stack",
                order = 2,
                progressWindow = DrillStudioPhaseWindow(0.35f, 0.65f),
                anchorKeyframeName = "stack",
            ),
        )
        val draft = draftForRegression(phases = phases)

        val updated = updateJointForSelectedPhase(
            draft = draft,
            selectedPhaseId = "phase_2",
            joint = BodyJoint.LEFT_WRIST,
            x = 0.11f,
            y = 0.88f,
        )

        assertFalse(updated.animationSpec.keyframes[0].joints.containsKey(BodyJoint.LEFT_WRIST))
        assertEquals(0.11f, updated.animationSpec.keyframes[1].joints[BodyJoint.LEFT_WRIST]?.x ?: -1f, 0.0001f)
        assertEquals(0.88f, updated.animationSpec.keyframes[1].joints[BodyJoint.LEFT_WRIST]?.y ?: -1f, 0.0001f)
    }

    @Test
    fun coerceSelectedPhaseId_rebindsSelectionWhenPhaseChanges() {
        val originalDraft = draftForRegression(
            phases = listOf(
                DrillStudioPhase("phase_1", "Setup", 1, DrillStudioPhaseWindow(0.0f, 0.3f), anchorKeyframeName = "setup"),
                DrillStudioPhase("phase_2", "Stack", 2, DrillStudioPhaseWindow(0.3f, 0.7f), anchorKeyframeName = "stack"),
            ),
        )
        val reducedDraft = originalDraft.copy(phases = originalDraft.phases.filterNot { it.id == "phase_2" })

        val reboundSelection = coerceSelectedPhaseId(reducedDraft, selectedPhaseId = "phase_2")

        assertEquals("phase_1", reboundSelection)
    }

    private fun draftForRegression(
        phases: List<DrillStudioPhase>,
        keyframes: List<SkeletonKeyframe> = listOf(
            SkeletonKeyframe(name = "setup", progress = 0.00f, joints = mapOf(BodyJoint.HEAD to NormalizedPoint(0.50f, 0.20f))),
            SkeletonKeyframe(name = "stack", progress = 0.45f, joints = mapOf(BodyJoint.HEAD to NormalizedPoint(0.50f, 0.15f))),
            SkeletonKeyframe(name = "finish", progress = 1.00f, joints = mapOf(BodyJoint.HEAD to NormalizedPoint(0.50f, 0.22f))),
        ),
    ): DrillStudioDocument = DrillStudioDocument(
        id = "regression_drill",
        seededCatalogDrillId = null,
        displayName = "Regression Drill",
        family = "test",
        movementType = CatalogMovementType.HOLD,
        cameraView = CatalogCameraView.SIDE,
        supportedViews = listOf(CatalogCameraView.SIDE),
        analysisPlane = CatalogAnalysisPlane.SAGITTAL,
        comparisonMode = CatalogComparisonMode.OVERLAY,
        phases = phases,
        metricThresholds = emptyMap(),
        animationSpec = SkeletonAnimationSpec(id = "anim", keyframes = keyframes),
    )
}
