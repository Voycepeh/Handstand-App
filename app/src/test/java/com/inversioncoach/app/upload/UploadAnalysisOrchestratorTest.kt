package com.inversioncoach.app.upload

import android.net.Uri
import com.inversioncoach.app.ui.upload.UploadFlowResult
import com.inversioncoach.app.ui.upload.UploadProgress
import com.inversioncoach.app.ui.upload.UploadStage
import com.inversioncoach.app.ui.upload.UploadTrackingMode
import com.inversioncoach.app.ui.upload.UploadVideoAnalysisRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadAnalysisOrchestratorTest {
    @Test
    fun executeDelegatesToCanonicalRunner() = runTest {
        var runCalled = false
        val runner = object : UploadVideoAnalysisRunner {
            override suspend fun run(
                uri: Uri,
                ownerToken: String,
                trackingMode: UploadTrackingMode,
                selectedDrillId: String?,
                selectedReferenceTemplateId: String?,
                isReferenceUpload: Boolean,
                createDrillFromReferenceUpload: Boolean,
                pendingDrillName: String?,
                onSessionCreated: (Long) -> Unit,
                onProgress: (UploadProgress) -> Unit,
                onLog: (String) -> Unit,
            ): UploadFlowResult {
                runCalled = true
                assertEquals("owner", ownerToken)
                onSessionCreated(51L)
                onProgress(UploadProgress(UploadStage.PREPARING_ANALYSIS, 0.2f, detail = "prep"))
                onLog("log-line")
                return UploadFlowResult(
                    sessionId = 51L,
                    replayUri = "file:///annotated.mp4",
                    rawReady = true,
                    annotatedReady = true,
                    finalStage = UploadStage.COMPLETED_ANNOTATED,
                )
            }
        }

        val created = mutableListOf<Long>()
        val progress = mutableListOf<UploadProgress>()
        val logs = mutableListOf<String>()
        val result = UploadAnalysisOrchestrator(runner).execute(
            uri = Uri.parse("content://video"),
            ownerToken = "owner",
            trackingMode = UploadTrackingMode.HOLD_BASED,
            selectedDrillId = null,
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            pendingDrillName = null,
            onSessionCreated = { created += it },
            onProgress = { progress += it },
            onLog = { logs += it },
        )

        assertTrue(runCalled)
        assertEquals(listOf(51L), created)
        assertEquals(UploadStage.PREPARING_ANALYSIS, progress.single().stage)
        assertEquals(listOf("log-line"), logs)
        assertEquals(UploadStage.COMPLETED_ANNOTATED, result.finalStage)
    }
}
