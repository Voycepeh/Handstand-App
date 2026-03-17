package com.inversioncoach.app.ui.upload

import android.net.Uri
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
                onProgress: (UploadProgress) -> Unit,
                onLog: (String) -> Unit,
            ): UploadFlowResult {
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
                onProgress: (UploadProgress) -> Unit,
                onLog: (String) -> Unit,
            ): UploadFlowResult {
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
    fun invalidUriShowsFailureMessage() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
            override suspend fun run(
                uri: Uri,
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
