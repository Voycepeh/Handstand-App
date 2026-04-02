package com.inversioncoach.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.EffectiveView
import com.inversioncoach.app.overlay.FreestyleViewMode

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
    HANDSTAND_PUSH_UP("Handstand Push Up"),
    SIT_UP("Sit Up"),
    WALL_HANDSTAND("Wall Handstand"),
    BACK_TO_WALL_HANDSTAND("Back To Wall Handstand"),
    ELEVATED_PIKE_PUSH_UP("Elevated Pike Push Up"),
    WALL_HANDSTAND_PUSH_UP("Wall Handstand Push Up"),
    FREE_HANDSTAND("Free Handstand"),
    ;

    companion object {
        private val legacyNameMap: Map<String, DrillType> = mapOf(
            "PUSH_UP" to HANDSTAND_PUSH_UP,
            "CHEST_TO_WALL_HANDSTAND" to WALL_HANDSTAND,
            "FREESTANDING_HANDSTAND_FUTURE" to FREE_HANDSTAND,
            "NEGATIVE_WALL_HANDSTAND_PUSH_UP" to WALL_HANDSTAND_PUSH_UP,
        )

        fun fromStoredName(raw: String): DrillType? =
            entries.firstOrNull { it.name == raw } ?: legacyNameMap[raw]
    }
}

enum class SessionMode {
    DRILL,
    FREESTYLE,
}

enum class SessionStartupState {
    IDLE,
    COUNTDOWN,
    ACTIVE,
    CANCELLED,
}

enum class SessionSource {
    LIVE_COACHING,
    UPLOADED_VIDEO,
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
    val analysisWidth: Int = 0,
    val analysisHeight: Int = 0,
    val analysisRotationDegrees: Int = 0,
    val mirrored: Boolean = false,
)

data class SmoothedPoseFrame(
    val timestampMs: Long,
    val joints: List<JointPoint>,
    val confidence: Float,
    val analysisWidth: Int = 0,
    val analysisHeight: Int = 0,
    val analysisRotationDegrees: Int = 0,
    val mirrored: Boolean = false,
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

enum class AnnotatedExportStatus {
    NOT_STARTED,
    VALIDATING_INPUT,
    PROCESSING,
    PROCESSING_SLOW,
    ANNOTATED_READY,
    ANNOTATED_FAILED,
    SKIPPED,
}

enum class CompressionStatus {
    NOT_STARTED,
    COMPRESSING,
    READY,
    FAILED,
}

enum class CleanupStatus {
    NOT_STARTED,
    DELETING_INTERMEDIATES,
    COMPLETE,
    PARTIAL,
    FAILED,
}

enum class RetainedAssetType {
    ANNOTATED_FINAL,
    RAW_FINAL,
    NONE,
}

enum class RawPersistStatus {
    NOT_STARTED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
}

enum class AnnotatedExportStage {
    QUEUED,
    PREPARING,
    LOADING_OVERLAYS,
    DECODING_SOURCE,
    RENDERING,
    ENCODING,
    VERIFYING,
    COMPLETED,
    FAILED,
}

enum class AnnotatedExportFailureReason {
    EXPORT_INPUT_INVALID,
    EXPORT_INPUT_CORRUPTED_AFTER_FREEZE,
    RAW_VIDEO_INVALID,
    RAW_VIDEO_BLACK_FRAME,
    OVERLAY_CAPTURE_EMPTY,
    OVERLAY_TIMESTAMPS_NON_MONOTONIC,
    OVERLAY_FLUSH_NOT_COMPLETED,
    RAW_VIDEO_URI_NULL,
    RAW_PERSIST_FAILED,
    EXPORT_NOT_STARTED,
    RAW_SAVE_FAILED,
    RAW_URI_EMPTY,
    OVERLAY_FRAMES_EMPTY,
    OVERLAY_DATA_EMPTY,
    OVERLAY_TIMELINE_EMPTY,
    OVERLAY_TIMELINE_MISSING,
    RAW_VIDEO_MISSING,
    EXPORT_TIMEOUT,
    ENCODE_FAILED,
    VERIFICATION_FAILED,
    EXPORT_RETURNED_EMPTY,
    ANNOTATED_EXPORT_FAILED,
    EXPORT_TIMED_OUT,
    EXPORT_CANCELLED,
    OUTPUT_URI_NULL,
    OUTPUT_FILE_MISSING,
    OUTPUT_FILE_ZERO_BYTES,
    OUTPUT_METADATA_UNREADABLE,
    METADATA_UNREADABLE,
    EXPORT_RENDER_FAILED,
    EXPORT_ENCODE_FAILED,
    REPLAY_SELECTION_FELL_BACK_TO_RAW,
    ANNOTATED_URI_NOT_PERSISTED,
    UNKNOWN_EXCEPTION,
    SOURCE_VIDEO_UNREADABLE,
    EXTRACTOR_INIT_FAILED,
    VIDEO_TRACK_NOT_FOUND,
    DECODER_INIT_FAILED,
    ENCODER_INIT_FAILED,
    INPUT_SURFACE_INIT_FAILED,
    MUXER_INIT_FAILED,
    FIRST_FRAME_DECODE_TIMEOUT,
    EXPORT_FAILED_AFTER_START,
    MUX_FINALIZE_FAILED,
    EGL_INIT_FAILED,
    GL_SHADER_COMPILE_FAILED,
    GL_PROGRAM_LINK_FAILED,
    ANNOTATED_COMPRESSION_FAILED,
    RAW_COMPRESSION_FAILED,
    RAW_REPLAY_INVALID,
    RAW_MEDIA_CORRUPT,
    STALE_PROCESSING_STATE,
    CLEANUP_DELETE_FAILED,
    RENDER_PIPELINE_EXCEPTION,
    UNKNOWN,
}

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

@Entity(tableName = "reference_template_records")
data class ReferenceTemplateRecord(
    @PrimaryKey val id: String,
    val drillId: String,
    val displayName: String,
    val templateType: String,
    val sourceType: String = "REFERENCE_UPLOAD",
    val sourceSessionId: Long? = null,
    val title: String = displayName,
    val phasePosesJson: String = "",
    val keyframesJson: String = "",
    val fpsHint: Int? = null,
    val durationMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long = createdAtMs,
    val isBaseline: Boolean = false,
    val sourceProfileIdsJson: String,
    val checkpointJson: String,
    val toleranceJson: String,
)

@Entity(tableName = "session_comparison_records")
data class SessionComparisonRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long?,
    val subjectAssetId: String,
    val subjectProfileId: String,
    val drillId: String,
    val templateId: String,
    val overallSimilarityScore: Int,
    val phaseScoresJson: String,
    val differencesJson: String,
    val summary: String,
    val scoringVersion: Int,
    val createdAtMs: Long,
)

