package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.AlignmentMetric
import com.inversioncoach.app.model.AngleDebugMetric
import com.inversioncoach.app.model.DrillScore
import com.inversioncoach.app.model.PoseFrame

class AlignmentMetricsEngine {

    data class AnalysisResult(
        val metrics: List<AlignmentMetric>,
        val score: DrillScore,
        val angles: List<AngleDebugMetric>,
        val fault: String?,
    )

    private val analyzers = mutableMapOf<com.inversioncoach.app.model.DrillType, DrillAnalyzer>()

    fun analyze(config: DrillModeConfig, frame: PoseFrame): AnalysisResult {
        val analyzer = analyzers.getOrPut(config.type) { analyzerFor(config.type) }
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

    private fun analyzerFor(drillType: com.inversioncoach.app.model.DrillType): DrillAnalyzer = when (drillType) {
        com.inversioncoach.app.model.DrillType.STANDING_POSTURE_HOLD -> StandingPostureAnalyzer()
        com.inversioncoach.app.model.DrillType.PUSH_UP -> PushUpAnalyzer()
        com.inversioncoach.app.model.DrillType.SIT_UP -> SitUpAnalyzer()
        com.inversioncoach.app.model.DrillType.CHEST_TO_WALL_HANDSTAND -> ChestToWallAnalyzer()
        com.inversioncoach.app.model.DrillType.BACK_TO_WALL_HANDSTAND -> BackToWallAnalyzer()
        com.inversioncoach.app.model.DrillType.PIKE_PUSH_UP -> PikePushUpAnalyzer()
        com.inversioncoach.app.model.DrillType.ELEVATED_PIKE_PUSH_UP -> ElevatedPikeAnalyzer()
        com.inversioncoach.app.model.DrillType.NEGATIVE_WALL_HANDSTAND_PUSH_UP -> NegativeHspuAnalyzer()
        else -> ChestToWallAnalyzer()
    }
}
