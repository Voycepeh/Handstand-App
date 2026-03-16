package com.inversioncoach.app.ui.live

import android.net.Uri
import android.util.Log
import com.inversioncoach.app.biomechanics.DrillModeConfig
import com.inversioncoach.app.model.DrillScore
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.overlay.DrillCameraSide
import java.io.File
import kotlin.math.abs

data class AggregatedIssueEvent(
    val issue: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val peakSeverity: Int,
    val representativeCue: String,
)

data class FrameValidityResult(
    val isValid: Boolean,
    val reason: String = "none",
)

enum class ReadinessState {
    NO_PERSON,
    PERSON_PARTIAL,
    READY_MINIMAL,
    READY_FULL,
}

data class SideChainQuality(
    val side: DrillCameraSide,
    val quality: Float,
    val presentCount: Int,
    val requiredCount: Int,
)

data class ReadinessEvaluation(
    val state: ReadinessState,
    val preferredSide: DrillCameraSide,
    val actualSide: DrillCameraSide,
    val leftQuality: SideChainQuality,
    val rightQuality: SideChainQuality,
    val requiredLandmarks: Set<String>,
    val presentLandmarks: Set<String>,
    val missingLandmarks: Set<String>,
    val timerEligible: Boolean,
    val repEligible: Boolean,
    val cueEligible: Boolean,
    val blockedReason: String,
)