@Entity(tableName = "drill_definition_records")
data class DrillDefinitionRecord(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val movementMode: String,
    val cameraView: String,
    val phaseSchemaJson: String,
    val keyJointsJson: String,
    val normalizationBasisJson: String,
    val cueConfigJson: String,
    val sourceType: String,
    val status: String,
    val version: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(tableName = "reference_asset_records")
data class ReferenceAssetRecord(
    @PrimaryKey val id: String,
    val drillId: String,
    val displayName: String,
    val ownerType: String,
    val sourceType: String,
    val videoUri: String?,
    val poseUri: String?,
    val profileUri: String?,
    val thumbnailUri: String?,
    val isReference: Boolean,
    val qualityLabel: String?,
    val createdAtMs: Long,
)

@Entity(tableName = "movement_profile_records")
data class MovementProfileRecord(
    @PrimaryKey val id: String,
    val assetId: String,
    val drillId: String,
    val extractionVersion: Int,
    val poseTimelineJson: String,
    val normalizedFeatureJson: String,
    val repSegmentsJson: String,
    val holdSegmentsJson: String,
    val createdAtMs: Long,
)

@Entity(tableName = "calibration_config_records")
data class CalibrationConfigRecord(
    @PrimaryKey val id: String,
    val drillId: String,
    val displayName: String,
    val configJson: String,
    val scoringVersion: Int,
    val featureVersion: Int,
    val isActive: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "session_records",
    indices = [
        androidx.room.Index(value = ["drillId"]),
        androidx.room.Index(value = ["referenceTemplateId"]),
    ],
)
data class SessionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val drillType: DrillType,
    val sessionSource: SessionSource = SessionSource.LIVE_COACHING,
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
    val rawMasterUri: String? = rawVideoUri,
    val annotatedMasterUri: String? = annotatedVideoUri,
    val rawFinalUri: String? = rawVideoUri,
    val annotatedFinalUri: String? = annotatedVideoUri,
    val bestPlayableUri: String? = annotatedVideoUri ?: rawVideoUri,
    val rawPersistStatus: RawPersistStatus = RawPersistStatus.NOT_STARTED,
    val rawPersistFailureReason: String? = null,
    val annotatedExportStatus: AnnotatedExportStatus = AnnotatedExportStatus.NOT_STARTED,
    val annotatedExportFailureReason: String? = null,
    val annotatedExportFailureDetail: String? = null,
    val annotatedExportElapsedMs: Long? = null,
    val annotatedExportStageAtFailure: String? = null,
    val annotatedExportStage: AnnotatedExportStage = AnnotatedExportStage.QUEUED,
    val annotatedExportPercent: Int = 0,
    val annotatedExportEtaSeconds: Int? = null,
    val annotatedExportLastUpdatedAt: Long? = null,
    val uploadPipelineStageLabel: String? = null,
    val uploadAnalysisProcessedFrames: Int = 0,
    val uploadAnalysisTotalFrames: Int = 0,
    val uploadAnalysisTimestampMs: Long? = null,
    val uploadProgressDetail: String? = null,
    val rawCompressionStatus: CompressionStatus = CompressionStatus.NOT_STARTED,
    val annotatedCompressionStatus: CompressionStatus = CompressionStatus.NOT_STARTED,
    val cleanupStatus: CleanupStatus = CleanupStatus.NOT_STARTED,
    val retainedAssetType: RetainedAssetType = RetainedAssetType.NONE,
    val rawPersistedAtMs: Long? = null,
    val annotatedExportedAtMs: Long? = null,
    val annotatedFinalizedAtMs: Long? = null,
    val rawFinalizedAtMs: Long? = null,
    val cleanupCompletedAtMs: Long? = null,
    val overlayFrameCount: Int = 0,
    val overlayTimelineUri: String? = null,
    val calibrationProfileVersion: Int? = null,
    val calibrationUpdatedAtMs: Long? = null,
    val userProfileId: String? = null,
    val bodyProfileId: String? = null,
    val bodyProfileVersion: Int? = null,
    val usedDefaultBodyModel: Boolean = false,
    val drillId: String? = null,
    val referenceTemplateId: String? = null,
    val notesUri: String?,
    val bestFrameTimestampMs: Long?,
    val worstFrameTimestampMs: Long?,
    val topImprovementFocus: String,
)

