package com.inversioncoach.app.drills

import com.inversioncoach.app.model.DrillDefinitionRecord
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillReadyTransitionTest {
    @Test
    fun blocksReadyTransitionWhenRequiredFieldsMissing() {
        val invalid = DrillDefinitionRecord(
            id = "custom-1",
            name = "",
            description = "desc",
            movementMode = DrillMovementMode.HOLD,
            cameraView = DrillCameraView.LEFT,
            phaseSchemaJson = "",
            keyJointsJson = "",
            normalizationBasisJson = "",
            cueConfigJson = "",
            sourceType = DrillSourceType.USER_CREATED,
            status = DrillStatus.DRAFT,
            version = 1,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )

        val errors = DrillDefinitionValidator.validate(invalid)

        assertTrue(errors.isNotEmpty())
    }
}
