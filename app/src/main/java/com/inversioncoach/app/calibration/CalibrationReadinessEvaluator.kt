package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.PoseFrame

class CalibrationReadinessEvaluator {
    data class ReadinessResult(
        val usable: Boolean,
        val visibleJointCount: Int,
        val missingRequiredJoints: List<String>,
    )

    fun isFrameUsable(step: CalibrationStep, frame: PoseFrame): Boolean {
        return evaluate(step, frame).usable
    }

    fun evaluate(step: CalibrationStep, frame: PoseFrame): ReadinessResult {
        val visible = frame.joints.count { it.visibility >= 0.5f }
        if (visible < 8) {
            return ReadinessResult(
                usable = false,
                visibleJointCount = visible,
                missingRequiredJoints = requiredJointNames(step),
            )
        }

        val visibleByName = frame.joints.filter { it.visibility >= 0.5f }.associateBy { it.name }
        val missing = when (step) {
            CalibrationStep.SIDE_NEUTRAL -> {
                val leftMissing = listOf("left_shoulder", "left_hip", "left_knee", "left_ankle").filterNot(visibleByName::containsKey)
                val rightMissing = listOf("right_shoulder", "right_hip", "right_knee", "right_ankle").filterNot(visibleByName::containsKey)
                if (leftMissing.isEmpty() || rightMissing.isEmpty()) emptyList() else (leftMissing + rightMissing).distinct()
            }

            else -> requiredJointNames(step).filterNot(visibleByName::containsKey)
        }

        return ReadinessResult(
            usable = missing.isEmpty(),
            visibleJointCount = visible,
            missingRequiredJoints = missing,
        )
    }

    fun requiredJointNames(step: CalibrationStep): List<String> {
        return when (step) {
            CalibrationStep.FRONT_NEUTRAL -> listOf(
                "left_shoulder",
                "right_shoulder",
                "left_hip",
                "right_hip",
            )

            CalibrationStep.SIDE_NEUTRAL -> listOf("left_shoulder", "left_hip", "left_knee", "left_ankle", "right_shoulder", "right_hip", "right_knee", "right_ankle")

            CalibrationStep.ARMS_OVERHEAD -> listOf(
                "left_shoulder",
                "right_shoulder",
                "left_elbow",
                "right_elbow",
                "left_wrist",
                "right_wrist",
            )

            CalibrationStep.CONTROLLED_HOLD -> listOf(
                "left_wrist",
                "right_wrist",
                "left_shoulder",
                "right_shoulder",
                "left_hip",
                "right_hip",
            )
        }
    }
}
