package com.inversioncoach.app.pose

import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import kotlin.math.abs
import kotlin.math.hypot

data class PoseCorrectionResult(
    val frame: PoseFrame,
    val unreliableJointNames: Set<String>,
    val inversionDetected: Boolean,
)

class PoseValidationAndCorrectionEngine(
    private val hipCollapseRatioThreshold: Float = 0.45f,
    private val discontinuityThreshold: Float = 0.22f,
) {
    private var previousValidJoints: Map<String, JointPoint> = emptyMap()

    fun reset() {
        previousValidJoints = emptyMap()
    }

    fun process(frame: PoseFrame, profile: UserBodyProfile?): PoseCorrectionResult {
        val byName = frame.joints.associateBy { it.name }
        val inversionDetected = detectInversion(byName)
        val unreliable = mutableSetOf<String>()

        if (inversionDetected) {
            evaluateHipCollapse(byName, profile)?.let { unreliable += it }
        }

        previousValidJoints.forEach { (name, prev) ->
            val current = byName[name] ?: return@forEach
            if (distance(current, prev) > discontinuityThreshold) {
                unreliable += name
            }
        }

        val corrected = frame.joints.map { joint ->
            if (joint.name !in unreliable) {
                previousValidJoints = previousValidJoints + (joint.name to joint)
                return@map joint
            }
            reconstruct(joint.name, byName, profile) ?: joint.copy(visibility = joint.visibility * 0.35f)
        }

        return PoseCorrectionResult(
            frame = frame.copy(joints = corrected),
            unreliableJointNames = unreliable,
            inversionDetected = inversionDetected,
        )
    }

    private fun evaluateHipCollapse(byName: Map<String, JointPoint>, profile: UserBodyProfile?): Set<String>? {
        val expectedFemur = profile?.femurLengthNormalized?.takeIf { it > 0f } ?: estimateFemur(byName)
        if (expectedFemur <= 0f) return null
        val unreliable = mutableSetOf<String>()
        checkHip("left", byName, expectedFemur)?.let { unreliable += it }
        checkHip("right", byName, expectedFemur)?.let { unreliable += it }
        return unreliable
    }

    private fun checkHip(side: String, byName: Map<String, JointPoint>, expectedFemur: Float): String? {
        val hip = byName["${side}_hip"] ?: return null
        val knee = byName["${side}_knee"] ?: return null
        val ratio = distance(hip, knee) / expectedFemur
        return if (ratio < hipCollapseRatioThreshold) "${side}_hip" else null
    }

    private fun reconstruct(name: String, byName: Map<String, JointPoint>, profile: UserBodyProfile?): JointPoint? {
        previousValidJoints[name]?.let { return it.copy(visibility = maxOf(0.3f, it.visibility * 0.75f)) }

        if (name.endsWith("_hip")) {
            val side = name.substringBefore('_')
            val shoulder = byName["${side}_shoulder"]
            val oppositeHip = byName[if (side == "left") "right_hip" else "left_hip"]
            if (shoulder != null && oppositeHip != null) {
                val torsoLength = profile?.torsoLengthNormalized?.takeIf { it > 0f }
                    ?: estimateTorsoLength(byName)
                return JointPoint(
                    name = name,
                    x = (oppositeHip.x + shoulder.x) / 2f,
                    y = shoulder.y + torsoLength,
                    z = shoulder.z,
                    visibility = 0.35f,
                )
            }
        }

        return null
    }

    private fun detectInversion(byName: Map<String, JointPoint>): Boolean {
        val leftShoulder = byName["left_shoulder"]
        val rightShoulder = byName["right_shoulder"]
        val leftHip = byName["left_hip"]
        val rightHip = byName["right_hip"]
        if (leftShoulder == null || rightShoulder == null || leftHip == null || rightHip == null) return false
        val shouldersY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipsY = (leftHip.y + rightHip.y) / 2f
        return hipsY < shouldersY - 0.02f
    }

    private fun estimateFemur(byName: Map<String, JointPoint>): Float {
        val left = byName["left_hip"]?.let { hip -> byName["left_knee"]?.let { knee -> distance(hip, knee) } }
        val right = byName["right_hip"]?.let { hip -> byName["right_knee"]?.let { knee -> distance(hip, knee) } }
        return listOfNotNull(left, right).average().toFloat()
    }

    private fun estimateTorsoLength(byName: Map<String, JointPoint>): Float {
        val left = byName["left_shoulder"]?.let { shoulder -> byName["left_hip"]?.let { hip -> abs(hip.y - shoulder.y) } }
        val right = byName["right_shoulder"]?.let { shoulder -> byName["right_hip"]?.let { hip -> abs(hip.y - shoulder.y) } }
        return listOfNotNull(left, right).average().toFloat().takeIf { it > 0f } ?: 0.2f
    }

    private fun distance(a: JointPoint, b: JointPoint): Float = hypot(a.x - b.x, a.y - b.y)
}
