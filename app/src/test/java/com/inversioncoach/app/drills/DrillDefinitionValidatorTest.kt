package com.inversioncoach.app.drills

import com.inversioncoach.app.model.DrillDefinitionRecord
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillDefinitionValidatorTest {
    @Test
    fun rejectsInvalidDraft() {
        val drill = DrillDefinitionRecord(
            id = "d1",
            name = "",
            description = "",
            movementMode = "BAD",
            cameraView = "",
            phaseSchemaJson = "|",
            keyJointsJson = "",
            normalizationBasisJson = "",
            cueConfigJson = "",
            sourceType = DrillSourceType.USER_CREATED,
            status = DrillStatus.DRAFT,
            version = 1,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )
        val errors = DrillDefinitionValidator.validate(drill)
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun acceptsValidReadyCandidate() {
        val drill = DrillDefinitionRecord(
            id = "d1",
            name = "Custom Drill",
            description = "desc",
            movementMode = DrillMovementMode.HOLD,
            cameraView = DrillCameraView.LEFT,
            phaseSchemaJson = "setup|hold",
            keyJointsJson = "left_shoulder|right_shoulder",
            normalizationBasisJson = "hips",
            cueConfigJson = "",
            sourceType = DrillSourceType.USER_CREATED,
            status = DrillStatus.DRAFT,
            version = 1,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )
        val errors = DrillDefinitionValidator.validate(drill)
        assertTrue(errors.isEmpty())
    }
}
