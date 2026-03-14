package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.DrillType
import kotlin.math.abs

class ScoreEngine {
    private val weights = DrillConfigs.all.associate { cfg ->
        cfg.type to cfg.metrics.associate { it.key to it.weight }
    }

    fun score(drill: DrillType, subScores: Map<String, Int>): DrillScoreBreakdown {
        val w = weights[drill] ?: emptyMap()
        val overall = if (w.isEmpty()) 0 else (w.entries.sumOf { (k, wt) -> (subScores[k] ?: 50) * wt } / 100).coerceIn(0, 100)
        val strongest = subScores.maxByOrNull { it.value }?.key ?: "consistency"
        val limiter = subScores.minByOrNull { it.value }?.key ?: "consistency"
        return DrillScoreBreakdown(overall, subScores, strongest, limiter)
    }

    fun consistencyScore(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val avg = values.average().toFloat()
        val mad = values.map { abs(it - avg) }.average().toFloat()
        return (100 - mad * 2).toInt().coerceIn(0, 100)
    }
}
