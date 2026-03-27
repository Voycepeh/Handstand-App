package com.inversioncoach.app.ui.calibration

import com.inversioncoach.app.calibration.CalibrationProfileProvider
import com.inversioncoach.app.calibration.DefaultDrillMovementProfiles
import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.calibration.DrillMovementProfileRepository
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun stepProgressesAfterEnoughUsableFrames() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val repo = FakeRepo()
        val viewModel = CalibrationViewModel(DrillType.FREE_HANDSTAND, FakeProvider(repo), repo)
        viewModel.beginCalibration()

        viewModel.onPoseFrame(sampleFrame())
        viewModel.captureStep()
        viewModel.continueToNextStep()
        advanceUntilIdle()

        assertEquals(CalibrationPhase.CAPTURING, viewModel.state.value.phase)
        assertEquals("SIDE NEUTRAL", viewModel.state.value.currentStep.name.replace('_', ' '))
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun completionPersistsUpdatedProfile() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val repo = FakeRepo()
        val viewModel = CalibrationViewModel(DrillType.FREE_HANDSTAND, FakeProvider(repo), repo)
        viewModel.beginCalibration()

        repeat(4) {
            viewModel.onPoseFrame(sampleFrame())
            viewModel.captureStep()
            viewModel.continueToNextStep()
        }
        advanceUntilIdle()

        assertNotNull(repo.saved)
        assertEquals(CalibrationPhase.COMPLETED, viewModel.state.value.phase)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun versionIncrementsWhenSaving() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val repo = FakeRepo(initialVersion = 4)
        val viewModel = CalibrationViewModel(DrillType.FREE_HANDSTAND, FakeProvider(repo), repo)
        viewModel.beginCalibration()

        repeat(4) {
            viewModel.onPoseFrame(sampleFrame())
            viewModel.captureStep()
            viewModel.continueToNextStep()
        }
        advanceUntilIdle()

        assertEquals(5, repo.saved?.profileVersion)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun ignoresFramesUntilCalibrationStarts() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val repo = FakeRepo()
        val viewModel = CalibrationViewModel(DrillType.FREE_HANDSTAND, FakeProvider(repo), repo)

        repeat(30) { viewModel.onPoseFrame(sampleFrame()) }

        assertEquals(CalibrationPhase.INTRO, viewModel.state.value.phase)
        assertEquals(0, viewModel.state.value.acceptedFrames)
        assertTrue(repo.saved == null)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun captureSetsCapturedStateAndReviewFrameUsesCapturedFrame() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val repo = FakeRepo()
        val viewModel = CalibrationViewModel(DrillType.FREE_HANDSTAND, FakeProvider(repo), repo)
        viewModel.beginCalibration()

        viewModel.onPoseFrame(sampleFrame())
        val liveFrame = viewModel.state.value.latestFrame
        viewModel.captureStep()
        val state = viewModel.state.value

        assertTrue(state.hasCapturedFrame)
        assertNotNull(state.capturedFrame)
        assertSame(state.capturedFrame, state.reviewFrame)
        assertTrue(liveFrame !== null)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun sampleFrame(): PoseFrame = PoseFrame(
        timestampMs = 0L,
        joints = listOf(
            JointPoint("left_shoulder", 0.3f, 0.2f, 0f, 0.9f),
            JointPoint("right_shoulder", 0.7f, 0.2f, 0f, 0.9f),
            JointPoint("left_hip", 0.35f, 0.6f, 0f, 0.9f),
            JointPoint("right_hip", 0.65f, 0.6f, 0f, 0.9f),
            JointPoint("left_elbow", 0.2f, 0.3f, 0f, 0.9f),
            JointPoint("right_elbow", 0.8f, 0.3f, 0f, 0.9f),
            JointPoint("left_wrist", 0.15f, 0.4f, 0f, 0.9f),
            JointPoint("right_wrist", 0.85f, 0.4f, 0f, 0.9f),
            JointPoint("left_knee", 0.35f, 0.8f, 0f, 0.9f),
            JointPoint("right_knee", 0.65f, 0.8f, 0f, 0.9f),
            JointPoint("left_ankle", 0.35f, 0.95f, 0f, 0.9f),
            JointPoint("right_ankle", 0.65f, 0.95f, 0f, 0.9f),
        ),
        confidence = 0.95f,
    )

    private class FakeProvider(private val repo: FakeRepo) : CalibrationProfileProvider {
        override suspend fun resolve(drillType: DrillType): DrillMovementProfile {
            return repo.initial ?: DefaultDrillMovementProfiles.forDrill(drillType)
        }
    }

    private class FakeRepo(initialVersion: Int = 1) : DrillMovementProfileRepository {
        val initial: DrillMovementProfile? = DefaultDrillMovementProfiles.forDrill(DrillType.FREE_HANDSTAND).copy(profileVersion = initialVersion)
        var saved: DrillMovementProfile? = null

        override suspend fun get(drillType: DrillType): DrillMovementProfile? = saved

        override suspend fun save(profile: DrillMovementProfile) {
            saved = profile
        }

        override suspend fun clear(drillType: DrillType) {
            saved = null
        }
    }
}
