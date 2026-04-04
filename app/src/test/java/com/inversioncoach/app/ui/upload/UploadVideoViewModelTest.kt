package com.inversioncoach.app.ui.upload

import android.net.Uri
import com.inversioncoach.app.model.AnnotatedExportStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadVideoViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun genericUploadStartsWithoutSelectedMovementType() {
        val viewModel = UploadVideoViewModel(runner = noOpRunner())

        assertNull(viewModel.state.value.effectiveMovementType)
        assertFalse(viewModel.state.value.isMovementTypeLocked)
    }

    @Test
    fun successPathTransitionsToAnnotatedCompleteAndStoresResult() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
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
                assertEquals(UploadTrackingMode.HOLD_BASED, trackingMode)
                onSessionCreated(42L)
                onProgress(UploadProgress(UploadStage.IMPORTING_RAW_VIDEO, 0.1f))
                onProgress(UploadProgress(UploadStage.RAW_IMPORT_COMPLETE, 0.2f))
                onProgress(UploadProgress(UploadStage.ANALYZING_VIDEO, 0.5f))
                onLog("test log")
                return UploadFlowResult(
                    sessionId = 42L,
                    replayUri = "file:///annotated.mp4",
                    rawReady = true,
                    annotatedReady = true,
                    finalStage = UploadStage.COMPLETED_ANNOTATED,
                )
            }
        }
        val viewModel = UploadVideoViewModel(runner)
        viewModel.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.COMPLETED_ANNOTATED, state.stage)
        assertEquals(42L, state.sessionId)
        assertEquals("file:///annotated.mp4", state.replayUri)
        assertTrue(state.technicalLog.contains("test log"))
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun downstreamFailureAfterRawImportCompletesAsRawOnly() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
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
                onSessionCreated(7L)
                onProgress(UploadProgress(UploadStage.RAW_IMPORT_COMPLETE, 0.2f))
                onProgress(UploadProgress(UploadStage.EXPORTING_ANNOTATED_VIDEO, 0.8f))
                return UploadFlowResult(
                    sessionId = 7L,
                    replayUri = "file:///raw.mp4",
                    rawReady = true,
                    annotatedReady = false,
                    exportFailureReason = "VERIFICATION_FAILED",
                    finalStage = UploadStage.COMPLETED_RAW_ONLY,
                )
            }
        }
        val viewModel = UploadVideoViewModel(runner)
        viewModel.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.COMPLETED_RAW_ONLY, state.stage)
        assertEquals("Raw replay ready, annotated replay unavailable", state.stageText)
        assertTrue(state.errorMessage?.contains("VERIFICATION_FAILED") == true)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun exportNotStartedFailureReasonIsSurfacedToUi() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
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
                onSessionCreated(13L)
                onProgress(UploadProgress(UploadStage.RAW_IMPORT_COMPLETE, 0.2f))
                onProgress(UploadProgress(UploadStage.ANALYZING_VIDEO, 0.5f))
                onProgress(UploadProgress(UploadStage.COMPLETED_RAW_ONLY, 1f))
                return UploadFlowResult(
                    sessionId = 13L,
                    replayUri = "file:///raw.mp4",
                    rawReady = true,
                    annotatedReady = false,
                    exportFailureReason = "EXPORT_NOT_STARTED",
                    finalStage = UploadStage.COMPLETED_RAW_ONLY,
                )
            }
        }
        val viewModel = UploadVideoViewModel(runner)
        viewModel.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.COMPLETED_RAW_ONLY, state.stage)
        assertEquals(13L, state.sessionId)
        assertTrue(state.errorMessage?.contains("EXPORT_NOT_STARTED") == true)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun analyzeEmitsProcessingStateBeforeTerminalResult() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
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
                onSessionCreated(88L)
                onProgress(UploadProgress(UploadStage.PREPARING_ANALYSIS, 0.2f, detail = "Preparing uploaded analysis"))
                onProgress(UploadProgress(UploadStage.ANALYZING_VIDEO, 0.4f, detail = "Analyzing uploaded frames"))
                onProgress(UploadProgress(UploadStage.EXPORTING_ANNOTATED_VIDEO, 0.8f, detail = "Encoding annotated output"))
                return UploadFlowResult(
                    sessionId = 88L,
                    replayUri = "file:///annotated.mp4",
                    rawReady = true,
                    annotatedReady = true,
                    finalStage = UploadStage.COMPLETED_ANNOTATED,
                )
            }
        }
        val viewModel = UploadVideoViewModel(runner)
        viewModel.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.COMPLETED_ANNOTATED, state.stage)
        assertEquals(AnnotatedExportStatus.ANNOTATED_READY, state.annotatedVideoStatus)
        assertTrue(state.progressPercent >= 1f)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun analyzeUsesCanonicalLocalCopyInsteadOfOriginalPickerUri() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        var receivedUri: Uri? = null
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
                receivedUri = uri
                onSessionCreated(123L)
                return UploadFlowResult(
                    sessionId = 123L,
                    replayUri = uri.toString(),
                    rawReady = true,
                    annotatedReady = false,
                    finalStage = UploadStage.COMPLETED_RAW_ONLY,
                )
            }
        }
        val originalUri = Uri.parse("content://picker/video/42")
        val canonicalUri = Uri.parse("file:///data/user/0/com.inversioncoach.app/cache/upload_intake/copy.mp4")
        val viewModel = UploadVideoViewModel(
            appContext = null,
            repository = null,
            selectedDrillId = null,
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            queueCoordinator = null,
            customRunner = runner,
            resolveCanonicalUploadUri = { canonicalUri },
        )
        viewModel.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)

        viewModel.analyze(originalUri)
        advanceUntilIdle()

        assertEquals(canonicalUri, receivedUri)
        assertEquals(canonicalUri, viewModel.state.value.selectedVideoUri)
        assertTrue(viewModel.state.value.technicalLog.contains("originalUri=$originalUri"))
        assertTrue(viewModel.state.value.technicalLog.contains("localUri=$canonicalUri"))
        assertTrue(viewModel.state.value.technicalLog.contains("usedLocal=true"))
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun invalidUriShowsFailureMessage() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
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
            ): UploadFlowResult = throw IllegalStateException("Unreadable media")
        })
        viewModel.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)

        viewModel.analyze(Uri.parse("content://broken"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.FAILED, state.stage)
        assertTrue(state.errorMessage?.contains("Unreadable") == true)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun zeroDecodedFramesFailureIsContainedInFailedState() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
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
            ): UploadFlowResult = throw IllegalStateException("zero_decoded_frames")
        })
        viewModel.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)

        viewModel.analyze(Uri.parse("content://broken"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.FAILED, state.stage)
        assertTrue(state.errorMessage?.contains("zero_decoded_frames", ignoreCase = true) == true)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun pickerInvalidSelectionMovesToFailure() {
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
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
            ): UploadFlowResult =
                UploadFlowResult(1L, null, rawReady = false, annotatedReady = false, finalStage = UploadStage.FAILED)
        })

        viewModel.onInvalidSelection("not a video")

        assertEquals(UploadStage.FAILED, viewModel.state.value.stage)
        assertEquals("not a video", viewModel.state.value.errorMessage)
    }

    @Test
    fun returningToUploadScreenDoesNotStartDuplicateRun() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val gate = CompletableDeferred<Unit>()
        var runCount = 0
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
                runCount += 1
                onSessionCreated(200L)
                gate.await()
                return UploadFlowResult(
                    sessionId = 200L,
                    replayUri = "file:///raw.mp4",
                    rawReady = true,
                    annotatedReady = false,
                    finalStage = UploadStage.COMPLETED_RAW_ONLY,
                )
            }
        }
        val coordinator = ActiveUploadCoordinator(TestScope(dispatcher), runner)
        val vmA = UploadVideoViewModel(
            appContext = null,
            repository = null,
            selectedDrillId = null,
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            queueCoordinator = coordinator,
            customRunner = runner,
            resolveCanonicalUploadUri = { it },
        )
        val vmB = UploadVideoViewModel(
            appContext = null,
            repository = null,
            selectedDrillId = null,
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            queueCoordinator = coordinator,
            customRunner = runner,
            resolveCanonicalUploadUri = { it },
        )
        vmA.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)
        vmB.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)

        vmA.analyze(Uri.parse("content://video"))
        advanceUntilIdle()
        vmB.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        assertEquals(1, runCount)
        assertTrue(vmB.state.value.errorMessage?.contains("Please wait", ignoreCase = true) == true)

        gate.complete(Unit)
        advanceUntilIdle()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun analyzeRequiresTrackingModeSelectionBeforeProcessing() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        var runnerCalled = false
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
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
                runnerCalled = true
                return UploadFlowResult(1L, null, rawReady = false, annotatedReady = false, finalStage = UploadStage.FAILED)
            }
        })

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.errorMessage?.contains("Choose Hold-based or Rep-based") == true)
        assertTrue(!runnerCalled)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun selectedUploadTrackingModeIsForwardedToRunner() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        var capturedMode: UploadTrackingMode? = null
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
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
                capturedMode = trackingMode
                onSessionCreated(9L)
                return UploadFlowResult(9L, "file:///raw.mp4", rawReady = true, annotatedReady = false, finalStage = UploadStage.COMPLETED_RAW_ONLY)
            }
        })
        viewModel.onTrackingModeSelected(UploadTrackingMode.REP_BASED)

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        assertEquals(UploadTrackingMode.REP_BASED, capturedMode)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun createDrillFromReferenceRequiresNameBeforeProcessing() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        var runnerCalled = false
        val viewModel = UploadVideoViewModel(
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
                    runnerCalled = true
                    return UploadFlowResult(1L, null, rawReady = false, annotatedReady = false, finalStage = UploadStage.FAILED)
                }
            },
            repository = null,
            selectedDrillId = "wall_handstand",
            selectedReferenceTemplateId = null,
            isReferenceUpload = true,
            createDrillFromReferenceUpload = true,
            pendingDrillName = "",
        )
        viewModel.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        assertEquals(UploadStage.FAILED, viewModel.state.value.stage)
        assertTrue(viewModel.state.value.errorMessage?.contains("Enter a drill name") == true)
        assertTrue(!runnerCalled)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun drillScopedUploadInheritsHoldTypeAndLocksSelector() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        var capturedMode: UploadTrackingMode? = null
        val viewModel = UploadVideoViewModel(
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
                    capturedMode = trackingMode
                    onSessionCreated(4L)
                    return UploadFlowResult(4L, null, rawReady = true, annotatedReady = false, finalStage = UploadStage.COMPLETED_RAW_ONLY)
                }
            },
            repository = null,
            selectedDrillId = "front_plank",
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            resolveDrillTrackingMode = { UploadTrackingMode.HOLD_BASED },
        )
        advanceUntilIdle()

        assertEquals(UploadTrackingMode.HOLD_BASED, viewModel.state.value.effectiveMovementType)
        assertTrue(viewModel.state.value.isMovementTypeLocked)
        viewModel.onTrackingModeSelected(UploadTrackingMode.REP_BASED)
        assertEquals(UploadTrackingMode.HOLD_BASED, viewModel.state.value.effectiveMovementType)

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()
        assertEquals(UploadTrackingMode.HOLD_BASED, capturedMode)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun drillScopedUploadInheritsRepTypeAndLocksSelector() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val viewModel = UploadVideoViewModel(
            runner = noOpRunner(),
            repository = null,
            selectedDrillId = "rep_drill",
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            resolveDrillTrackingMode = { UploadTrackingMode.REP_BASED },
        )
        advanceUntilIdle()

        assertEquals(UploadTrackingMode.REP_BASED, viewModel.state.value.effectiveMovementType)
        assertTrue(viewModel.state.value.isMovementTypeLocked)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun uiCanReattachToActiveUploadSession() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val sharedCoordinator = ActiveUploadCoordinator(
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
                    onSessionCreated(555L)
                    onProgress(UploadProgress(UploadStage.ANALYZING_VIDEO, 0.5f, detail = "Analyzing movement"))
                    gate.await()
                    return UploadFlowResult(555L, null, rawReady = true, annotatedReady = false, finalStage = UploadStage.COMPLETED_RAW_ONLY)
                }
            },
        )
        val vmA = UploadVideoViewModel(
            appContext = null,
            repository = null,
            selectedDrillId = null,
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            queueCoordinator = sharedCoordinator,
            customRunner = noOpRunner(),
        )
        vmA.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)
        vmA.analyze(Uri.parse("content://video"))
        advanceUntilIdle()
        val vmB = UploadVideoViewModel(
            appContext = null,
            repository = null,
            selectedDrillId = null,
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            queueCoordinator = sharedCoordinator,
            customRunner = noOpRunner(),
        )
        advanceUntilIdle()

        assertEquals(555L, vmB.state.value.sessionId)
        assertEquals(UploadStage.ANALYZING_VIDEO, vmB.state.value.stage)
        gate.complete(Unit)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun clearingUploadScreenViewModelDoesNotCancelSharedCoordinatorWork() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val sharedCoordinator = ActiveUploadCoordinator(
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
                    onSessionCreated(901L)
                    onProgress(UploadProgress(UploadStage.ANALYZING_VIDEO, 0.4f, detail = "Analyzing movement"))
                    gate.await()
                    return UploadFlowResult(901L, null, rawReady = true, annotatedReady = false, finalStage = UploadStage.COMPLETED_RAW_ONLY)
                }
            },
        )
        val vmA = TestableUploadVideoViewModel(
            queueCoordinator = sharedCoordinator,
            runner = noOpRunner(),
        )
        vmA.onTrackingModeSelected(UploadTrackingMode.HOLD_BASED)
        vmA.analyze(Uri.parse("content://video"))
        advanceUntilIdle()
        vmA.clearForTest()

        val vmB = UploadVideoViewModel(
            appContext = null,
            repository = null,
            selectedDrillId = null,
            selectedReferenceTemplateId = null,
            isReferenceUpload = false,
            createDrillFromReferenceUpload = false,
            queueCoordinator = sharedCoordinator,
            customRunner = noOpRunner(),
        )
        advanceUntilIdle()
        assertEquals(901L, vmB.state.value.sessionId)
        assertEquals(UploadStage.ANALYZING_VIDEO, vmB.state.value.stage)
        gate.complete(Unit)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun noOpRunner() = object : UploadVideoAnalysisRunner {
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
        ): UploadFlowResult = UploadFlowResult(1L, null, rawReady = false, annotatedReady = false, finalStage = UploadStage.FAILED)
    }

}

private class TestableUploadVideoViewModel(
    queueCoordinator: ActiveUploadCoordinator,
    runner: UploadVideoAnalysisRunner,
) : UploadVideoViewModel(
    appContext = null,
    repository = null,
    selectedDrillId = null,
    selectedReferenceTemplateId = null,
    isReferenceUpload = false,
    createDrillFromReferenceUpload = false,
    queueCoordinator = queueCoordinator,
    customRunner = runner,
) {
    fun clearForTest() = onCleared()
}
