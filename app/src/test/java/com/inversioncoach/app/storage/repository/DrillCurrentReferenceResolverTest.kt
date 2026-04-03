package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.ReferenceTemplateRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrillCurrentReferenceResolverTest {
    @Test
    fun prefersBaselineTemplateEvenWhenNewerNonBaselineExists() {
        val baseline = template(id = "baseline", isBaseline = true, updatedAtMs = 100)
        val newer = template(id = "newer", isBaseline = false, updatedAtMs = 900)

        val resolved = DrillCurrentReferenceResolver.resolve(listOf(newer, baseline))

        assertEquals("baseline", resolved?.id)
    }

    @Test
    fun fallsBackToMostRecentlyUpdatedTemplateWhenNoBaseline() {
        val older = template(id = "older", isBaseline = false, updatedAtMs = 100)
        val newer = template(id = "newer", isBaseline = false, updatedAtMs = 200)

        val resolved = DrillCurrentReferenceResolver.resolve(listOf(older, newer))

        assertEquals("newer", resolved?.id)
    }

    @Test
    fun returnsNullWhenNoTemplatesExist() {
        assertNull(DrillCurrentReferenceResolver.resolve(emptyList()))
    }

    private fun template(
        id: String,
        isBaseline: Boolean,
        updatedAtMs: Long,
    ) = ReferenceTemplateRecord(
        id = id,
        drillId = "drill-1",
        displayName = id,
        templateType = "SINGLE_REFERENCE",
        createdAtMs = 1,
        updatedAtMs = updatedAtMs,
        isBaseline = isBaseline,
        sourceProfileIdsJson = "profile-1",
        checkpointJson = "{}",
        toleranceJson = "{}",
    )
}
