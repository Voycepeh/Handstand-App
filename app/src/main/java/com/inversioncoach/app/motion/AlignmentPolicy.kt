package com.inversioncoach.app.motion

import com.inversioncoach.app.model.AlignmentStrictness

data class AlignmentTolerancePolicy(
    val lineDeviationNormMax: Float,
)

object AlignmentPolicy {
    fun forStrictness(strictness: AlignmentStrictness): AlignmentTolerancePolicy = when (strictness) {
        AlignmentStrictness.BEGINNER -> AlignmentTolerancePolicy(lineDeviationNormMax = 0.20f)
        AlignmentStrictness.STANDARD -> AlignmentTolerancePolicy(lineDeviationNormMax = 0.14f)
        AlignmentStrictness.ADVANCED -> AlignmentTolerancePolicy(lineDeviationNormMax = 0.10f)
        AlignmentStrictness.CUSTOM -> AlignmentTolerancePolicy(lineDeviationNormMax = 0.14f)
    }
}
