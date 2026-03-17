package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.motion.AngleEngine
import com.inversioncoach.app.motion.AngleFrame
import com.inversioncoach.app.motion.HoldAlignmentTracker
import com.inversioncoach.app.motion.MovementPhase
import com.inversioncoach.app.motion.MovementPhaseDetector
import com.inversioncoach.app.motion.PhaseThresholds
import com.inversioncoach.app.ui.live.ReadinessState
import kotlin.math.abs

class PoseFrameNormalizer {
    fun normalize(frame: PoseFrame): PoseFrame {
        if (frame.joints.isEmpty()) return frame
        val minX = frame.joints.minOf { it.x }
        val maxX = frame.joints.maxOf { it.x }
        val minY = frame.joints.minOf { it.y }
        val maxY = frame.joints.maxOf { it.y }
        val width = (maxX - minX).coerceAtLeast(0.001f)
        val height = (maxY - minY).coerceAtLeast(0.001f)
        val normalized = frame.joints.map { joint ->
            joint.copy(
                x = ((joint.x - minX) / width).coerceIn(0f, 1f),
                y = ((joint.y - minY) / height).coerceIn(0f, 1f),
            )
        }
        return frame.copy(joints = normalized)
    }
}

class LandmarkVisibilityEvaluator {
    fun visibleLandmarks(frame: PoseFrame, minVisibility: Float): Set<String> =
        frame.joints.filter { it.visibility >= minVisibility }.map { it.name }.toSet()

    fun confidenceRatio(frame: PoseFrame, required: Set<String>, minVisibility: Float): Float {
        if (required.isEmpty()) return 1f
        val joints = frame.joints.associateBy { it.name }
        val visible = required.count { (joints[it]?.visibility ?: 0f) >= minVisibility }
        return visible.toFloat() / required.size.toFloat()
    }
}

class JointAngleEngine {
    private val angleEngine = AngleEngine()

    fun compute(frame: PoseFrame): AngleFrame {
        val asMap = frame.joints.mapNotNull { joint ->
            toJointIdOrNull(joint.name)?.let { it to com.inversioncoach.app.motion.Landmark2D(joint.x, joint.y) }
        }.toMap()
        return angleEngine.compute(
            com.inversioncoach.app.motion.SmoothedPoseFrame(
                timestampMs = frame.timestampMs,
                filteredLandmarks = asMap,
                velocityByLandmark = emptyMap(),
            )
        )
    }

    private fun toJointIdOrNull(name: String): com.inversioncoach.app.motion.JointId? =
        when (name.lowercase()) {
            "nose" -> com.inversioncoach.app.motion.JointId.NOSE
            "left_shoulder" -> com.inversioncoach.app.motion.JointId.LEFT_SHOULDER
            "right_shoulder" -> com.inversioncoach.app.motion.JointId.RIGHT_SHOULDER
            "left_elbow" -> com.inversioncoach.app.motion.JointId.LEFT_ELBOW
            "right_elbow" -> com.inversioncoach.app.motion.JointId.RIGHT_ELBOW
            "left_wrist" -> com.inversioncoach.app.motion.JointId.LEFT_WRIST
            "right_wrist" -> com.inversioncoach.app.motion.JointId.RIGHT_WRIST
            "left_hip" -> com.inversioncoach.app.motion.JointId.LEFT_HIP
            "right_hip" -> com.inversioncoach.app.motion.JointId.RIGHT_HIP
            "left_knee" -> com.inversioncoach.app.motion.JointId.LEFT_KNEE
            "right_knee" -> com.inversioncoach.app.motion.JointId.RIGHT_KNEE
            "left_ankle" -> com.inversioncoach.app.motion.JointId.LEFT_ANKLE
            "right_ankle" -> com.inversioncoach.app.motion.JointId.RIGHT_ANKLE
            else -> null
        }
}

