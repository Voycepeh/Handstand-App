package com.inversioncoach.app.ui.upload

import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.model.DrillDefinitionRecord
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadDrillReadinessGateTest {
    @Test
    fun rejectsDraftCustomDrill() {
        val draft = DrillDefinitionRecord(
            id = "drill-1",
            name = "Draft Drill",
            description = "",
            movementMode = "HOLD",
            cameraView = DrillCameraView.LEFT,
            phaseSchemaJson = "setup|hold",
            keyJointsJson = "hips",
            normalizationBasisJson = "hips",
            cueConfigJson = "",
            sourceType = DrillSourceType.USER_CREATED,
            status = DrillStatus.DRAFT,
            version = 1,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )

        val error = validateSelectedDrillForUpload("drill-1", draft)
        assertTrue(error?.contains("not ready", ignoreCase = true) == true)
    }

    @Test
    fun acceptsReadyCustomDrill() {
        val ready = DrillDefinitionRecord(
            id = "drill-1",
            name = "Ready Drill",
            description = "",
            movementMode = "HOLD",
            cameraView = DrillCameraView.LEFT,
            phaseSchemaJson = "setup|hold",
            keyJointsJson = "hips",
            normalizationBasisJson = "hips",
            cueConfigJson = "",
            sourceType = DrillSourceType.USER_CREATED,
            status = DrillStatus.READY,
            version = 1,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )

        val error = validateSelectedDrillForUpload("drill-1", ready)
        assertNull(error)
    }
}
