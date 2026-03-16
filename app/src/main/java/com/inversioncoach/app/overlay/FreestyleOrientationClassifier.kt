package com.inversioncoach.app.overlay

import com.inversioncoach.app.model.JointPoint
import kotlin.math.abs

class FreestyleOrientationClassifier {
    fun classify(joints: List<JointPoint>): FreestyleViewMode {
        val lookup = joints.associateBy { it.name }
        val shoulderSeparation = horizontalGap(lookup, "left_shoulder", "right_shoulder")
        val hipSeparation = horizontalGap(lookup, "left_hip", "right_hip")
        val bilateralSpread = maxOf(shoulderSeparation, hipSeparation)

        if (bilateralSpread >= 0.16f) {
            return FreestyleViewMode.BILATERAL_VIEW
        }

        val leftVisibility = sideVisibilityScore(lookup, "left")
        val rightVisibility = sideVisibilityScore(lookup, "right")
        return if (leftVisibility >= rightVisibility) {
            FreestyleViewMode.LEFT_SIDE_VIEW
        } else {
            FreestyleViewMode.RIGHT_SIDE_VIEW
        }
    }

    private fun horizontalGap(lookup: Map<String, JointPoint>, left: String, right: String): Float {
        val l = lookup[left] ?: return 0f
        val r = lookup[right] ?: return 0f
        if (l.visibility < 0.35f || r.visibility < 0.35f) return 0f
        return abs(l.x - r.x)
    }

    private fun sideVisibilityScore(lookup: Map<String, JointPoint>, prefix: String): Float =
        listOf("shoulder", "elbow", "wrist", "hip", "knee", "ankle")
            .sumOf { (lookup["${prefix}_$it"]?.visibility ?: 0f).toDouble() }
            .toFloat()
}
