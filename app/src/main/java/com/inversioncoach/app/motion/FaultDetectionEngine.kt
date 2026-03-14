package com.inversioncoach.app.motion

class FaultDetectionEngine(
    private val persistenceFrames: Int = 5,
) {
    private val counters = mutableMapOf<String, Int>()

    fun detect(angleFrame: AngleFrame, movementState: MovementState): List<FaultEvent> {
        val faults = mutableListOf<FaultEvent>()

        maybeFault(
            key = "elbows_flare",
            condition = (angleFrame.anglesDeg["left_shoulder_opening"] ?: 180f) < 55f ||
                (angleFrame.anglesDeg["right_shoulder_opening"] ?: 180f) < 55f,
            faults = faults,
            message = "Keep elbows closer to your line",
            severity = FaultSeverity.MEDIUM,
            side = BodySide.BOTH,
            ts = angleFrame.timestampMs,
        )

        maybeFault(
            key = "head_forward",
            condition = angleFrame.trunkLeanDeg > ThresholdTuningStore.trunkLeanMaxDeg,
            faults = faults,
            message = "Stack head with torso",
            severity = FaultSeverity.MEDIUM,
            side = BodySide.NONE,
            ts = angleFrame.timestampMs,
        )

        maybeFault(
            key = "line_loss",
            condition = angleFrame.lineDeviationNorm > ThresholdTuningStore.lineDeviationMaxNorm,
            faults = faults,
            message = "Maintain hollow-body line",
            severity = FaultSeverity.HIGH,
            side = BodySide.BOTH,
            ts = angleFrame.timestampMs,
        )

        if (movementState.currentPhase == MovementPhase.BOTTOM) {
            maybeFault(
                key = "incomplete_rom",
                condition = (angleFrame.anglesDeg["left_elbow_flexion"] ?: 180f) > ThresholdTuningStore.elbowBottomThresholdDeg,
                faults = faults,
                message = "Go deeper for full range",
                severity = FaultSeverity.MEDIUM,
                side = BodySide.LEFT,
                ts = angleFrame.timestampMs,
            )
        }

        return faults
    }

    private fun maybeFault(
        key: String,
        condition: Boolean,
        faults: MutableList<FaultEvent>,
        message: String,
        severity: FaultSeverity,
        side: BodySide,
        ts: Long,
    ) {
        val count = if (condition) (counters[key] ?: 0) + 1 else 0
        counters[key] = count
        if (count >= persistenceFrames) {
            faults += FaultEvent(
                code = key,
                severity = severity,
                message = message,
                side = side,
                startTimestampMs = ts,
            )
        }
    }
}
