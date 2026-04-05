package com.inversioncoach.app.ui.upload

import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.drills.DrillMovementMode
import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.runtime.RuntimeDrillDefinition
import com.inversioncoach.app.drills.runtime.RuntimeDrillMapper
import com.inversioncoach.app.model.DrillDefinitionRecord
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadDrillReadinessGateTest {
    @Test
    fun rejectsDraftCustomDrill() {
        val draft = runtimeDrill(status = DrillStatus.DRAFT)

        val error = validateSelectedDrillForUpload("drill-1", draft)
        assertTrue(error?.contains("not ready", ignoreCase = true) == true)
    }

    @Test
    fun acceptsReadyCustomDrill() {
        val ready = runtimeDrill(status = DrillStatus.READY)

        val error = validateSelectedDrillForUpload("drill-1", ready)
        assertNull(error)
    }

    @Test
    fun consumesRuntimeDrillMappedFromLegacyRecord() {
        val legacy = DrillDefinitionRecord(
            id = "drill-1",
            name = "Ready Drill",
            description = "",
            movementMode = DrillMovementMode.HOLD,
            cameraView = DrillCameraView.SIDE,
            phaseSchemaJson = "setup|hold",
            keyJointsJson = "hips|shoulders",
            normalizationBasisJson = "hips",
            cueConfigJson = "",
            sourceType = DrillSourceType.USER_CREATED,
            status = DrillStatus.READY,
            version = 1,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )

        val runtime = RuntimeDrillMapper.fromRecord(legacy)
        val error = validateSelectedDrillForUpload("drill-1", runtime)

        assertNull(error)
        assertTrue(runtime.phases.contains("hold"))
        assertTrue(runtime.keyJoints.contains("hips"))
    }

    private fun runtimeDrill(status: String): RuntimeDrillDefinition = RuntimeDrillDefinition(
        id = "drill-1",
        name = "Drill",
        movementMode = DrillMovementMode.HOLD,
        cameraView = DrillCameraView.SIDE,
        status = status,
        phases = listOf("setup", "hold"),
        keyJoints = setOf("hips"),
        normalizationBasis = "hips",
    )
}
