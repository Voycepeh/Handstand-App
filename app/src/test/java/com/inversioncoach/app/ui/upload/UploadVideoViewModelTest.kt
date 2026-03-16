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
    fun successPathTransitionsToSuccessAndStoresResult() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val runner = object : UploadVideoAnalysisRunner {
            override suspend fun run(uri: Uri, onProgress: (UploadProgress) -> Unit): UploadFlowResult {
                onProgress(UploadProgress(UploadStage.PREPARING_VIDEO, 0.1f))
                onProgress(UploadProgress(UploadStage.ANALYZING, 0.4f))
                onProgress(UploadProgress(UploadStage.RENDERING, 0.8f))
                return UploadFlowResult(sessionId = 42L, replayUri = "file:///annotated.mp4", annotatedReady = true)
            }
        }
        val viewModel = UploadVideoViewModel(runner)

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.SUCCESS, state.stage)
        assertEquals(42L, state.sessionId)
        assertEquals("file:///annotated.mp4", state.replayUri)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun invalidUriShowsFailureMessage() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
            override suspend fun run(uri: Uri, onProgress: (UploadProgress) -> Unit): UploadFlowResult =
                throw IllegalStateException("Unreadable media")
        })

        viewModel.analyze(Uri.parse("content://broken"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.FAILURE, state.stage)
        assertTrue(state.errorMessage?.contains("Unreadable") == true)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun pickerInvalidSelectionMovesToFailure() {
        val viewModel = UploadVideoViewModel(object : UploadVideoAnalysisRunner {
            override suspend fun run(uri: Uri, onProgress: (UploadProgress) -> Unit): UploadFlowResult =
                UploadFlowResult(1L, null, annotatedReady = false)
        })

        viewModel.onInvalidSelection("not a video")

        assertEquals(UploadStage.FAILURE, viewModel.state.value.stage)
        assertEquals("not a video", viewModel.state.value.errorMessage)
    }

    @Test
    fun exportFallbackStillCompletesWithTruthfulMessage() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val runner = object : UploadVideoAnalysisRunner {
            override suspend fun run(uri: Uri, onProgress: (UploadProgress) -> Unit): UploadFlowResult {
                onProgress(UploadProgress(UploadStage.RENDERING, 0.8f))
                return UploadFlowResult(
                    sessionId = 7L,
                    replayUri = "file:///raw.mp4",
                    annotatedReady = false,
                    exportFailureReason = "VERIFICATION_FAILED",
                )
            }
        }
        val viewModel = UploadVideoViewModel(runner)

        viewModel.analyze(Uri.parse("content://video"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(UploadStage.SUCCESS, state.stage)
        assertEquals("Analysis complete (raw replay only)", state.stageText)
        assertTrue(state.errorMessage?.contains("VERIFICATION_FAILED") == true)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

}
