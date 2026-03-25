package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.drills.core.DrillRegistry
import com.inversioncoach.app.model.AlignmentMetric
import com.inversioncoach.app.model.AngleDebugMetric
import com.inversioncoach.app.model.DrillScore
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.PoseFrame

class AlignmentMetricsEngine(
    private val drillRegistry: DrillRegistry = DrillRegistry(),
) {

    data class AnalysisResult(
        val metrics: List<AlignmentMetric>,
        val score: DrillScore,
        val angles: List<AngleDebugMetric>,
        val fault: String?,
    )

    fun analyze(config: DrillModeConfig, frame: PoseFrame): AnalysisResult {
        val analyzer = drillRegistry.analyzerFor(config)
        val result = analyzer.analyzeFrame(frame) ?: return AnalysisResult(
            metrics = emptyList(),
            score = DrillScore(
                overall = 0,
                subScores = emptyMap(),
                strongestArea = "n/a",
                limitingFactor = "insufficient data",
            ),
            angles = emptyList(),
            fault = null,
        )
        val metrics = result.score.subScores.map { (key, score) ->
            AlignmentMetric(key = key, value = score / 100f, target = 0.85f, score = score)
        }
        val sortedIssues = result.issues.sortedByDescending {
            when (it.severity) {
                IssueSeverity.MAJOR -> 3
                IssueSeverity.MODERATE -> 2
                IssueSeverity.MINOR -> 1
            }
        }
        return AnalysisResult(
            metrics = metrics,
            score = DrillScore(
                overall = result.score.overall,
                subScores = result.score.subScores,
                strongestArea = result.score.strongestArea,
                limitingFactor = result.score.mainLimiter,
            ),
            angles = result.metrics.jointAngles.map { AngleDebugMetric(it.key, it.value) },
            fault = sortedIssues.firstOrNull()?.type?.name?.lowercase()?.replace('_', ' '),
        )
    }

    fun requireSupported(drillType: DrillType): DrillModeConfig = DrillConfigs.requireByType(drillType)
}
