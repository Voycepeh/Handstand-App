package com.inversioncoach.app.movementprofile

data class ReferenceTemplateDefinition(
    val id: String,
    val templateName: String,
    val drillId: String,
    val description: String,
    val phaseTimingMs: Map<String, Long>,
    val alignmentTargets: Map<String, Float>,
    val stabilityTargets: Map<String, Float>,
    val assetPath: String,
)

data class StoredProfileSnapshot(
    val phaseDurationsMs: Map<String, Long>,
    val featureMeans: Map<String, Float>,
    val stabilityJitter: Map<String, Float>,
)

data class MovementComparisonResult(
    val overallSimilarityScore: Int,
    val timingScore: Int,
    val alignmentScore: Int,
    val stabilityScore: Int,
    val phaseScores: Map<String, Int>,
    val topDifferences: List<String>,
)

data class ComparisonDeviation(
    val key: String,
    val severity: Float,
    val description: String,
)
