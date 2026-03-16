package com.inversioncoach.app.ui.live

import com.inversioncoach.app.biomechanics.DrillConfigs
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.overlay.DrillCameraSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedReadinessEngineTest {
    private val config = DrillConfigs.requireByType(DrillType.PIKE_PUSH_UP)

    @Test
    fun fallsBackToOppositeSideWhenClearlyHigherQuality() {
        val engine = SharedReadinessEngine(DrillType.PIKE_PUSH_UP, config, DrillCameraSide.LEFT)
        val frame = frameWith(
            confidence = 0.55f,
            leftVisibility = 0.25f,
            rightVisibility = 0.9f,
        )

        val eval = List(3) { engine.evaluate(frame) }.last()

        assertEquals(DrillCameraSide.RIGHT, eval.actualSide)
        assertTrue(eval.rightQuality.quality > eval.leftQuality.quality)
    }

    @Test
    fun requiresConsecutiveFramesToEnterMinimalReadiness() {
        val engine = SharedReadinessEngine(DrillType.PIKE_PUSH_UP, config, DrillCameraSide.LEFT)
        val valid = frameWith(confidence = 0.5f, leftVisibility = 0.8f, rightVisibility = 0.3f)

        val first = engine.evaluate(valid)
        val second = engine.evaluate(valid)
        val third = engine.evaluate(valid)

        assertEquals(ReadinessState.PERSON_PARTIAL, first.state)
        assertEquals(ReadinessState.PERSON_PARTIAL, second.state)
        assertEquals(ReadinessState.READY_MINIMAL, third.state)
        assertTrue(third.timerEligible)
    }

    private fun frameWith(confidence: Float, leftVisibility: Float, rightVisibility: Float): PoseFrame {
        val joints = listOf(
            joint("left_shoulder", 0.42f, 0.35f, leftVisibility),
            joint("left_elbow", 0.41f, 0.45f, leftVisibility),
            joint("left_wrist", 0.40f, 0.56f, leftVisibility),
            joint("left_hip", 0.45f, 0.6f, leftVisibility),
            joint("left_knee", 0.46f, 0.74f, leftVisibility),
            joint("left_ankle", 0.47f, 0.88f, leftVisibility),
            joint("right_shoulder", 0.58f, 0.35f, rightVisibility),
            joint("right_elbow", 0.59f, 0.45f, rightVisibility),
            joint("right_wrist", 0.60f, 0.56f, rightVisibility),
            joint("right_hip", 0.55f, 0.6f, rightVisibility),
            joint("right_knee", 0.54f, 0.74f, rightVisibility),
            joint("right_ankle", 0.53f, 0.88f, rightVisibility),
        )
        return PoseFrame(
            timestampMs = System.currentTimeMillis(),
            joints = joints,
            confidence = confidence,
            landmarksDetected = joints.size,
        )
    }

    private fun joint(name: String, x: Float, y: Float, visibility: Float) = JointPoint(
        name = name,
        x = x,
        y = y,
        z = 0f,
        visibility = visibility,
    )
}
