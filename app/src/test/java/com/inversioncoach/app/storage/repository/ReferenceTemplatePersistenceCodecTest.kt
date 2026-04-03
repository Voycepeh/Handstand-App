package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.ReferenceTemplateRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferenceTemplatePersistenceCodecTest {
    @Test
    fun decodeReferenceProfileIdsFiltersBlanks() {
        val ids = ReferenceTemplatePersistenceCodec.decodeReferenceProfileIds("profile-1| |profile-2||")

        assertEquals(listOf("profile-1", "profile-2"), ids)
    }

    @Test
    fun decodeTemplateDefinitionSupportsLegacyToleranceKeys() {
        val record = ReferenceTemplateRecord(
            id = "template-1",
            drillId = "drill-1",
            displayName = "Template",
            templateType = "SINGLE_REFERENCE",
            sourceProfileIdsJson = "profile-1",
            checkpointJson = """{"phaseTimingsMs":{"setup":1200}}""",
            toleranceJson = """{"featureMeans":{"line":0.62},"stabilityJitter":{"jitter":0.11}}""",
            createdAtMs = 1L,
        )

        val decoded = ReferenceTemplatePersistenceCodec.decodeTemplateDefinition(record)

        assertEquals(mapOf("setup" to 1200L), decoded?.phaseTimingMs)
        assertEquals(mapOf("line" to 0.62f), decoded?.alignmentTargets)
        assertEquals(mapOf("jitter" to 0.11f), decoded?.stabilityTargets)
    }

    @Test
    fun decodeTemplateDefinitionReturnsNullForInvalidJson() {
        val record = ReferenceTemplateRecord(
            id = "template-1",
            drillId = "drill-1",
            displayName = "Template",
            templateType = "SINGLE_REFERENCE",
            sourceProfileIdsJson = "",
            checkpointJson = "not-json",
            toleranceJson = "{}",
            createdAtMs = 1L,
        )

        val decoded = ReferenceTemplatePersistenceCodec.decodeTemplateDefinition(record)

        assertNull(decoded)
    }
}