class SharedReadinessEngine(
    private val drillType: DrillType,
    private val config: DrillModeConfig,
    private val preferredSide: DrillCameraSide,
) {
    private val requiredJoints = requiredJointsByDrill[drillType] ?: DEFAULT_REQUIRED_JOINTS
    private var stableState = ReadinessState.NO_PERSON
    private var enterCandidateCount = 0
    private var exitCandidateCount = 0

    fun evaluate(frame: PoseFrame): ReadinessEvaluation {
        val joints = frame.joints.associateBy { it.name }
        val presentLandmarks = joints.filterValues { it.visibility >= MIN_OVERLAY_VISIBILITY }.keys
        val missingLandmarks = requiredJoints - presentLandmarks
        val leftQuality = evaluateSideQuality(joints, DrillCameraSide.LEFT)
        val rightQuality = evaluateSideQuality(joints, DrillCameraSide.RIGHT)
        val actualSide = chooseActualSide(leftQuality, rightQuality)

        val visibleJoints = joints.values.count { it.visibility >= MIN_OVERLAY_VISIBILITY }
        val bestQuality = maxOf(leftQuality.quality, rightQuality.quality)
        val bestPresentCount = maxOf(leftQuality.presentCount, rightQuality.presentCount)

        val targetState = when {
            joints.isEmpty() || frame.confidence < MIN_NO_PERSON_CONFIDENCE || visibleJoints < MIN_VISIBLE_JOINTS_FOR_PARTIAL -> ReadinessState.NO_PERSON
            frame.confidence >= MIN_READY_FULL_CONFIDENCE && bestQuality >= MIN_FULL_CHAIN_QUALITY && missingLandmarks.size <= MAX_MISSING_FULL_LANDMARKS -> ReadinessState.READY_FULL
            frame.confidence >= MIN_READY_MINIMAL_CONFIDENCE && bestQuality >= MIN_MINIMAL_CHAIN_QUALITY && bestPresentCount >= MIN_CHAIN_LANDMARKS_FOR_MINIMAL -> ReadinessState.READY_MINIMAL
            else -> ReadinessState.PERSON_PARTIAL
        }

        val smoothedState = smoothState(targetState)
        val blockedReason = when (smoothedState) {
            ReadinessState.NO_PERSON -> "no_person_detected"
            ReadinessState.PERSON_PARTIAL -> when {
                frame.confidence < MIN_READY_MINIMAL_CONFIDENCE -> "low_confidence"
                bestPresentCount < MIN_CHAIN_LANDMARKS_FOR_MINIMAL -> "missing_required_landmarks"
                config.sideViewPrimary && bestQuality < MIN_MINIMAL_CHAIN_QUALITY -> "wrong_orientation"
                else -> "body_not_fully_visible"
            }
            else -> "none"
        }

        val isReadyMinimal = smoothedState >= ReadinessState.READY_MINIMAL
        return ReadinessEvaluation(
            state = smoothedState,
            preferredSide = preferredSide,
            actualSide = actualSide,
            leftQuality = leftQuality,
            rightQuality = rightQuality,
            requiredLandmarks = requiredJoints,
            presentLandmarks = presentLandmarks,
            missingLandmarks = missingLandmarks,
            timerEligible = isReadyMinimal,
            repEligible = isReadyMinimal,
            cueEligible = smoothedState >= ReadinessState.PERSON_PARTIAL,
            blockedReason = blockedReason,
        )
    }

    private fun smoothState(target: ReadinessState): ReadinessState {
        return if (target.ordinal > stableState.ordinal) {
            enterCandidateCount += 1
            exitCandidateCount = 0
            if (enterCandidateCount >= FRAMES_TO_ENTER_READY) {
                stableState = target
                enterCandidateCount = 0
            }
            stableState
        } else if (target.ordinal < stableState.ordinal) {
            exitCandidateCount += 1
            enterCandidateCount = 0
            if (exitCandidateCount >= FRAMES_TO_EXIT_READY) {
                stableState = target
                exitCandidateCount = 0
            }
            stableState
        } else {
            enterCandidateCount = 0
            exitCandidateCount = 0
            stableState
        }
    }

    private fun chooseActualSide(left: SideChainQuality, right: SideChainQuality): DrillCameraSide {
        val preferredQuality = if (preferredSide == DrillCameraSide.LEFT) left else right
        val oppositeQuality = if (preferredSide == DrillCameraSide.LEFT) right else left
        return if (oppositeQuality.quality - preferredQuality.quality >= SIDE_FALLBACK_QUALITY_MARGIN) {
            oppositeQuality.side
        } else {
            preferredSide
        }
    }

    private fun evaluateSideQuality(joints: Map<String, com.inversioncoach.app.model.JointPoint>, side: DrillCameraSide): SideChainQuality {
        val prefix = if (side == DrillCameraSide.LEFT) "left_" else "right_"
        val chain = SIDE_CHAIN_JOINTS.map { "$prefix$it" }
        val present = chain.mapNotNull { joints[it]?.visibility?.takeIf { visibility -> visibility >= MIN_OVERLAY_VISIBILITY } }
        val presenceRatio = present.size.toFloat() / chain.size.toFloat()
        val avgVisibility = if (present.isEmpty()) 0f else present.average().toFloat()
        val quality = (presenceRatio * 0.6f) + (avgVisibility * 0.4f)
        return SideChainQuality(
            side = side,
            quality = quality.coerceIn(0f, 1f),
            presentCount = present.size,
            requiredCount = chain.size,
        )
    }

    companion object {
        private val DEFAULT_REQUIRED_JOINTS = setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_knee", "right_knee")
        private val requiredJointsByDrill = mapOf(
            DrillType.FREESTYLE to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
            DrillType.WALL_HANDSTAND to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
            DrillType.PIKE_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
            DrillType.ELEVATED_PIKE_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
            DrillType.WALL_HANDSTAND_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
            DrillType.FREE_HANDSTAND to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
            DrillType.HANDSTAND_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        )
        private val SIDE_CHAIN_JOINTS = listOf("shoulder", "elbow", "wrist", "hip", "knee", "ankle")
        private const val MIN_OVERLAY_VISIBILITY = 0.2f
        private const val MIN_NO_PERSON_CONFIDENCE = 0.12f
        private const val MIN_READY_MINIMAL_CONFIDENCE = 0.28f
        private const val MIN_READY_FULL_CONFIDENCE = 0.42f
        private const val MIN_VISIBLE_JOINTS_FOR_PARTIAL = 4
        private const val MIN_CHAIN_LANDMARKS_FOR_MINIMAL = 4
        private const val MIN_MINIMAL_CHAIN_QUALITY = 0.44f
        private const val MIN_FULL_CHAIN_QUALITY = 0.67f
        private const val MAX_MISSING_FULL_LANDMARKS = 2
        private const val SIDE_FALLBACK_QUALITY_MARGIN = 0.15f
        private const val FRAMES_TO_ENTER_READY = 3
        private const val FRAMES_TO_EXIT_READY = 4
    }
}

