package com.inversioncoach.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DrillType {
    WALL_PUSH_UP,
    INCLINE_OR_KNEE_PUSH_UP,
    BODYWEIGHT_SQUAT,
    REVERSE_LUNGE,
    FOREARM_PLANK,
    GLUTE_BRIDGE,
    STANDARD_PUSH_UP,
    PULL_UP_OR_ASSISTED_PULL_UP,
    PARALLEL_BAR_DIP,
    HANGING_KNEE_RAISE,
    PIKE_PUSH_UP,
    HOLLOW_BODY_HOLD,
    WALL_FACING_HANDSTAND_HOLD,
    L_SIT_HOLD,
    BURPEE,
    STANDING_POSTURE_HOLD,
    PUSH_UP,
    SIT_UP,
    CHEST_TO_WALL_HANDSTAND,
    BACK_TO_WALL_HANDSTAND,
    ELEVATED_PIKE_PUSH_UP,
    NEGATIVE_WALL_HANDSTAND_PUSH_UP,
    FREESTANDING_HANDSTAND_FUTURE,
}

data class JointPoint(
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
)

data class PoseFrame(
    val timestampMs: Long,
    val joints: List<JointPoint>,
    val confidence: Float,
    val landmarksDetected: Int = joints.size,
    val inferenceTimeMs: Long = 0L,
    val droppedFrames: Int = 0,
    val rejectionReason: String = "none",
)

data class SmoothedPoseFrame(
    val timestampMs: Long,
    val joints: List<JointPoint>,
    val confidence: Float,
)

data class AlignmentMetric(
    val key: String,
    val value: Float,
    val target: Float,
    val score: Int,
)

data class AngleDebugMetric(
    val key: String,
    val degrees: Float,
)

data class DrillScore(
    val overall: Int,
    val subScores: Map<String, Int>,
    val strongestArea: String,
    val limitingFactor: String,
)

enum class CueStyle { CONCISE, TECHNICAL, ENCOURAGING }

data class CoachingCue(
    val id: String,
    val text: String,
    val severity: Int,
    val generatedAtMs: Long,
)

data class Recommendation(
    val title: String,
    val reason: String,
    val drillFocus: DrillType,
)

@Entity(tableName = "session_records")
data class SessionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val drillType: DrillType,
    val startedAtMs: Long,
    val completedAtMs: Long,
    val overallScore: Int,
    val strongestArea: String,
    val limitingFactor: String,
    val issues: String,
    val wins: String,
    val metricsJson: String,
    val annotatedVideoUri: String?,
    val rawVideoUri: String?,
    val notesUri: String?,
    val bestFrameTimestampMs: Long?,
    val worstFrameTimestampMs: Long?,
    val topImprovementFocus: String,
)

@Entity(tableName = "frame_metric_records")
data class FrameMetricRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val confidence: Float,
    val overallScore: Int,
    val limitingFactor: String,
    val metricScoresJson: String,
    val anglesJson: String,
    val activeIssue: String?,
)

@Entity(tableName = "issue_events")
data class IssueEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val issue: String,
    val severity: Int,
)

data class SessionSummary(
    val headline: String,
    val whatWentWell: List<String>,
    val whatBrokeDown: List<String>,
    val whereItBrokeDown: String,
    val nextFocus: String,
    val recommendedDrill: Recommendation,
    val issueTimeline: List<String>,
)

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    val cueStyle: CueStyle = CueStyle.CONCISE,
    val cueFrequencySeconds: Float = 2f,
    val audioVolume: Float = 1f,
    val overlayIntensity: Float = 1f,
    val localOnlyPrivacyMode: Boolean = true,
    val retainDays: Int = 60,
    val debugOverlayEnabled: Boolean = false,
    val maxStorageMb: Int = 1024,
    val minSessionDurationSeconds: Int = 3,
)

data class LiveSessionUiState(
    val drillType: DrillType,
    val score: Int = 0,
    val currentCue: String = "",
    val currentCueId: String = "",
    val currentCueGeneratedAtMs: Long = 0L,
    val confidence: Float = 0f,
    val holdSeconds: Int = 0,
    val repCount: Int = 0,
    val currentPhase: String = "setup",
    val activeFault: String = "",
    val isRecording: Boolean = false,
    val showOverlay: Boolean = true,
    val showIdealLine: Boolean = true,
    val showDebugOverlay: Boolean = false,
    val cameraReady: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val warningMessage: String? = null,
    val errorMessage: String? = null,
    val debugAngles: List<AngleDebugMetric> = emptyList(),
    val debugMetrics: List<AlignmentMetric> = emptyList(),
    val debugLandmarksDetected: Int = 0,
    val debugInferenceTimeMs: Long = 0L,
    val debugFrameDrops: Int = 0,
    val debugRejectionReason: String = "none",
)

data class LiveSessionOptions(
    val voiceEnabled: Boolean = true,
    val recordingEnabled: Boolean = true,
    val showSkeletonOverlay: Boolean = true,
    val showIdealLine: Boolean = true,
    val zoomOutCamera: Boolean = true,
)
