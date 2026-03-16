package com.inversioncoach.app.ui.live

import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReplayDisplayStateTest {

    @Test
    fun processingWithFailureReasonIsInconsistent() {
        val session = baseSession.copy(
            rawPersistStatus = RawPersistStatus.SUCCEEDED,
            annotatedExportStatus = AnnotatedExportStatus.PROCESSING,
            annotatedExportFailureReason = "RAW_SAVE_FAILED",
            annotatedVideoUri = null,
        )
        assertEquals(ReplayDisplayState.INCONSISTENT_STATE, deriveReplayDisplayState(session, hasActiveExportJob = true))
    }

    @Test
    fun failedWithRawReadableShowsRawOnly() {
        val raw = File.createTempFile("raw_replay_state", ".mp4").apply { writeText("raw") }
        val session = baseSession.copy(
            rawPersistStatus = RawPersistStatus.SUCCEEDED,
            rawVideoUri = raw.toURI().toString(),
            annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
            annotatedExportFailureReason = "ANY",
        )
        assertEquals(
            ReplayDisplayState.RAW_ONLY,
            deriveReplayDisplayState(session, hasActiveExportJob = false),
        )
    }

    private val baseSession = SessionRecord(
        id = 1,
        title = "t",
        drillType = DrillType.WALL_HANDSTAND,
        startedAtMs = 1,
        completedAtMs = 2,
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
