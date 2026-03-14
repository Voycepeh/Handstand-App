package com.inversioncoach.app.motion

import kotlin.math.max

class TemporalPoseSmoother(
    private val baseAlpha: Float = 0.45f,
    private val minConfidence: Float = 0.20f,
) {
    private var previous: SmoothedPoseFrame? = null

    fun smooth(frame: PoseFrame): SmoothedPoseFrame {
        val old = previous
        if (old == null) {
            val seed = SmoothedPoseFrame(
                timestampMs = frame.timestampMs,
                filteredLandmarks = frame.landmarks,
                velocityByLandmark = frame.landmarks.mapValues { Landmark2D(0f, 0f) },
            )
            previous = seed
            return seed
        }

        val dtSec = max((frame.timestampMs - old.timestampMs).coerceAtLeast(1L).toFloat() / 1000f, 0.001f)
        val filtered = mutableMapOf<JointId, Landmark2D>()
        val velocity = mutableMapOf<JointId, Landmark2D>()

        JointId.entries.forEach { joint ->
            val current = frame.landmarks[joint]
            val prevFiltered = old.filteredLandmarks[joint]

            when {
                current != null && prevFiltered != null -> {
                    val confidence = (frame.confidenceByLandmark[joint] ?: 1f).coerceIn(0f, 1f)
                    val alpha = (baseAlpha * confidence).coerceIn(0.12f, 0.85f)
                    val x = alpha * current.x + (1f - alpha) * prevFiltered.x
                    val y = alpha * current.y + (1f - alpha) * prevFiltered.y
                    filtered[joint] = Landmark2D(x, y)
                    velocity[joint] = Landmark2D((x - prevFiltered.x) / dtSec, (y - prevFiltered.y) / dtSec)
                }

                current != null -> {
                    filtered[joint] = current
                    velocity[joint] = Landmark2D(0f, 0f)
                }

                prevFiltered != null && (frame.confidenceByLandmark[joint] ?: 0f) < minConfidence -> {
                    filtered[joint] = prevFiltered
                    velocity[joint] = Landmark2D(0f, 0f)
                }
            }
        }

        return SmoothedPoseFrame(frame.timestampMs, filtered, velocity).also { previous = it }
    }

    fun reset() {
        previous = null
    }
}
