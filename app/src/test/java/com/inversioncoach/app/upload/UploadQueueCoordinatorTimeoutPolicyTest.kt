package com.inversioncoach.app.upload

import com.inversioncoach.app.model.UploadJobStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadQueueCoordinatorTimeoutPolicyTest {
    @Test
    fun stageTimeoutsAreSaneAndStageAware() {
        assertTrue(stageTimeoutFor(UploadJobStage.ANALYZING_VIDEO) > stageTimeoutFor(UploadJobStage.VALIDATING_INPUT))
        assertTrue(stageTimeoutFor(UploadJobStage.RENDERING_ANNOTATED_VIDEO) >= stageTimeoutFor(UploadJobStage.IMPORTING_RAW_VIDEO))
    }

    @Test
    fun timeoutReasonPrioritizesOverallThenStageThenProgressThenHeartbeat() {
        assertEquals("overall_timeout", timeoutReason(heartbeatStale = true, progressStale = true, stageStale = true, overallStale = true))
        assertEquals("stage_timeout", timeoutReason(heartbeatStale = true, progressStale = true, stageStale = true, overallStale = false))
        assertEquals("progress_timeout", timeoutReason(heartbeatStale = true, progressStale = true, stageStale = false, overallStale = false))
        assertEquals("heartbeat_timeout", timeoutReason(heartbeatStale = true, progressStale = false, stageStale = false, overallStale = false))
    }
}
