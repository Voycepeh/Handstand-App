package com.inversioncoach.app.ui.upload

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveUploadCoordinatorTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun onlyOneUploadJobCanRunAtATime() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val coordinator = ActiveUploadCoordinator(
            scope = TestScope(dispatcher),
            runner = object : UploadVideoAnalysisRunner {
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
                    onSessionCreated(1L)
                    gate.await()
                    return UploadFlowResult(1L, null, rawReady = true, annotatedReady = false, finalStage = UploadStage.COMPLETED_RAW_ONLY)
                }
            },
        )
        val request = ActiveUploadRequest(
            sourceUri = Uri.parse("content://video"),
            trackingMode = UploadTrackingMode.HOLD_BASED,
            selectedDrillId = null,
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            pendingDrillName = null,
        )
        coordinator.start(request)
        val blocked = coordinator.start(request) as ActiveUploadStartResult.Blocked
        assertTrue(blocked.message.contains("already in progress"))
        gate.complete(Unit)
    }

    @Test
    fun staleProgressFromOldSessionIsIgnored() = runTest(dispatcher) {
        lateinit var staleCallback: (UploadProgress) -> Unit
        val coordinator = ActiveUploadCoordinator(
            scope = TestScope(dispatcher),
            runner = object : UploadVideoAnalysisRunner {
                var call = 0
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
                    call += 1
                    onSessionCreated(call.toLong())
                    if (call == 1) {
                        staleCallback = onProgress
                        return UploadFlowResult(1L, null, rawReady = true, annotatedReady = false, finalStage = UploadStage.COMPLETED_RAW_ONLY)
                    }
                    onProgress(UploadProgress(UploadStage.ANALYZING_VIDEO, 0.5f))
                    return UploadFlowResult(2L, null, rawReady = true, annotatedReady = false, finalStage = UploadStage.COMPLETED_RAW_ONLY)
                }
            },
        )
        val request = ActiveUploadRequest(Uri.parse("content://video"), UploadTrackingMode.HOLD_BASED, null, null, false, false, null)
        coordinator.start(request)
        advanceUntilIdle()
        coordinator.start(request)
        advanceUntilIdle()
        staleCallback(UploadProgress(UploadStage.EXPORTING_ANNOTATED_VIDEO, 0.9f, detail = "stale"))
        advanceUntilIdle()
        assertEquals("Raw replay ready, annotated replay unavailable", coordinator.state.value.activeSession?.stageText)
    }

    @Test
    fun cancellationIsExposedAsCancelledNotFailure() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val coordinator = ActiveUploadCoordinator(
            scope = TestScope(dispatcher),
            runner = object : UploadVideoAnalysisRunner {
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
                    onSessionCreated(7L)
                    gate.await()
                    return UploadFlowResult(7L, null, rawReady = false, annotatedReady = false, finalStage = UploadStage.FAILED)
                }
            },
        )
        val request = ActiveUploadRequest(Uri.parse("content://video"), UploadTrackingMode.HOLD_BASED, null, null, false, false, null)
        coordinator.start(request)
        coordinator.cancelActiveUpload()
        advanceUntilIdle()
        assertEquals(UploadStage.CANCELLED, coordinator.state.value.activeSession?.stage)
    }

    @Test
    fun clearTerminalSessionRemovesCompletedSnapshot() = runTest(dispatcher) {
        val coordinator = ActiveUploadCoordinator(
            scope = TestScope(dispatcher),
            runner = object : UploadVideoAnalysisRunner {
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
                    onSessionCreated(12L)
                    return UploadFlowResult(12L, null, rawReady = true, annotatedReady = false, finalStage = UploadStage.COMPLETED_RAW_ONLY)
                }
            },
        )
        val request = ActiveUploadRequest(Uri.parse("content://video"), UploadTrackingMode.HOLD_BASED, null, null, false, false, null)
        coordinator.start(request)
        advanceUntilIdle()
        assertTrue(coordinator.state.value.activeSession?.isTerminal == true)
        coordinator.clearTerminalSession()
        assertNull(coordinator.state.value.activeSession)
    }
}
