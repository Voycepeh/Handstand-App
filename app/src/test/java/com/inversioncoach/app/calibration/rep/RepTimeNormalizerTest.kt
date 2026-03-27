package com.inversioncoach.app.calibration.rep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepTimeNormalizerTest {
    @Test
    fun normalize_returnsConfiguredOutputLength() {
        val normalizer = RepTimeNormalizer(outputLength = 5)

        val normalized = normalizer.normalize(listOf(0f, 10f, 20f))

        assertEquals(5, normalized.size)
    }

    @Test
    fun normalize_preservesMonotonicInterpolation() {
        val normalizer = RepTimeNormalizer(outputLength = 6)

        val normalized = normalizer.normalize(listOf(0f, 10f, 20f))

        assertTrue(normalized.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0f, normalized.first())
        assertEquals(20f, normalized.last())
    }

    @Test
    fun normalize_singleValueRepeatsAcrossOutput() {
        val normalizer = RepTimeNormalizer(outputLength = 4)

        val normalized = normalizer.normalize(listOf(7f))

        assertEquals(listOf(7f, 7f, 7f, 7f), normalized)
    }
}
