package com.inversioncoach.app.motion

data class PhaseThresholds(
    val downStartDeg: Float,
    val bottomDeg: Float,
    val upStartDeg: Float,
    val topDeg: Float,
    val minDwellMs: Long = 100,
)

class MovementPhaseDetector(
    private val thresholds: PhaseThresholds,
    private val trackedAngle: String,
) {
    private var phase: MovementPhase = MovementPhase.SETUP
    private var phaseSince = 0L
    private var repCount = 0

    fun update(frame: AngleFrame): MovementState {
        if (phaseSince == 0L) phaseSince = frame.timestampMs
        val angle = frame.anglesDeg[trackedAngle] ?: 0f
        val canSwitch = frame.timestampMs - phaseSince >= thresholds.minDwellMs

        when (phase) {
            MovementPhase.SETUP -> if (angle <= thresholds.downStartDeg && canSwitch) moveTo(MovementPhase.ECCENTRIC, frame)
            MovementPhase.ECCENTRIC -> {
                if (angle <= thresholds.bottomDeg && canSwitch) moveTo(MovementPhase.BOTTOM, frame)
                else if (angle >= thresholds.topDeg && canSwitch) moveTo(MovementPhase.SETUP, frame)
            }
            MovementPhase.BOTTOM -> if (angle >= thresholds.upStartDeg && canSwitch) moveTo(MovementPhase.CONCENTRIC, frame)
            MovementPhase.CONCENTRIC -> {
                if (angle >= thresholds.topDeg && canSwitch) {
                    moveTo(MovementPhase.TOP, frame)
                    repCount += 1
                }
            }
            MovementPhase.TOP -> if (canSwitch) moveTo(MovementPhase.RESET, frame)
            MovementPhase.RESET -> if (canSwitch) moveTo(MovementPhase.SETUP, frame)
            MovementPhase.HOLD -> Unit
        }

        val progress = when (phase) {
            MovementPhase.SETUP -> 0f
            MovementPhase.ECCENTRIC -> 0.25f
            MovementPhase.BOTTOM -> 0.5f
            MovementPhase.CONCENTRIC -> 0.75f
            MovementPhase.TOP, MovementPhase.RESET -> 1f
            MovementPhase.HOLD -> 1f
        }

        return MovementState(
            currentPhase = phase,
            repProgress = progress,
            confidence = 0.8f,
            startedAt = phaseSince,
            completedRepCount = repCount,
        )
    }

    fun reset() {
        phase = MovementPhase.SETUP
        phaseSince = 0L
        repCount = 0
    }

    private fun moveTo(target: MovementPhase, frame: AngleFrame) {
        phase = target
        phaseSince = frame.timestampMs
    }
}
