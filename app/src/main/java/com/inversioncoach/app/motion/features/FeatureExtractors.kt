package com.inversioncoach.app.motion.features

import com.inversioncoach.app.motion.AlignmentScoreSnapshot
import com.inversioncoach.app.motion.AlignmentScoringEngine
import com.inversioncoach.app.motion.AngleEngine
import com.inversioncoach.app.motion.AngleFrame
import com.inversioncoach.app.motion.DrillQualityProfile
import com.inversioncoach.app.motion.FaultEvent
import com.inversioncoach.app.motion.SmoothedPoseFrame
import com.inversioncoach.app.motion.StabilityAnalysisEngine
import com.inversioncoach.app.motion.StabilitySnapshot
import com.inversioncoach.app.motion.UserCalibrationSettings

interface AngleFeatureExtractor {
    fun compute(frame: SmoothedPoseFrame): AngleFrame
}

interface AlignmentFeatureExtractor {
    fun score(frame: AngleFrame, faults: List<FaultEvent>): AlignmentScoreSnapshot
}

interface SymmetryFeatureExtractor {
    fun score(frame: AngleFrame): Float
}

interface StabilityFeatureExtractor {
    fun analyze(frame: SmoothedPoseFrame): StabilitySnapshot
}

class DefaultAngleFeatureExtractor(
    private val angleEngine: AngleEngine = AngleEngine(),
) : AngleFeatureExtractor {
    override fun compute(frame: SmoothedPoseFrame): AngleFrame = angleEngine.compute(frame)
}

class DefaultAlignmentFeatureExtractor(
    private val profile: DrillQualityProfile,
    calibration: UserCalibrationSettings,
) : AlignmentFeatureExtractor {
    private var alignmentEngine = AlignmentScoringEngine(profile, calibration)

    override fun score(frame: AngleFrame, faults: List<FaultEvent>): AlignmentScoreSnapshot = alignmentEngine.score(frame, faults)

    fun reconfigure(calibration: UserCalibrationSettings) {
        alignmentEngine = AlignmentScoringEngine(profile, calibration)
    }
}

class DefaultSymmetryFeatureExtractor : SymmetryFeatureExtractor {
    override fun score(frame: AngleFrame): Float {
        val left = frame.anglesDeg["left_elbow_flexion"] ?: return 1f
        val right = frame.anglesDeg["right_elbow_flexion"] ?: return 1f
        val diff = kotlin.math.abs(left - right)
        return (1f - (diff / 180f)).coerceIn(0f, 1f)
    }
}

class DefaultStabilityFeatureExtractor(
    private val stabilityEngine: StabilityAnalysisEngine = StabilityAnalysisEngine(),
) : StabilityFeatureExtractor {
    override fun analyze(frame: SmoothedPoseFrame): StabilitySnapshot = stabilityEngine.analyze(frame)
}
