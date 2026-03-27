package com.inversioncoach.app.calibration.hold

import com.inversioncoach.app.model.PoseFrame
import kotlin.math.abs

class HoldWindowSelector(
    private val minimumWindowFrames: Int = 15,
) {
    fun select(frames: List<PoseFrame>): List<List<PoseFrame>> {
        if (frames.isEmpty()) return emptyList()

        val windows = mutableListOf<List<PoseFrame>>()
        val current = mutableListOf<PoseFrame>()

        var previous: PoseFrame? = null
        for (frame in frames) {
            val stable = isStable(previous, frame)
            if (stable) {
                current += frame
            } else {
                if (current.size >= minimumWindowFrames) windows += current.toList()
                current.clear()
            }
            previous = frame
        }

        if (current.size >= minimumWindowFrames) windows += current.toList()
        return windows
    }

    private fun isStable(previous: PoseFrame?, current: PoseFrame): Boolean {
        if (previous == null) return true
        val prevNose = previous.joints.firstOrNull { it.name == "nose" }
        val curNose = current.joints.firstOrNull { it.name == "nose" }
        if (prevNose == null || curNose == null) return true
        val movement = abs(prevNose.x - curNose.x) + abs(prevNose.y - curNose.y)
        return movement < 0.04f
    }
}