class FrameValidityGate(
    private val drillType: DrillType,
    private val config: DrillModeConfig,
) {
    private val requiredJointsByDrill = mapOf(
        DrillType.FREESTYLE to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.WALL_HANDSTAND to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.PIKE_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.ELEVATED_PIKE_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.WALL_HANDSTAND_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.FREE_HANDSTAND to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.HANDSTAND_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
    )

    fun evaluate(frame: PoseFrame): FrameValidityResult {
        if (frame.confidence < MIN_FRAME_CONFIDENCE) return FrameValidityResult(false, "low_confidence")
        val joints = frame.joints.associateBy { it.name }
        val required = requiredJointsByDrill[drillType] ?: DEFAULT_REQUIRED_JOINTS
        if (required.any { joints[it]?.visibility ?: 0f < MIN_JOINT_VISIBILITY }) {
            return FrameValidityResult(false, "missing_required_landmarks")
        }
        val inFrameRatio = joints.values.count { it.x in 0.02f..0.98f && it.y in 0.02f..0.98f }.toFloat() / joints.size.coerceAtLeast(1)
        if (inFrameRatio < MIN_IN_FRAME_RATIO) return FrameValidityResult(false, "body_not_fully_visible")

        val xs = joints.values.map { it.x }
        val ys = joints.values.map { it.y }
        if (xs.isEmpty() || ys.isEmpty()) return FrameValidityResult(false, "missing_required_landmarks")
        val width = (xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)
        val height = (ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)
        if (height > MAX_BODY_SCALE || width > MAX_BODY_SCALE) return FrameValidityResult(false, "too_close_to_camera")
        if (height < MIN_BODY_SCALE) return FrameValidityResult(false, "too_far_from_camera")

        val leftShoulder = joints["left_shoulder"]
        val rightShoulder = joints["right_shoulder"]
        val leftHip = joints["left_hip"]
        val rightHip = joints["right_hip"]
        if (leftShoulder != null && rightShoulder != null && leftHip != null && rightHip != null) {
            val shoulderWidth = abs(leftShoulder.x - rightShoulder.x)
            val torsoHeight = abs(((leftShoulder.y + rightShoulder.y) / 2f) - ((leftHip.y + rightHip.y) / 2f)).coerceAtLeast(0.001f)
            val shoulderToTorsoRatio = shoulderWidth / torsoHeight
            if (config.sideViewPrimary && shoulderToTorsoRatio > MAX_SIDE_VIEW_SHOULDER_RATIO) {
                return FrameValidityResult(false, "wrong_orientation")
            }
        }

        return FrameValidityResult(true)
    }

    companion object {
        private val DEFAULT_REQUIRED_JOINTS = setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_knee", "right_knee")
        private const val MIN_FRAME_CONFIDENCE = 0.45f
        private const val MIN_JOINT_VISIBILITY = 0.4f
        private const val MIN_IN_FRAME_RATIO = 0.8f
        private const val MIN_BODY_SCALE = 0.18f
        private const val MAX_BODY_SCALE = 0.92f
        private const val MAX_SIDE_VIEW_SHOULDER_RATIO = 0.85f
    }
}

class IssueEventAggregator(
    private val minDurationMs: Long = 650L,
    private val maxGapMs: Long = 350L,
) {
    private data class OpenEvent(
        val issue: String,
        var startMs: Long,
        var endMs: Long,
        var peakSeverity: Int,
        var cue: String,
    )

    private val openByIssue = linkedMapOf<String, OpenEvent>()
    private val completed = mutableListOf<AggregatedIssueEvent>()

    fun onIssue(ts: Long, issue: String, severity: Int, cue: String?) {
        closeStaleEvents(ts)
        val existing = openByIssue[issue]
        if (existing == null) {
            openByIssue[issue] = OpenEvent(issue = issue, startMs = ts, endMs = ts, peakSeverity = severity, cue = cue.orEmpty())
            return
        }
        if (ts - existing.endMs > maxGapMs) {
            close(issue)
            openByIssue[issue] = OpenEvent(issue = issue, startMs = ts, endMs = ts, peakSeverity = severity, cue = cue.orEmpty())
            return
        }
        existing.endMs = ts
        existing.peakSeverity = maxOf(existing.peakSeverity, severity)
        if (existing.cue.isBlank() && !cue.isNullOrBlank()) existing.cue = cue
    }

    fun flushAll(endMs: Long): List<AggregatedIssueEvent> {
        closeStaleEvents(endMs)
        openByIssue.keys.toList().forEach {
            close(it)
        }
        return completed.toList()
    }

    fun aggregatedSoFar(): List<AggregatedIssueEvent> = completed.toList()

    private fun close(issue: String) {
        val event = openByIssue.remove(issue) ?: return
        val duration = event.endMs - event.startMs
        if (duration >= minDurationMs) {
            completed += AggregatedIssueEvent(
                issue = issue,
                startMs = event.startMs,
                endMs = event.endMs,
                durationMs = duration,
                peakSeverity = event.peakSeverity,
                representativeCue = event.cue.ifBlank { "Maintain control" },
            )
        }
    }

    private fun closeStaleEvents(referenceTs: Long) {
        openByIssue
            .filterValues { referenceTs - it.endMs > maxGapMs }
            .keys
            .toList()
            .forEach(::close)
    }
}

