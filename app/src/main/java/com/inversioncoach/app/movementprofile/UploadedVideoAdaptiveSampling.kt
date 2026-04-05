package com.inversioncoach.app.movementprofile

import kotlin.math.abs

enum class SamplingMode {
    SPARSE,
    BURST,
    HOLD_STEADY,
    RECOVERY,
    LEGACY_FIXED,
}

enum class BurstTriggerReason {
    VISUAL_DIFF,
    SUBJECT_MOVEMENT,
    JOINT_MOVEMENT,
    CONFIDENCE_DROP,
    SEGMENT_BOUNDARY,
    GUARDRAIL_FIRST_SEGMENT,
    GUARDRAIL_LAST_SEGMENT,
    ROLLING_WINDOW_GUARDRAIL,
}

data class AdaptiveSamplingConfig(
    val enabled: Boolean = true,
    val fallbackToLegacyOnSignalLoss: Boolean = true,
    val legacyFixedFps: Int = 6,
    val candidateDecodeFps: Int = 12,
    val sparseIntervalMs: Long = 250L,
    val holdSteadyIntervalMs: Long = 300L,
    val repSteadyIntervalMs: Long = 200L,
    val burstIntervalMs: Long = 90L,
    val recoveryIntervalMs: Long = 140L,
    val burstCooldownMs: Long = 700L,
    val minRollingWindowSampleMs: Long = 333L,
    val visualDiffBurstThreshold: Double = 0.12,
    val subjectMoveBurstThreshold: Double = 0.06,
    val jointMoveBurstThreshold: Double = 0.08,
    val confidenceDropBurstThreshold: Double = 0.25,
    val burstScoreThreshold: Double = 0.20,
)

data class AdaptiveSamplingSignal(
    val timestampMs: Long,
    val videoDurationMs: Long,
    val visualDiff: Double,
    val subjectMovement: Double = 0.0,
    val jointMovement: Double = 0.0,
    val confidenceDrop: Double = 0.0,
    val signalReliable: Boolean = true,
)

data class AdaptiveSamplingDecision(
    val sample: Boolean,
    val mode: SamplingMode,
    val nextIntervalMs: Long,
    val reasons: Set<BurstTriggerReason>,
    val motionScore: Double,
)

