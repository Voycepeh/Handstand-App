package com.inversioncoach.app.drillpackage

import com.inversioncoach.app.drillpackage.mapping.DrillRecordPortableMapper
import com.inversioncoach.app.drillpackage.model.PortableDrill
import com.inversioncoach.app.drillpackage.model.PortableJoint2D
import com.inversioncoach.app.drillpackage.model.PortablePhase
import com.inversioncoach.app.drillpackage.model.PortablePose
import com.inversioncoach.app.drillpackage.model.PortableViewType
import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.drills.DrillMovementMode
import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.model.DrillDefinitionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillRecordPortableMapperTest {
    @Test
    fun mapsLegacyLeftAndRightToPortableSide() {
        val left = baseRecord(cameraView = DrillCameraView.LEFT)
        val right = baseRecord(cameraView = DrillCameraView.RIGHT)

        assertEquals(PortableViewType.SIDE, DrillRecordPortableMapper.toPortableDrill(left).cameraView)
        assertEquals(PortableViewType.SIDE, DrillRecordPortableMapper.toPortableDrill(right).cameraView)
    }

    @Test
    fun mapsPortableSideBackToNeutralLegacySideNotLeft() {
        val portable = basePortable(cameraView = PortableViewType.SIDE)

        val record = DrillRecordPortableMapper.toDrillDefinitionRecord(portable = portable, nowMs = 123L)

        assertEquals(DrillCameraView.SIDE, record.cameraView)
    }

    @Test
    fun preservesPortableAuthoringFieldsViaCueConfigPayloadRoundTrip() {
        val portable = basePortable(cameraView = PortableViewType.SIDE).copy(
            supportedViews = listOf(PortableViewType.SIDE, PortableViewType.FRONT),
            comparisonMode = "OVERLAY",
            poses = listOf(
                PortablePose(
                    phaseId = "setup",
                    name = "Setup Pose",
                    viewType = PortableViewType.SIDE,
                    joints = mapOf("left_shoulder" to PortableJoint2D(0.2f, 0.3f, visibility = 0.8f, confidence = 0.7f)),
                    holdDurationMs = 100,
                ),
            ),
            metricThresholds = mapOf("hip_angle" to 22f),
            extensions = mapOf("author" to "studio"),
        )

        val record = DrillRecordPortableMapper.toDrillDefinitionRecord(portable = portable, nowMs = 10L)
        val restored = DrillRecordPortableMapper.toPortableDrill(record)

        assertEquals(portable.cameraView, restored.cameraView)
        assertEquals(portable.supportedViews, restored.supportedViews)
        assertEquals(portable.comparisonMode, restored.comparisonMode)
        assertEquals(portable.poses, restored.poses)
        assertEquals(portable.metricThresholds, restored.metricThresholds)
        assertTrue(restored.extensions["author"] == "studio")
    }

    private fun baseRecord(cameraView: String): DrillDefinitionRecord = DrillDefinitionRecord(
        id = "seed_push_up",
        name = "Push Up",
        description = "desc",
        movementMode = DrillMovementMode.REP,
        cameraView = cameraView,
        phaseSchemaJson = "setup|eccentric|concentric",
        keyJointsJson = "left_shoulder|right_shoulder|left_hip",
        normalizationBasisJson = "HIPS",
        cueConfigJson = "seedKey:seed_push_up",
        sourceType = DrillSourceType.SEEDED,
        status = DrillStatus.READY,
        version = 3,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )

    private fun basePortable(cameraView: PortableViewType): PortableDrill = PortableDrill(
        id = "portable_push_up",
        title = "Push Up",
        description = "desc",
        family = "push",
        movementType = DrillMovementMode.REP,
        cameraView = cameraView,
        supportedViews = listOf(cameraView),
        comparisonMode = "POSE_TIMELINE",
        normalizationBasis = "HIPS",
        keyJoints = listOf("left_shoulder", "right_shoulder"),
        tags = listOf("seeded"),
        phases = listOf(
            PortablePhase(id = "setup", label = "Setup", order = 0),
            PortablePhase(id = "eccentric", label = "Eccentric", order = 1),
        ),
        poses = emptyList(),
        metricThresholds = emptyMap(),
        extensions = emptyMap(),
    )
}
