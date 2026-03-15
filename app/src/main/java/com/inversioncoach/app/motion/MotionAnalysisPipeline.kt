package com.inversioncoach.app.motion

import com.inversioncoach.app.model.AlignmentStrictness
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.PoseFrame as LegacyPoseFrame

class MotionAnalysisPipeline(
    drillType: DrillType = DrillType.FREESTANDING_HANDSTAND_FUTURE,
) {
    private val smoother = TemporalPoseSmoother()
    private val angleEngine = AngleEngine()
    private val drillDefinition = DrillCatalog.byType(drillType)
    private val profile = DrillQualityProfiles.byType(drillType)
    private val phaseDetector = MovementPhaseDetector(
        thresholds = PhaseThresholds(
            downStartDeg = 165f,
            bottomDeg = 95f,
            upStartDeg = 110f,
            topDeg = 168f,
        ),
        trackedAngle = trackedAngleFor(drillDefinition.movementPattern),
    )
    private var alignmentEngine = AlignmentScoringEngine(profile, UserCalibrationSettings(AlignmentStrictness.BEGINNER))
    private var holdQualityTracker = HoldQualityTracker(UserCalibrationSettings(AlignmentStrictness.BEGINNER).resolvedThresholds())
    private var repQualityEvaluator = RepQualityEvaluator(profile, UserCalibrationSettings(AlignmentStrictness.BEGINNER).resolvedThresholds())
    private val holdTrackerCompat = HoldAlignmentTracker()
    private val faultEngine = FaultDetectionEngine(
        movementPattern = drillDefinition.movementPattern,
        allowedFaultCodes = drillDefinition.commonFaults,
    )
    private val feedbackEngine = FeedbackEngine()
    private val stabilityEngine = StabilityAnalysisEngine()
    private var configuredStrictness: AlignmentStrictness = AlignmentStrictness.BEGINNER

    data class Output(
        val smoothed: SmoothedPoseFrame,
        val angles: AngleFrame,
        val movement: MovementState,
        val repTracking: RepTrackingSnapshot?,
        val holdTracking: HoldTrackingSnapshot?,
        val isAligned: Boolean,
        val faults: List<FaultEvent>,
        val cue: LiveCue?,
        val alignment: AlignmentScoreSnapshot,
        val stability: StabilitySnapshot,
        val holdQuality: HoldQualitySnapshot?,
        val repQuality: RepQualitySnapshot?,
    )

    fun analyze(frame: LegacyPoseFrame, strictness: AlignmentStrictness = AlignmentStrictness.BEGINNER, calibration: UserCalibrationSettings? = null): Output {
        val effectiveCalibration = calibration ?: UserCalibrationSettings(strictness)
        configureStrictnessIfNeeded(effectiveCalibration)

        val motionFrame = PoseFrame(
            timestampMs = frame.timestampMs,
            landmarks = frame.joints.mapNotNull { raw ->
                mapJoint(raw.name)?.let { it to Landmark2D(raw.x, raw.y) }
            }.toMap(),
            confidenceByLandmark = frame.joints.mapNotNull { raw -> mapJoint(raw.name)?.let { it to raw.visibility } }.toMap(),
        )

        val smoothed = smoother.smooth(motionFrame)
        val angles = angleEngine.compute(smoothed)
        val movementProbe = MovementState(MovementPhase.HOLD, 0f, 0f, frame.timestampMs, 0)
        val preliminaryFaults = faultEngine.detect(angles, movementProbe)
        val alignment = alignmentEngine.score(angles, preliminaryFaults)
        val thresholds = effectiveCalibration.resolvedThresholds()
        val isAligned = alignment.smoothedScore >= thresholds.minimumGoodFormScore
        val movement = if (drillDefinition.repMode == RepMode.REP_BASED) {
            phaseDetector.update(angles, isAligned)
        } else {
            MovementState(MovementPhase.HOLD, if (isAligned) 1f else 0f, 0.8f, frame.timestampMs, 0)
        }
        val faults = faultEngine.detect(angles, movement)
        val cue = feedbackEngine.selectCue(faults, frame.timestampMs)
        val stability = stabilityEngine.analyze(smoothed)

        val repTracking = if (drillDefinition.repMode == RepMode.REP_BASED) phaseDetector.snapshot() else null
        val holdTracking = if (drillDefinition.repMode == RepMode.HOLD_BASED) holdTrackerCompat.update(frame.timestampMs, isAligned) else null

        val holdQuality = if (drillDefinition.repMode == RepMode.HOLD_BASED) {
            holdQualityTracker.update(frame.timestampMs, alignment.smoothedScore)
        } else {
            null
        }

        val repQuality = if (drillDefinition.repMode == RepMode.REP_BASED) {
            repQualityEvaluator.update(
                timestampMs = frame.timestampMs,
                movement = movement,
                repTracking = repTracking,
                alignmentScore = alignment.smoothedScore,
                dominantFault = alignment.dominantFault,
                angles = angles,
            )
        } else {
            null
        }

        return Output(
            smoothed = smoothed,
            angles = angles,
            movement = movement,
            repTracking = repTracking,
            holdTracking = holdTracking,
            isAligned = isAligned,
            faults = faults,
            cue = cue,
            alignment = alignment,
            stability = stability,
            holdQuality = holdQuality,
            repQuality = repQuality,
        )
    }

    private fun configureStrictnessIfNeeded(calibration: UserCalibrationSettings) {
        if (calibration.strictness == configuredStrictness && calibration.strictness != AlignmentStrictness.CUSTOM) return
        configuredStrictness = calibration.strictness
        alignmentEngine = AlignmentScoringEngine(profile, calibration)
        holdQualityTracker = HoldQualityTracker(calibration.resolvedThresholds())
        repQualityEvaluator = RepQualityEvaluator(profile, calibration.resolvedThresholds())
    }

    private fun mapJoint(name: String): JointId? = when (name) {
        "nose" -> JointId.NOSE
        "left_shoulder" -> JointId.LEFT_SHOULDER
        "right_shoulder" -> JointId.RIGHT_SHOULDER
        "left_elbow" -> JointId.LEFT_ELBOW
        "right_elbow" -> JointId.RIGHT_ELBOW
        "left_wrist" -> JointId.LEFT_WRIST
        "right_wrist" -> JointId.RIGHT_WRIST
        "left_hip" -> JointId.LEFT_HIP
        "right_hip" -> JointId.RIGHT_HIP
        "left_knee" -> JointId.LEFT_KNEE
        "right_knee" -> JointId.RIGHT_KNEE
        "left_ankle" -> JointId.LEFT_ANKLE
        "right_ankle" -> JointId.RIGHT_ANKLE
        else -> null
    }

    private fun trackedAngleFor(pattern: MovementPattern): String = when (pattern) {
        MovementPattern.VERTICAL_PUSH -> "left_elbow_flexion"
    }
}
