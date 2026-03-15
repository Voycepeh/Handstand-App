package com.inversioncoach.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DrillType(val displayName: String) {
    FREESTYLE("Freestyle Live Coaching"),
    WALL_PUSH_UP("Wall Push Up"),
    INCLINE_OR_KNEE_PUSH_UP("Incline Or Knee Push Up"),
    BODYWEIGHT_SQUAT("Bodyweight Squat"),
    REVERSE_LUNGE("Reverse Lunge"),
    FOREARM_PLANK("Forearm Plank"),
    GLUTE_BRIDGE("Glute Bridge"),
    STANDARD_PUSH_UP("Standard Push Up"),
    PULL_UP_OR_ASSISTED_PULL_UP("Pull Up Or Assisted Pull Up"),
    PARALLEL_BAR_DIP("Parallel Bar Dip"),
    HANGING_KNEE_RAISE("Hanging Knee Raise"),
    PIKE_PUSH_UP("Pike Push Up"),
    HOLLOW_BODY_HOLD("Hollow Body Hold"),
    WALL_FACING_HANDSTAND_HOLD("Wall Facing Handstand Hold"),
    L_SIT_HOLD("L Sit Hold"),
    BURPEE("Burpee"),
    STANDING_POSTURE_HOLD("Standing Posture Hold"),
    PUSH_UP("Handstand Push Up"),
    SIT_UP("Sit Up"),
    CHEST_TO_WALL_HANDSTAND("Wall Handstand"),
    BACK_TO_WALL_HANDSTAND("Back To Wall Handstand"),
    ELEVATED_PIKE_PUSH_UP("Elevated Pike Push Up"),
    NEGATIVE_WALL_HANDSTAND_PUSH_UP("Wall Handstand Push Up"),
    FREESTANDING_HANDSTAND_FUTURE("Free Handstand"),
}

enum class SessionMode {
    DRILL,
    FREESTYLE,
}

fun DrillType.sessionMode(): SessionMode = if (this == DrillType.FREESTYLE) SessionMode.FREESTYLE else SessionMode.DRILL

fun DrillType.isFreestyle(): Boolean = sessionMode() == SessionMode.FREESTYLE

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

enum class AlignmentStrictness { BEGINNER, STANDARD, ADVANCED, CUSTOM }

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

fun SessionRecord.sessionMode(): SessionMode = drillType.sessionMode()

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
    val alignmentStrictness: AlignmentStrictness = AlignmentStrictness.BEGINNER,
    val customLineDeviation: Float = 0.14f,
    val customMinimumGoodFormScore: Int = 72,
    val customRepAcceptanceThreshold: Int = 70,
    val customHoldAlignedThreshold: Int = 72,
)

data class LiveSessionUiState(
    val drillType: DrillType,
    val sessionMode: SessionMode = drillType.sessionMode(),
    val score: Int = 0,
    val alignmentScore: Int = 0,
    val smoothedAlignmentScore: Int = 0,
    val stabilityScore: Int = 0,
    val currentCue: String = "",
    val currentCueId: String = "",
    val currentCueGeneratedAtMs: Long = 0L,
    val confidence: Float = 0f,
    val holdSeconds: Int = 0,
    val repCount: Int = 0,
    val rawRepCount: Int = 0,
    val totalAlignedDurationMs: Long = 0L,
    val currentAlignedStreakMs: Long = 0L,
    val bestAlignedStreakMs: Long = 0L,
    val totalSessionTrackedMs: Long = 0L,
    val currentPhase: String = "setup",
    val activeFault: String = "",
    val alignmentRate: Float = 0f,
    val averageAlignmentScore: Int = 0,
    val lastRepScore: Int = 0,
    val acceptedReps: Int = 0,
    val rejectedReps: Int = 0,
    val averageRepQuality: Int = 0,
    val bestRepScore: Int = 0,
    val mostCommonFailureReason: String = "",
    val averageStabilityScore: Int = 0,
    val peakDrift: Float = 0f,
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
) {
    companion object {
        fun freestyleDefaults(): LiveSessionOptions = LiveSessionOptions(
            voiceEnabled = false,
            recordingEnabled = true,
            showSkeletonOverlay = true,
            showIdealLine = true,
            zoomOutCamera = true,
        )
    }
}
