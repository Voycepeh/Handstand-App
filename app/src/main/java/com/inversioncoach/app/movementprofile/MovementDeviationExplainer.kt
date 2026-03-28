package com.inversioncoach.app.movementprofile

class MovementDeviationExplainer {
    fun topDifferences(deviations: List<ComparisonDeviation>, limit: Int = 3): List<String> =
        deviations
            .sortedByDescending { it.severity }
            .take(limit)
            .map { it.description }
}
