package com.inversioncoach.app.ui.upload

import android.net.Uri
import com.inversioncoach.app.model.AnnotatedExportStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadVideoViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun successPathTransitionsToAnnotatedCompleteAndStoresResult() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val runner = object : UploadVideoAnalysisRunner {
            override suspend fun run(
                uri: Uri,
                onSessionCreated: (Long) -> Unit,
                onProgress: (UploadProgress) -> Unit,
                onLog: (String) -> Unit,
            ): UploadFlowResult {
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

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.COMPLETED_ANNOTATED, state.stage)
        assertEquals(AnnotatedExportStatus.ANNOTATED_READY, state.annotatedVideoStatus)
        assertTrue(state.progressPercent >= 1f)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun invalidUriShowsFailureMessage() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
            override suspend fun run(
                uri: Uri,
                onSessionCreated: (Long) -> Unit,
                onProgress: (UploadProgress) -> Unit,
                onLog: (String) -> Unit,
            ): UploadFlowResult = throw IllegalStateException("Unreadable media")
        })

        viewModel.analyze(Uri.parse("content://broken"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.FAILED, state.stage)
        assertTrue(state.errorMessage?.contains("Unreadable") == true)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun pickerInvalidSelectionMovesToFailure() {
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
            override suspend fun run(
                uri: Uri,
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
}
