package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryAttemptOwnershipTest {

    @Test
    fun claimedActiveAttempt_rejectsBlankAttemptWriter() {
        val session = baseSession().copy(
            annotatedExportStatus = AnnotatedExportStatus.PROCESSING,
            activeProcessingAttemptId = "attempt-new",
            processingOwnerType = "UPLOAD_PIPELINE",
            processingOwnerId = "worker-new",
        )

        val canMutate = canMutateAttemptOwnedExportState(
            session = session,
            attemptId = null,
            ownerType = "UPLOAD_PIPELINE",
            ownerId = "worker-legacy",
        )

        assertFalse("Blank attempt writers must not mutate claimed active attempts", canMutate)
    }

    @Test
    fun idleSession_allowsAdministrativeWriteWithoutAttempt() {
        val session = baseSession().copy(
            annotatedExportStatus = AnnotatedExportStatus.NOT_STARTED,
            activeProcessingAttemptId = null,
            processingOwnerType = null,
            processingOwnerId = null,
        )

        val canMutate = canMutateAttemptOwnedExportState(
            session = session,
            attemptId = null,
            ownerType = null,
            ownerId = null,
        )

        assertTrue("Non-processing administrative writes can omit attempt ownership", canMutate)
    }

    private fun baseSession(): SessionRecord = SessionRecord(
        id = 1L,
        title = "Session",
        drillType = DrillType.WALL_HANDSTAND,
        startedAtMs = 0L,
        completedAtMs = 1000L,
        overallScore = 0,
        strongestArea = "",
        limitingFactor = "",
        issues = "",
        wins = "",
        metricsJson = "",
        annotatedVideoUri = null,
        rawVideoUri = null,
        notesUri = null,
        bestFrameTimestampMs = null,
        worstFrameTimestampMs = null,
        topImprovementFocus = "",
    )
}
