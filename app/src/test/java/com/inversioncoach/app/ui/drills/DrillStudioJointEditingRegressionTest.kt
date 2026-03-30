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