data class SessionComputation(
    val score: DrillScore,
    val topIssue: String,
    val strongestArea: String,
    val topImprovementFocus: String,
    val summaryWins: String,
    val status: String,
)

data class ReplayAssetSelection(
    val uri: String?,
    val label: String,
)

data class PreferredReplayUri(
    val uri: String?,
    val source: String,
)

object SessionSummaryComputer {
    fun compute(
        validFrameScores: List<Int>,
        latestScore: DrillScore,
        events: List<AggregatedIssueEvent>,
    ): SessionComputation {
        if (validFrameScores.size < MIN_VALID_FRAMES) {
            return SessionComputation(
                score = DrillScore(0, emptyMap(), "n/a", "insufficient_data"),
                topIssue = "insufficient_data",
                strongestArea = "n/a",
                topImprovementFocus = "Retry with full-body side view and stable lighting",
                summaryWins = "Session invalid: insufficient high-quality frames for reliable analysis.",
                status = "invalid",
            )
        }

        val avgScore = validFrameScores.average().toInt()
        val topIssue = events.groupingBy { it.issue }.eachCount().maxByOrNull { it.value }?.key ?: "none"
        val strongest = latestScore.strongestArea.takeIf { it.isNotBlank() } ?: "consistency"
        val limiter = when {
            topIssue != "none" -> topIssue
            latestScore.limitingFactor.isNotBlank() -> latestScore.limitingFactor
            else -> "consistency"
        }

        return SessionComputation(
            score = latestScore.copy(overall = avgScore, limitingFactor = limiter, strongestArea = strongest),
            topIssue = topIssue,
            strongestArea = strongest,
            topImprovementFocus = limiter.replace('_', ' '),
            summaryWins = "Strongest area: ${strongest.replace('_', ' ')}",
            status = "valid",
        )
    }

    private const val MIN_VALID_FRAMES = 12
}

fun mediaAssetExists(uri: String?): Boolean {
    if (uri.isNullOrBlank()) return false
    val path = runCatching { Uri.parse(uri).path }.getOrNull() ?: return false
    return File(path).exists()
}

fun selectReplayAsset(session: SessionRecord?): ReplayAssetSelection {
    val preferred = resolvePreferredReplayUri(session)
    return when (preferred.source) {
        "annotated" -> ReplayAssetSelection(uri = preferred.uri, label = "Annotated replay")
        "raw" -> ReplayAssetSelection(uri = preferred.uri, label = "Raw replay")
        else -> ReplayAssetSelection(uri = null, label = "Replay unavailable")
    }
}

fun resolvePreferredReplayUri(
    session: SessionRecord?,
    isReadable: (String?) -> Boolean = ::mediaAssetExists,
): PreferredReplayUri {
    val annotatedUri = session?.annotatedFinalUri ?: session?.annotatedVideoUri
    if (isReadable(annotatedUri)) {
        return PreferredReplayUri(uri = annotatedUri, source = "annotated")
    }
    val rawUri = session?.rawFinalUri ?: session?.rawVideoUri ?: session?.rawMasterUri
    if (isReadable(rawUri)) {
        return PreferredReplayUri(uri = rawUri, source = "raw")
    }
    return PreferredReplayUri(uri = null, source = "none")
}

object SessionDiagnostics {
    private const val TAG = "SessionDiagnostics"

    fun log(message: String) {
        Log.d(TAG, message)
    }

    fun logStructured(
        event: String,
        sessionId: Long?,
        drillType: DrillType,
        rawUri: String?,
        annotatedUri: String?,
        overlayFrameCount: Int,
        failureReason: String? = null,
    ) {
        Log.d(
            TAG,
            "event=$event sessionId=${sessionId ?: -1L} drillType=$drillType rawUri=${rawUri.orEmpty()} " +
                "annotatedUri=${annotatedUri.orEmpty()} overlayFrames=$overlayFrameCount " +
                "failureReason=${failureReason.orEmpty()}",
        )
    }
}
