package com.inversioncoach.app.calibration.hold

import com.inversioncoach.app.calibration.HoldMetricTemplate
import com.inversioncoach.app.calibration.HoldTemplate
import com.inversioncoach.app.calibration.HoldTemplateSource
import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.PoseFrame

class HoldTemplateBuilder(
    private val extractor: HoldMetricExtractor = HoldMetricExtractor(),
    private val windowSelector: HoldWindowSelector = HoldWindowSelector(),
) {
    fun build(
        drillType: DrillType,
        profileVersion: Int,
        bodyProfile: UserBodyProfile?,
        frames: List<PoseFrame>,
    ): HoldTemplate? {
        val bestWindow = windowSelector.select(frames).maxByOrNull { it.size } ?: return null
        val snapshots = bestWindow.map { extractor.extract(it, bodyProfile) }

        val metricKeys = snapshots.flatMap { it.values.keys }.distinct()
        val metrics = metricKeys.mapNotNull { key ->
            val values = snapshots.mapNotNull { it.values[key] }
            if (values.isEmpty()) return@mapNotNull null
            HoldMetricTemplate(
                metricKey = key,
                targetValue = values.average().toFloat(),
                goodTolerance = toleranceFor(key),
                warnTolerance = warnToleranceFor(key),
                weight = weightFor(key),
            )
        }

        return HoldTemplate(
            drillType = drillType,
            profileVersion = profileVersion,
            metrics = metrics,
            minStableDurationMs = 2000L,
            source = HoldTemplateSource.STABLE_HOLD_CAPTURE,
        )
    }

    private fun toleranceFor(key: String): Float = when (key) {
        "left_right_symmetry" -> 0.08f
        else -> 0.04f
    }

    private fun warnToleranceFor(key: String): Float = when (key) {
        "left_right_symmetry" -> 0.15f
        else -> 0.08f
    }

    private fun weightFor(key: String): Float = when (key) {
        "wrist_shoulder_offset" -> 1.0f
        "shoulder_hip_offset" -> 1.0f
        "hip_ankle_offset" -> 0.9f
        "torso_line_deviation" -> 0.8f
        "left_right_symmetry" -> 0.6f
        "stability_score" -> 0.5f
        else -> 0.5f
    }
}
