package com.inversioncoach.app.calibration.rep

class RepTimeNormalizer(
    private val outputLength: Int = 50,
) {
    fun normalize(values: List<Float>): List<Float> {
        if (values.isEmpty()) return List(outputLength) { 0f }
        if (values.size == 1) return List(outputLength) { values.first() }

        return List(outputLength) { i ->
            val pos = i.toFloat() * (values.lastIndex.toFloat() / (outputLength - 1).coerceAtLeast(1))
            val left = pos.toInt()
            val right = (left + 1).coerceAtMost(values.lastIndex)
            val frac = pos - left
            values[left] * (1f - frac) + values[right] * frac
        }
    }
}
