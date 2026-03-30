package com.inversioncoach.app.storage.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryPortableDrillContractTest {
    @Test
    fun exposesPortableDrillApis() {
        val methodNames = SessionRepository::class.java.methods.map { it.name }.toSet()
        listOf(
            "getAllDrills",
            "getActiveDrills",
            "createDrill",
            "updateDrill",
            "validateAndMarkDrillReady",
            "archiveDrill",
            "deleteDrill",
            "getDrill",
            "saveReferenceAsset",
            "saveMovementProfile",
            "saveCalibrationConfig",
            "saveReferenceTemplate",
            "updateTemplateFromDraft",
            "createTemplateFromDraft",
            "createTemplateFromReferenceUpload",
            "createDrillFromReferenceUpload",
            "promoteSessionToTemplate",
            "setBaselineTemplateForDrill",
            "listTemplatesForDrill",
            "getSessionsForDrill",
            "saveSessionComparison",
            "getTemplatesForDrill",
            "getComparisonsForSession",
            "getComparisonsForDrill",
        ).forEach { expected ->
            assertTrue("SessionRepository must expose $expected()", methodNames.contains(expected))
        }
    }
}
