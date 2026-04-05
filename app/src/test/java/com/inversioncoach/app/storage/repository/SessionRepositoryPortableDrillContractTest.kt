package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.drills.DrillMovementMode
import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.runtime.RuntimeDrillMapper
import com.inversioncoach.app.model.DrillDefinitionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryPortableDrillContractTest {
    @Test
    fun runtimeMapperProducesRuntimeShapeFromLegacyRecord() {
        val record = baseRecord(cameraView = DrillCameraView.SIDE)

        val runtime = RuntimeDrillMapper.fromRecord(record)

        assertEquals(record.id, runtime.id)
        assertEquals(record.status, runtime.status)
        assertEquals(listOf("setup", "hold"), runtime.phases)
        assertTrue(runtime.keyJoints.contains("left_shoulder"))
    }

    @Test
    fun drillLibraryProjectionKeepsNeutralSideCameraValue() {
        val projected = projectSelectableDrills(
            listOf(
                baseRecord(cameraView = DrillCameraView.SIDE),
            ),
        )

        assertEquals(1, projected.size)
        assertEquals(DrillCameraView.SIDE, projected.first().cameraView)
    }

    private fun baseRecord(cameraView: String): DrillDefinitionRecord = DrillDefinitionRecord(
        id = "drill-1",
        name = "Drill",
        description = "desc",
        movementMode = DrillMovementMode.HOLD,
        cameraView = cameraView,
        phaseSchemaJson = "setup|hold",
        keyJointsJson = "left_shoulder|right_shoulder",
        normalizationBasisJson = "hips",
        cueConfigJson = "comparisonMode:POSE_TIMELINE",
        sourceType = DrillSourceType.USER_CREATED,
        status = DrillStatus.READY,
        version = 1,
        createdAtMs = 0,
        updatedAtMs = 0,
    )
}
