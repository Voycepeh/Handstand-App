package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.model.ReferenceTemplateRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReferenceTemplateRecordCodecTest {
    @Test
    fun toDefinitionMapsFeatureMeansFallbackAndPhaseTimings() {
        val record = record(
            checkpointJson = """{"phaseTimingsMs":{"setup":1000,"hold":1200}}""",
            toleranceJson = """{"featureMeans":{"alignment_score":0.8},"stabilityJitter":{"trunk_lean":0.15}}""",
        )

        val definition = ReferenceTemplateRecordCodec.toDefinition(record)

        assertNotNull(definition)
        assertEquals(1000L, definition?.phaseTimingMs?.get("setup"))
        assertEquals(0.8f, definition?.alignmentTargets?.get("alignment_score"))
        assertEquals(0.15f, definition?.stabilityTargets?.get("trunk_lean"))
    }

    @Test
    fun toDefinitionReturnsNullForMalformedJson() {
        val definition = ReferenceTemplateRecordCodec.toDefinition(
            record(checkpointJson = "{bad", toleranceJson = "{}"),
        )

        assertNull(definition)
    }

    @Test
    fun sourceProfileIdsParsesPipeDelimitedIdsSafely() {
        val ids = ReferenceTemplateRecordCodec.sourceProfileIds(
            record(sourceProfileIdsJson = " profile-1 | |profile-2|  "),
        )

        assertEquals(listOf("profile-1", "profile-2"), ids)
    }

    private fun record(
        sourceProfileIdsJson: String = "profile-1",
        checkpointJson: String = "{}",
        toleranceJson: String = "{}",
    ) = ReferenceTemplateRecord(
        id = "template-1",
        drillId = "drill-1",
        displayName = "Template",
        templateType = "SINGLE_REFERENCE",
        createdAtMs = 10,
        sourceProfileIdsJson = sourceProfileIdsJson,
        checkpointJson = checkpointJson,
        toleranceJson = toleranceJson,
    )
}
