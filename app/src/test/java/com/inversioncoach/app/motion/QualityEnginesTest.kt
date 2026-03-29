package com.inversioncoach.app.motion

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.PoseFrame as LegacyPoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityEnginesTest {

    @Test
    fun alignmentScoring_variesByDrillProfile() {
        val frame = AngleFrame(1000L, mapOf("left_elbow_flexion" to 165f, "right_elbow_flexion" to 166f, "left_knee_flexion" to 170f, "right_knee_flexion" to 170f, "wrist_to_shoulder_line" to 12f), 8f, 6f, 0.08f)
        val standard = UserCalibrationSettings()
        val handstand = AlignmentScoringEngine(DrillQualityProfiles.byType(DrillType.FREE_HANDSTAND), standard)
        val pike = AlignmentScoringEngine(DrillQualityProfiles.byType(DrillType.PIKE_PUSH_UP), standard)

        val holdScore = handstand.score(frame, emptyList())
        val pikeScore = pike.score(frame, emptyList())

        assertTrue(holdScore.rawScore != pikeScore.rawScore)
    }

    @Test
    fun calibrationDefaultsMatchAnalyzerDefaults() {
        val defaults = UserCalibrationSettings().resolvedThresholds()
        assertEquals(72, defaults.minimumGoodFormScore)
        assertEquals(70, defaults.repAcceptanceThreshold)
    }

    @Test
    fun holdQualityAccumulatesAlignedDuration() {
        val tracker = HoldQualityTracker(UserCalibrationSettings().resolvedThresholds())
        tracker.update(0L, 80)
        tracker.update(200L, 85)
        val snapshot = tracker.update(500L, 84)

        assertTrue(snapshot.totalHoldDurationMs >= 500L)
        assertTrue(snapshot.alignedHoldDurationMs > 0L)
        assertTrue(snapshot.alignmentRate > 0f)
    }

    @Test
    fun repEvaluatorRejectsLowQualityRep() {
        val evaluator = RepQualityEvaluator(
            DrillQualityProfiles.byType(DrillType.PIKE_PUSH_UP),
            UserCalibrationSettings().resolvedThresholds(),
        )
        val repTracking = RepTrackingSnapshot(rawRepAttempts = 1, validRepCount = 0, alignmentPassRatio = 0.1f)
        val result = evaluator.update(
            timestampMs = 1200L,
            movement = MovementState(MovementPhase.TOP, 1f, 1f, 0L, 0),
            repTracking = repTracking,
            alignmentScore = 35,
            dominantFault = "body_line",
            angles = AngleFrame(1200L, mapOf("left_elbow_flexion" to 150f, "right_elbow_flexion" to 152f), 0f, 0f, 0.2f),
            frame = LegacyPoseFrame(1200L, emptyList(), 0.9f),
        )

        assertTrue(result.totalRepsDetected == 1)
        assertTrue(result.rejectedReps >= 1)
    }

    @Test
    fun repEvaluatorCollectsFramesOnlyAfterCycleStart() {
        val evaluator = RepQualityEvaluator(
            DrillQualityProfiles.byType(DrillType.PIKE_PUSH_UP),
            UserCalibrationSettings().resolvedThresholds(),
        )

        repeat(3) { idx ->
            evaluator.update(
                timestampMs = idx * 33L,
                movement = MovementState(MovementPhase.TOP, 0f, 1f, 0L, 0),
                repTracking = RepTrackingSnapshot(rawRepAttempts = 0, validRepCount = 0, alignmentPassRatio = 1f),
                alignmentScore = 80,
                dominantFault = "",
                angles = AngleFrame(idx * 33L, mapOf("left_elbow_flexion" to 170f, "right_elbow_flexion" to 170f), 0f, 0f, 0.05f),
                frame = LegacyPoseFrame(idx * 33L, emptyList(), 0.9f),
            )
        }

        val result = evaluator.update(
            timestampMs = 200L,
            movement = MovementState(MovementPhase.ECCENTRIC, 0.2f, 1f, 0L, 0),
            repTracking = RepTrackingSnapshot(rawRepAttempts = 1, validRepCount = 0, alignmentPassRatio = 1f),
            alignmentScore = 80,
            dominantFault = "",
            angles = AngleFrame(200L, mapOf("left_elbow_flexion" to 120f, "right_elbow_flexion" to 120f), 0f, 0f, 0.05f),
            frame = LegacyPoseFrame(200L, emptyList(), 0.9f),
        )

        assertEquals(1, result.totalRepsDetected)
        assertEquals(1, evaluator.completedRepWindows().first().frames.size)
    }

    @Test
    fun repEvaluatorRestartsCycleAfterStaleGapWithoutDroppingCurrentFrame() {
        val evaluator = RepQualityEvaluator(
            DrillQualityProfiles.byType(DrillType.PIKE_PUSH_UP),
            UserCalibrationSettings().resolvedThresholds(),
        )

        evaluator.update(
            timestampMs = 100L,
            movement = MovementState(MovementPhase.ECCENTRIC, 0.2f, 1f, 0L, 0),
            repTracking = RepTrackingSnapshot(rawRepAttempts = 0, validRepCount = 0, alignmentPassRatio = 1f),
            alignmentScore = 80,
            dominantFault = "",
            angles = AngleFrame(100L, mapOf("left_elbow_flexion" to 140f, "right_elbow_flexion" to 140f), 0f, 0f, 0.05f),
            frame = LegacyPoseFrame(100L, emptyList(), 0.9f),
        )

        val result = evaluator.update(
            timestampMs = 6200L,
            movement = MovementState(MovementPhase.ECCENTRIC, 0.3f, 1f, 0L, 0),
            repTracking = RepTrackingSnapshot(rawRepAttempts = 1, validRepCount = 0, alignmentPassRatio = 1f),
            alignmentScore = 80,
            dominantFault = "",
            angles = AngleFrame(6200L, mapOf("left_elbow_flexion" to 130f, "right_elbow_flexion" to 130f), 0f, 0f, 0.05f),
            frame = LegacyPoseFrame(6200L, emptyList(), 0.9f),
        )

        assertEquals(1, result.totalRepsDetected)
        assertEquals(1, evaluator.completedRepWindows().first().frames.size)
    }

    @Test
    fun stabilityEngineComputesDriftAndScore() {
        val engine = StabilityAnalysisEngine(windowSize = 6)
        val base = mapOf(
            JointId.LEFT_SHOULDER to Landmark2D(0.45f, 0.2f),
            JointId.RIGHT_SHOULDER to Landmark2D(0.55f, 0.2f),
            JointId.LEFT_HIP to Landmark2D(0.45f, 0.5f),
            JointId.RIGHT_HIP to Landmark2D(0.55f, 0.5f),
            JointId.LEFT_ANKLE to Landmark2D(0.45f, 0.8f),
            JointId.RIGHT_ANKLE to Landmark2D(0.55f, 0.8f),
        )
        var snapshot = StabilitySnapshot(0f, 0f, 0f, 0)
        repeat(6) { i ->
            val drifted = base.mapValues { (_, p) -> Landmark2D(p.x + (if (i % 2 == 0) 0.01f else -0.01f), p.y) }
            snapshot = engine.analyze(SmoothedPoseFrame(i * 100L, drifted, emptyMap()))
        }

        assertTrue(snapshot.swayAmplitude >= 0f)
        assertTrue(snapshot.stabilityScore in 0..100)
    }
}
