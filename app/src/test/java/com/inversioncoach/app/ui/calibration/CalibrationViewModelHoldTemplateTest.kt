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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelHoldTemplateTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun controlledHoldFramesProduceSavedHoldTemplate() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val repo = FakeRepo()
        val vm = CalibrationViewModel(DrillType.FREE_HANDSTAND, FakeProvider(repo), repo)

        vm.beginCalibration()
        repeat(80) { vm.onPoseFrame(frame(it.toLong())) }
        advanceUntilIdle()

        assertNotNull(repo.saved?.holdTemplate)
        assertTrue(repo.saved!!.holdTemplate!!.metrics.isNotEmpty())
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun profileVersionIncrements() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val repo = FakeRepo(initialVersion = 7)
        val vm = CalibrationViewModel(DrillType.FREE_HANDSTAND, FakeProvider(repo), repo)

        vm.beginCalibration()
        repeat(80) { vm.onPoseFrame(frame(it.toLong())) }
        advanceUntilIdle()

        assertEquals(8, repo.saved?.profileVersion)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun fallbackWorksWhenNoHoldFramesExist() = runTest(dispatcher) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val repo = FakeRepo()
        val vm = CalibrationViewModel(DrillType.FREE_HANDSTAND, FakeProvider(repo), repo)
        vm.completeCalibration()
        advanceUntilIdle()

        assertEquals(repo.initial?.holdTemplate, repo.saved?.holdTemplate)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun frame(ts: Long): PoseFrame = PoseFrame(
        timestampMs = ts,
        joints = listOf(
            JointPoint("nose", 0.5f, 0.1f, 0f, 0.9f),
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
    }
}