@Entity(tableName = "user_profile_records")
data class UserProfileRecord(
    @PrimaryKey val id: String,
    val displayName: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val isArchived: Boolean = false,
)

@Entity(tableName = "body_profile_records")
data class BodyProfileRecord(
    @PrimaryKey val id: String,
    val userProfileId: String,
    val version: Int,
    val payloadJson: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
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
    val cueFrequencySeconds: Float = 2f,
    val audioVolume: Float = 1f,
    val localOnlyPrivacyMode: Boolean = true,
    val retainDays: Int = 60,
    val debugOverlayEnabled: Boolean = false,
    val maxStorageMb: Int = 5 * 1024,
    val startupCountdownSeconds: Int = 10,
    val annotatedExportQuality: String = AnnotatedExportQuality.STABLE.name,
    val hasCompletedPreferencesOnboarding: Boolean = false,
    val minSessionDurationSeconds: Int = 3,
    val drillCameraSideSelections: String = "",
    val activeUserProfileId: String? = null,
    val userBodyProfileJson: String? = null,
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
    val startupState: SessionStartupState = SessionStartupState.IDLE,
    val sessionCountdownRemainingSeconds: Int? = null,
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
    val unreliableJointNames: Set<String> = emptySet(),
    val drillCameraSide: DrillCameraSide? = null,
    val freestyleViewMode: FreestyleViewMode = FreestyleViewMode.UNKNOWN,
)

data class LiveSessionOptions(
    val voiceEnabled: Boolean = true,
    val recordingEnabled: Boolean = true,
    val showSkeletonOverlay: Boolean = true,
    val showIdealLine: Boolean = true,
    val showCenterOfGravity: Boolean = true,
    val zoomOutCamera: Boolean = true,
    val drillCameraSide: DrillCameraSide = DrillCameraSide.LEFT,
    val effectiveView: EffectiveView = EffectiveView.FREESTYLE,
    val selectedDrillId: String? = null,
) {
    companion object {
        fun freestyleDefaults(): LiveSessionOptions = LiveSessionOptions(
            voiceEnabled = false,
            recordingEnabled = true,
            showSkeletonOverlay = true,
            showIdealLine = true,
            showCenterOfGravity = true,
            zoomOutCamera = true,
            effectiveView = EffectiveView.FREESTYLE,
        )
    }
}
