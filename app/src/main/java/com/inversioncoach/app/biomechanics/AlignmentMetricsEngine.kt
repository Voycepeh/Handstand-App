package com.inversioncoach.app.biomechanics

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import com.inversioncoach.app.model.AlignmentMetric
import com.inversioncoach.app.model.AngleDebugMetric
import com.inversioncoach.app.model.DrillScore
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame

class AlignmentMetricsEngine {

    data class AnalysisResult(
        val metrics: List<AlignmentMetric>,
        val score: DrillScore,
        val angles: List<AngleDebugMetric>,
        val fault: String?,
    )

    fun analyze(config: DrillModeConfig, frame: PoseFrame): AnalysisResult {
        val joints = frame.joints.associateBy { it.name }
        val angles = computeAngles(joints)
        val metrics = config.metrics.mapIndexed { idx, weight ->
            val metric = metricFromKey(weight.key, joints, angles, frame.confidence)
            metric.copy(score = max(0, metric.score - idx))
        }
        val score = score(config, metrics)
        val fault = config.faults[score.limitingFactor]
        return AnalysisResult(metrics, score, angles, fault)
    }

    private fun metricFromKey(
        key: String,
        joints: Map<String, JointPoint>,
        angles: List<AngleDebugMetric>,
        fallbackConfidence: Float,
    ): AlignmentMetric {
        val raw = when (key) {
            "line_quality", "line_retention" -> 100f - abs(x("left_shoulder", joints) - x("left_hip", joints)) * 220f
            "shoulder_openness", "shoulder_push" -> angleScore(angles, "shoulder_open_angle", 170f)
            "scapular_elevation" -> 100f - abs(y("left_shoulder", joints) - y("left_ear", joints)) * 180f
            "rib_pelvis_control", "reduced_arch" -> 100f - abs(x("left_rib_proxy", joints) - x("left_hip", joints)) * 240f
            "leg_tension" -> angleScore(angles, "knee_extension_angle", 175f)
            "hip_stack", "hip_height" -> 100f - abs(x("left_hip", joints) - x("left_wrist", joints)) * 220f
            "wall_reliance" -> 100f - abs(x("left_ankle", joints) - x("left_hip", joints)) * 180f
            "shoulder_loading", "loading_angle" -> angleScore(angles, "torso_vertical_angle", 90f)
            "head_path", "pressing_path", "path_consistency" -> 100f - abs(x("nose", joints) - x("left_wrist", joints)) * 260f
            "elbow_path" -> angleScore(angles, "elbow_extension_angle", 160f)
            "tempo_control", "descent_control" -> fallbackConfidence * 100f
            "depth", "bottom_position" -> 100f - abs(y("nose", joints) - y("left_wrist", joints)) * 250f
            "lockout", "top_position" -> angleScore(angles, "elbow_extension_angle", 168f)
            else -> fallbackConfidence * 100f
        }
        val safe = raw.coerceIn(0f, 100f)
        return AlignmentMetric(key = key, value = safe / 100f, target = 0.85f, score = safe.toInt())
    }

    fun score(config: DrillModeConfig, metrics: List<AlignmentMetric>): DrillScore {
        val weighted = config.metrics.sumOf { metricWeight ->
            val metricScore = metrics.firstOrNull { it.key == metricWeight.key }?.score ?: 50
            metricScore * metricWeight.weight
        }
        val overall = (weighted / 100).coerceIn(0, 100)
        val sorted = metrics.sortedByDescending { it.score }
        return DrillScore(
            overall = overall,
            subScores = metrics.associate { it.key to it.score },
            strongestArea = sorted.firstOrNull()?.key ?: "consistency",
            limitingFactor = sorted.lastOrNull()?.key ?: "consistency",
        )
    }

    private fun computeAngles(joints: Map<String, JointPoint>): List<AngleDebugMetric> {
        val shoulderOpen = angle(
            joints["left_elbow"],
            joints["left_shoulder"],
            joints["left_hip"],
        )
        val kneeExt = angle(joints["left_hip"], joints["left_knee"], joints["left_ankle"])
        val elbowExt = angle(joints["left_shoulder"], joints["left_elbow"], joints["left_wrist"])
        val torsoVertical = lineAngleToHorizontal(joints["left_shoulder"], joints["left_hip"])
        return listOf(
            AngleDebugMetric("shoulder_open_angle", shoulderOpen),
            AngleDebugMetric("knee_extension_angle", kneeExt),
            AngleDebugMetric("elbow_extension_angle", elbowExt),
            AngleDebugMetric("torso_vertical_angle", torsoVertical),
        )
    }

    private fun angleScore(angles: List<AngleDebugMetric>, key: String, target: Float): Float {
        val degrees = angles.firstOrNull { it.key == key }?.degrees ?: return 50f
        return (100f - abs(degrees - target) * 1.6f).coerceIn(0f, 100f)
    }

    private fun x(key: String, joints: Map<String, JointPoint>): Float = joints[key]?.x ?: 0.5f
    private fun y(key: String, joints: Map<String, JointPoint>): Float = joints[key]?.y ?: 0.5f

    private fun angle(a: JointPoint?, b: JointPoint?, c: JointPoint?): Float {
        if (a == null || b == null || c == null) return 90f
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val dot = abx * cbx + aby * cby
        val magAb = kotlin.math.sqrt(abx * abx + aby * aby)
        val magCb = kotlin.math.sqrt(cbx * cbx + cby * cby)
        if (magAb == 0f || magCb == 0f) return 90f
        val cosine = (dot / (magAb * magCb)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cosine).toDouble()).toFloat()
    }

    private fun lineAngleToHorizontal(a: JointPoint?, b: JointPoint?): Float {
        if (a == null || b == null) return 90f
        val radians = atan2((a.y - b.y), (a.x - b.x))
        val deg = abs(Math.toDegrees(radians.toDouble()).toFloat())
        return min(180f, max(0f, deg))
    }
}