class MotionPhaseDetector(profile: MovementProfile) {
    private val detector = MovementPhaseDetector(
        thresholds = if (profile.repRule != null) {
            PhaseThresholds(
                downStartDeg = profile.repRule.topThresholdDeg - 5f,
                bottomDeg = profile.repRule.bottomThresholdDeg,
                upStartDeg = profile.repRule.bottomThresholdDeg + 7f,
                topDeg = profile.repRule.topThresholdDeg,
                minDwellMs = profile.repRule.minRepDurationMs / 4L,
            )
        } else {
            PhaseThresholds(150f, 90f, 100f, 160f)
        },
        trackedAngle = profile.repRule?.angleKey ?: "elbow_avg",
    )

    fun update(angleFrame: AngleFrame, isAligned: Boolean): MovementPhase = detector.update(angleFrame, isAligned).currentPhase
}

class ReadinessEngine(
    private val visibilityEvaluator: LandmarkVisibilityEvaluator = LandmarkVisibilityEvaluator(),
) {
    fun evaluate(frame: PoseFrame, rule: ReadinessRule): ReadinessState {
        val visible = visibilityEvaluator.visibleLandmarks(frame, minVisibility = 0.2f)
        if (frame.confidence < rule.minConfidence / 2f || visible.size < 3) return ReadinessState.NO_PERSON
        val requiredVisible = rule.requiredLandmarks.count { visible.contains(it) }
        if (requiredVisible < rule.minVisibleLandmarkCount) return ReadinessState.PERSON_PARTIAL
        return if (frame.confidence >= rule.minConfidence) ReadinessState.READY_FULL else ReadinessState.READY_MINIMAL
    }
}

class HoldDetector(private val holdRule: HoldRule) {
    private val tracker = HoldAlignmentTracker(holdRule.maxBreakMs)
    fun update(timestampMs: Long, aligned: Boolean): Boolean = tracker.update(timestampMs, aligned).bestAlignedStreakMs >= holdRule.minHoldMs
}

class RepDetector(private val repRule: RepRule) {
    private var state: MovementPhase = MovementPhase.SETUP
    private var reps = 0

    fun update(angleDeg: Float): Int {
        when (state) {
            MovementPhase.SETUP -> if (angleDeg <= repRule.bottomThresholdDeg) state = MovementPhase.BOTTOM
            MovementPhase.BOTTOM -> if (angleDeg >= repRule.topThresholdDeg) {
                reps += 1
                state = MovementPhase.TOP
            }
            MovementPhase.TOP -> if (angleDeg < repRule.topThresholdDeg - 3f) state = MovementPhase.SETUP
            else -> state = MovementPhase.SETUP
        }
        return reps
    }
}

class AlignmentScorer {
    fun score(frame: PoseFrame, rules: List<AlignmentRule>): Float {
        if (rules.isEmpty()) return 1f
        val joints = frame.joints.associateBy { it.name }
        val scores = rules.map { rule ->
            val value = metricValue(rule.metricKey, joints)
            1f - (abs(value - rule.target) / rule.tolerance.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        }
        return scores.average().toFloat()
    }

    private fun metricValue(metric: String, joints: Map<String, JointPoint>): Float = when (metric) {
        "shoulder_hip_stack" -> {
            val shoulder = avgX(joints, "left_shoulder", "right_shoulder")
            val hip = avgX(joints, "left_hip", "right_hip")
            abs(shoulder - hip)
        }
        else -> 0f
    }

    private fun avgX(joints: Map<String, JointPoint>, left: String, right: String): Float {
        val l = joints[left]?.x ?: 0.5f
        val r = joints[right]?.x ?: 0.5f
        return (l + r) / 2f
    }
}

class MovementFeedbackEngine {
    fun feedback(readiness: ReadinessState, alignmentScore: Float, reps: Int, holdComplete: Boolean): List<String> {
        val cues = mutableListOf<String>()
        if (readiness < ReadinessState.READY_MINIMAL) cues += "Frame body fully before starting."
        if (alignmentScore < 0.65f) cues += "Stack shoulders over hips."
        if (reps > 0) cues += "Reps counted: $reps"
        if (holdComplete) cues += "Hold target achieved."
        return cues
    }
}
