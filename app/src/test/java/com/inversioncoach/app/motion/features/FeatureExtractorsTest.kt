package com.inversioncoach.app.motion.features

import com.inversioncoach.app.motion.AlignmentScoringEngine
import com.inversioncoach.app.motion.AngleEngine
import com.inversioncoach.app.motion.FaultEvent
import com.inversioncoach.app.motion.FaultSeverity
import com.inversioncoach.app.motion.BodySide
import com.inversioncoach.app.motion.DrillQualityProfiles
import com.inversioncoach.app.motion.JointId
import com.inversioncoach.app.motion.Landmark2D
import com.inversioncoach.app.motion.SmoothedPoseFrame
import com.inversioncoach.app.motion.UserCalibrationSettings
import com.inversioncoach.app.model.AlignmentStrictness
import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureExtractorsTest {
    @Test
    fun angleAndAlignmentExtractors_matchLegacyEngines() {
        val frame = SmoothedPoseFrame(
            timestampMs = 1000L,
            filteredLandmarks = mapOf(
                JointId.LEFT_SHOULDER to Landmark2D(0.4f, 0.3f),
                JointId.LEFT_ELBOW to Landmark2D(0.35f, 0.45f),
                JointId.LEFT_WRIST to Landmark2D(0.3f, 0.6f),
                JointId.RIGHT_SHOULDER to Landmark2D(0.6f, 0.3f),
                JointId.RIGHT_ELBOW to Landmark2D(0.65f, 0.45f),
                JointId.RIGHT_WRIST to Landmark2D(0.7f, 0.6f),
                JointId.LEFT_HIP to Landmark2D(0.45f, 0.55f),
                JointId.RIGHT_HIP to Landmark2D(0.55f, 0.55f),
                JointId.LEFT_ANKLE to Landmark2D(0.45f, 0.9f),
                JointId.RIGHT_ANKLE to Landmark2D(0.55f, 0.9f),
                JointId.NOSE to Landmark2D(0.5f, 0.15f),
            ),
            velocityByLandmark = emptyMap(),
        )

        val legacyAngles = AngleEngine().compute(frame)
        val wrappedAngles = DefaultAngleFeatureExtractor().compute(frame)
        assertEquals(legacyAngles.anglesDeg, wrappedAngles.anglesDeg)

        val profile = DrillQualityProfiles.byType(DrillType.FREE_HANDSTAND)
        val alignment = DefaultAlignmentFeatureExtractor(profile, UserCalibrationSettings(AlignmentStrictness.BEGINNER))
        val faults = listOf(FaultEvent("none", FaultSeverity.LOW, "", BodySide.NONE, 0L))
        val first = alignment.score(legacyAngles, faults)
        val second = alignment.score(wrappedAngles, faults)
        assertEquals(first.smoothedScore, second.smoothedScore)



        val advancedCalibration = UserCalibrationSettings(AlignmentStrictness.ADVANCED)
        alignment.reconfigure(advancedCalibration)
        val wrappedAdvanced = alignment.score(wrappedAngles, faults)
        val legacyAdvanced = AlignmentScoringEngine(profile, advancedCalibration).score(wrappedAngles, faults)
        assertEquals(legacyAdvanced.smoothedScore, wrappedAdvanced.smoothedScore)
        assertEquals(legacyAdvanced.instantScore, wrappedAdvanced.instantScore)

        val stability = DefaultStabilityFeatureExtractor().analyze(frame)
        assertEquals(stability.stabilityScore, DefaultStabilityFeatureExtractor().analyze(frame).stabilityScore)
    }
}
