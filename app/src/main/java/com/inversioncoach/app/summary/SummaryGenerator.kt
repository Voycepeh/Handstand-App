package com.inversioncoach.app.summary

import com.inversioncoach.app.model.DrillScore
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionSummary

class SummaryGenerator(
    private val recommendationEngine: RecommendationEngine,
) {
    fun generate(
        drillType: DrillType,
        score: DrillScore,
        issues: List<String>,
        wins: List<String>,
    ): SessionSummary {
        val recommendation = recommendationEngine.recommend(drillType, score.limitingFactor)
        val headline = "You maintained ${score.strongestArea.lowercase()} best, with breakdowns in ${score.limitingFactor.lowercase()}."
        return SessionSummary(
            headline = headline,
            whatWentWell = wins.take(3).ifEmpty { listOf("Consistency improved across the set.") },
            whatBrokeDown = issues.take(3).ifEmpty { listOf("No major breakdowns detected.") },
            whereItBrokeDown = "Breakdown appeared most during fatigue and transitions.",
            nextFocus = "Prioritize ${score.limitingFactor.lowercase()} while keeping cue volume low.",
            recommendedDrill = recommendation,
            issueTimeline = issues.take(3).mapIndexed { index, item -> "T+${(index + 1) * 15}s: $item" },
        )
    }
}
