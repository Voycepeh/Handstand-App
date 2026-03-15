package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.CueStyle
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }


data class NormalizedPose(
    val timestampMs: Long,
    val dominantSide: BodySide,
    val joints: Map<String, JointPoint>,
    val midpoints: Map<String, JointPoint>,
    val torsoLength: Float,
    val confidenceLevel: ConfidenceLevel,
    val confidence: Float,
)

enum class BodySide { LEFT, RIGHT }

data class DerivedMetrics(
    val timestampMs: Long,
    val jointAngles: Map<String, Float>,
    val segmentVerticalDeviation: Map<String, Float>,
    val stackOffsetsNorm: Map<String, Float>,
    val bodyLineDeviationNorm: Float,
    val kneeExtensionScore: Int,
    val bananaProxyScore: Int,
    val pelvicControlProxyScore: Int,
    val shoulderOpennessScore: Int,
    val scapularElevationProxyScore: Int,
    val tempoMetrics: Map<String, Float>,
    val pathMetrics: Map<String, Float>,
    val confidenceLevel: ConfidenceLevel,
    val confidence: Float,
)

enum class IssueSeverity { MINOR, MODERATE, MAJOR }

enum class IssueType {
    TRACKING_POOR,
    BANANA_ARCH,
    SHOULDERS_NOT_OPEN,
    PASSIVE_SHOULDERS,
    SOFT_KNEES,
    HEAD_OUT,
    HIPS_OFF_STACK,
    WALL_RELIANCE,
    HIPS_TOO_LOW,
    HEAD_PATH_FORWARD,
    ELBOWS_FLARING,
    INCOMPLETE_LOCKOUT,
    RUSHED_DESCENT,
    HIPS_DRIFTING,
    INSUFFICIENT_DEPTH,
    INCONSISTENT_PATH,
    LOSING_LINE_MIDWAY,
    HIPS_FOLDING,
    SHOULDER_COLLAPSE,
}

data class IssueInstance(
    val type: IssueType,
    val severity: IssueSeverity,
    val sinceTimestampMs: Long,
    val detail: String,
)

data class CueDecision(
    val category: String,
    val style: CueStyle,
    val text: String,
    val isEncouragement: Boolean,
    val issue: IssueType?,
)

enum class ThresholdStrictness {
    BEGINNER,
    STANDARD,
    ADVANCED,
}

data class DrillThresholdProfile(
    val drillType: DrillType,
    val holdStartStableMs: Long,
    val visualPersistFrames: Int,
    val spokenPersistFrames: Int,
    val sameCueCooldownMs: Long,
    val sameIssueFamilyCooldownMs: Long,
    val encouragementCooldownMs: Long,
    val stackExcellentNorm: Float,
    val stackAcceptableNorm: Float,
    val stackPoorNorm: Float,
    val bodyLineGoodNorm: Float,
    val bodyLineWarnNorm: Float,
    val bodyLinePoorNorm: Float,
    val elbowExcellentDeg: Float,
    val elbowAcceptableDeg: Float,
    val elbowSoftDeg: Float,
    val kneeExcellentDeg: Float,
    val kneeAcceptableDeg: Float,
    val kneeSoftDeg: Float,
    val shoulderExcellentMinDeg: Float,
    val shoulderExcellentMaxDeg: Float,
    val shoulderAcceptableMinDeg: Float,
    val shoulderLimitedMinDeg: Float,
    val hipLineExcellentMinDeg: Float,
    val hipLineExcellentMaxDeg: Float,
    val hipLineAcceptableMinDeg: Float,
    val kneeGoodDeg: Float,
    val kneeWarnDeg: Float,
    val lockoutDeg: Float,
    val lockoutWarnDeg: Float,
    val elbowBottomFullDepthMinDeg: Float,
    val elbowBottomFullDepthMaxDeg: Float,
    val elbowBottomCollapseDeg: Float,
    val descentGoodSec: Float,
    val descentAcceptableSec: Float,
    val descentPoorSec: Float,
    val hipAboveShoulderNormMin: Float,
    val headForwardNormMax: Float,
    val archHipNormThreshold: Float,
    val archMarginNorm: Float,
    val wallNearNorm: Float,
    val shoulderEarNearNorm: Float,
)

data class DrillScoreBreakdown(
    val overall: Int,
    val subScores: Map<String, Int>,
    val strongestArea: String,
    val mainLimiter: String,
)

data class RepAnalysis(
    val repIndex: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val topFrameTs: Long,
    val bottomFrameTs: Long,
    val worstAlignmentFrameTs: Long,
    val metrics: Map<String, Float>,
    val score: DrillScoreBreakdown,
    val issues: List<IssueInstance>,
    val headPath: List<Pair<Float, Float>>,
    val shoulderPath: List<Pair<Float, Float>>,
    val hipPath: List<Pair<Float, Float>>,
    val alignmentLossPercent: Int? = null,
)

data class HoldAnalysis(
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val durationMs: Long,
    val best3sWindowScore: Int,
    val longestGreenZoneStreakMs: Long,
    val acceptableAlignmentPercent: Int,
)

data class SessionAnalysis(
    val drillType: DrillType,
    val score: DrillScoreBreakdown,
    val reps: List<RepAnalysis>,
    val hold: HoldAnalysis?,
    val issueTimeline: List<IssueTimelineRange>,
    val bestFrameTimestampMs: Long?,
    val worstFrameTimestampMs: Long?,
    val mostCommonIssue: IssueType?,
    val consistencyScore: Int,
    val summary: SessionNarrative,
    val recommendation: DrillRecommendation,
    val debugFrames: List<DebugFrameData>,
)

data class IssueTimelineRange(
    val issue: IssueType,
    val severity: IssueSeverity,
    val startMs: Long,
    val endMs: Long,
)

data class SessionNarrative(
    val whatWentWell: String,
    val whatBrokeDown: String,
    val whereItBrokeDown: String,
    val focusNext: String,
    val nextDrillSuggestion: String,
)

data class DrillRecommendation(
    val drillName: String,
    val reason: String,
    val cueFocus: String,
)

data class MetricDebugEvaluation(
    val metricKey: String,
    val rawValue: Float,
    val thresholdBand: String,
    val subScore: Int,
    val triggeredIssue: IssueType? = null,
)

data class DebugFrameData(
    val timestampMs: Long,
    val rawJointMap: Map<String, Pair<Float, Float>>,
    val confidences: Map<String, Float>,
    val derivedAngles: Map<String, Float>,
    val normalizedOffsets: Map<String, Float>,
    val metricDebug: List<MetricDebugEvaluation>,
    val classifiedIssues: List<IssueType>,
    val cueTrace: String,
    val score: Int,
)

data class FrameAnalysis(
    val normalizedPose: NormalizedPose,
    val metrics: DerivedMetrics,
    val issues: List<IssueInstance>,
    val score: DrillScoreBreakdown,
    val cue: CueDecision?,
    val debug: DebugFrameData,
)

interface DrillAnalyzer {
    fun analyzeFrame(frame: PoseFrame): FrameAnalysis?
    fun finalizeRep(timestampMs: Long): RepAnalysis?
    fun finalizeSession(): SessionAnalysis
}