class UploadedVideoSamplingPlanner(
    private val config: AdaptiveSamplingConfig = AdaptiveSamplingConfig(),
    private val movementType: MovementType?,
) {
    private var lastSampleTimestampMs: Long = Long.MIN_VALUE
    private var burstUntilMs: Long = Long.MIN_VALUE
    private var recoveryUntilMs: Long = Long.MIN_VALUE

    fun decide(signal: AdaptiveSamplingSignal): AdaptiveSamplingDecision {
        if (!config.enabled || (config.fallbackToLegacyOnSignalLoss && !signal.signalReliable)) {
            return decideLegacy(signal)
        }

        val reasons = mutableSetOf<BurstTriggerReason>()
        val inFirstSegment = signal.timestampMs <= 600L
        val inLastSegment = signal.videoDurationMs > 0 && signal.videoDurationMs - signal.timestampMs <= 500L
        if (inFirstSegment) reasons += BurstTriggerReason.GUARDRAIL_FIRST_SEGMENT
        if (inLastSegment) reasons += BurstTriggerReason.GUARDRAIL_LAST_SEGMENT

        if (signal.visualDiff >= config.visualDiffBurstThreshold) reasons += BurstTriggerReason.VISUAL_DIFF
        if (signal.subjectMovement >= config.subjectMoveBurstThreshold) reasons += BurstTriggerReason.SUBJECT_MOVEMENT
        if (signal.jointMovement >= config.jointMoveBurstThreshold) reasons += BurstTriggerReason.JOINT_MOVEMENT
        if (signal.confidenceDrop >= config.confidenceDropBurstThreshold) reasons += BurstTriggerReason.CONFIDENCE_DROP

        val motionScore = (signal.visualDiff * 0.45) +
            (signal.subjectMovement * 0.20) +
            (signal.jointMovement * 0.25) +
            (signal.confidenceDrop * 0.10)
        if (reasons.any { it in setOf(BurstTriggerReason.VISUAL_DIFF, BurstTriggerReason.SUBJECT_MOVEMENT, BurstTriggerReason.JOINT_MOVEMENT, BurstTriggerReason.CONFIDENCE_DROP) } || motionScore >= config.burstScoreThreshold) {
            burstUntilMs = maxOf(burstUntilMs, signal.timestampMs + config.burstCooldownMs)
            reasons += BurstTriggerReason.SEGMENT_BOUNDARY
        }

        val baseMode = when {
            signal.timestampMs <= burstUntilMs -> SamplingMode.BURST
            signal.timestampMs <= recoveryUntilMs -> SamplingMode.RECOVERY
            movementType == MovementType.HOLD -> SamplingMode.HOLD_STEADY
            else -> SamplingMode.SPARSE
        }
        val baseInterval = when (baseMode) {
            SamplingMode.BURST -> config.burstIntervalMs
            SamplingMode.HOLD_STEADY -> config.holdSteadyIntervalMs
            SamplingMode.SPARSE -> if (movementType == MovementType.REP) config.repSteadyIntervalMs else config.sparseIntervalMs
            SamplingMode.RECOVERY -> config.recoveryIntervalMs
            SamplingMode.LEGACY_FIXED -> legacyIntervalMs()
        }

        val timeSinceLast = if (lastSampleTimestampMs == Long.MIN_VALUE) Long.MAX_VALUE else signal.timestampMs - lastSampleTimestampMs
        val rollingGuardrailIntervalMs = minOf(config.minRollingWindowSampleMs, legacyIntervalMs())
        val mustRefresh = timeSinceLast >= rollingGuardrailIntervalMs
        if (mustRefresh && lastSampleTimestampMs != Long.MIN_VALUE) {
            reasons += BurstTriggerReason.ROLLING_WINDOW_GUARDRAIL
        }
        val shouldSample = lastSampleTimestampMs == Long.MIN_VALUE ||
            inFirstSegment ||
            inLastSegment ||
            mustRefresh ||
            timeSinceLast >= baseInterval

        if (shouldSample) {
            lastSampleTimestampMs = signal.timestampMs
            if (baseMode == SamplingMode.BURST) {
                recoveryUntilMs = signal.timestampMs + config.burstCooldownMs
            }
        }

        return AdaptiveSamplingDecision(
            sample = shouldSample,
            mode = baseMode,
            nextIntervalMs = baseInterval,
            reasons = reasons,
            motionScore = motionScore,
        )
    }

    private fun decideLegacy(signal: AdaptiveSamplingSignal): AdaptiveSamplingDecision {
        val interval = legacyIntervalMs()
        val elapsed = if (lastSampleTimestampMs == Long.MIN_VALUE) Long.MAX_VALUE else signal.timestampMs - lastSampleTimestampMs
        val shouldSample = lastSampleTimestampMs == Long.MIN_VALUE || elapsed >= interval
        if (shouldSample) lastSampleTimestampMs = signal.timestampMs
        return AdaptiveSamplingDecision(
            sample = shouldSample,
            mode = SamplingMode.LEGACY_FIXED,
            nextIntervalMs = interval,
            reasons = emptySet(),
            motionScore = 0.0,
        )
    }

    private fun legacyIntervalMs(): Long = (1000f / config.legacyFixedFps.coerceAtLeast(1)).toLong().coerceAtLeast(16L)
}

data class PoseSamplingSignal(
    val centerX: Float,
    val centerY: Float,
    val boxSize: Float,
    val confidence: Float,
    val wristHipDistance: Float,
)

object PoseSamplingSignalExtractor {
    fun fromFrame(frame: com.inversioncoach.app.model.PoseFrame): PoseSamplingSignal? {
        if (frame.joints.isEmpty()) return null
        val xs = frame.joints.map { it.x }
        val ys = frame.joints.map { it.y }
        val centerX = xs.average().toFloat()
        val centerY = ys.average().toFloat()
        val width = (xs.maxOrNull() ?: centerX) - (xs.minOrNull() ?: centerX)
        val height = (ys.maxOrNull() ?: centerY) - (ys.minOrNull() ?: centerY)
        val boxSize = (width + height) / 2f

        val wrist = frame.joints.firstOrNull { it.name.contains("wrist") }
        val hip = frame.joints.firstOrNull { it.name.contains("hip") }
        val wristHip = if (wrist != null && hip != null) {
            val dx = wrist.x - hip.x
            val dy = wrist.y - hip.y
            kotlin.math.sqrt((dx * dx) + (dy * dy))
        } else {
            0f
        }
        return PoseSamplingSignal(
            centerX = centerX,
            centerY = centerY,
            boxSize = boxSize,
            confidence = frame.confidence,
            wristHipDistance = wristHip,
        )
    }

    fun movementDelta(previous: PoseSamplingSignal?, current: PoseSamplingSignal?): Pair<Double, Double> {
        if (previous == null || current == null) return 0.0 to 0.0
        val centerDelta = abs(current.centerX - previous.centerX) + abs(current.centerY - previous.centerY)
        val boxDelta = abs(current.boxSize - previous.boxSize)
        val subjectMovement = (centerDelta + boxDelta).toDouble()
        val jointMovement = abs(current.wristHipDistance - previous.wristHipDistance).toDouble()
        return subjectMovement to jointMovement
    }
}
