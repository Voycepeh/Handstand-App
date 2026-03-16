package com.inversioncoach.app.ui.live

import android.net.Uri
import android.util.Log
import com.inversioncoach.app.biomechanics.DrillModeConfig
import com.inversioncoach.app.model.DrillScore
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.model.SessionRecord
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

class FrameValidityGate(
    private val drillType: DrillType,
    private val config: DrillModeConfig,
) {
    private val requiredJointsByDrill = mapOf(
        DrillType.FREESTYLE to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.CHEST_TO_WALL_HANDSTAND to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.PIKE_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.ELEVATED_PIKE_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.FREESTANDING_HANDSTAND_FUTURE to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
        DrillType.PUSH_UP to setOf("left_shoulder", "right_shoulder", "left_hip", "right_hip", "left_ankle", "right_ankle", "left_wrist", "right_wrist"),
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
    val annotatedUri = session?.annotatedVideoUri?.takeIf(::mediaAssetExists)
    if (annotatedUri != null) {
        return ReplayAssetSelection(uri = annotatedUri, label = "Annotated replay")
    }
    val rawUri = session?.rawVideoUri?.takeIf(::mediaAssetExists)
    if (rawUri != null) {
        return ReplayAssetSelection(uri = rawUri, label = "Raw replay")
    }
    return ReplayAssetSelection(uri = null, label = "Replay unavailable")
}

object SessionDiagnostics {
    private const val TAG = "SessionDiagnostics"

    fun log(message: String) {
        Log.d(TAG, message)
    }
}
