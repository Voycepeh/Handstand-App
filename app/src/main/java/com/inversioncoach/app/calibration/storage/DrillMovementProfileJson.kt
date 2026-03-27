package com.inversioncoach.app.calibration.storage

import com.inversioncoach.app.calibration.DrillMovementProfile
import com.inversioncoach.app.calibration.HoldMetricTemplate
import com.inversioncoach.app.calibration.HoldTemplate
import com.inversioncoach.app.calibration.HoldTemplateSource
import com.inversioncoach.app.calibration.RepTemplate
import com.inversioncoach.app.calibration.RepTemplateSource
import com.inversioncoach.app.calibration.TemporalMetricProfile
import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.model.DrillType
import org.json.JSONArray
import org.json.JSONObject

class DrillMovementProfileJson {
    fun encode(profile: DrillMovementProfile): String = JSONObject().apply {
        put("drillType", profile.drillType.name)
        put("profileVersion", profile.profileVersion)
        put("createdAtMs", profile.createdAtMs)
        put("updatedAtMs", profile.updatedAtMs)
        put("userBodyProfile", profile.userBodyProfile?.let { bodyProfileJson(it) })
        put("holdTemplate", profile.holdTemplate?.let { holdTemplateJson(it) })
        put("repTemplate", profile.repTemplate?.let { repTemplateJson(it) })
    }.toString()

    fun decode(raw: String): DrillMovementProfile {
        val obj = JSONObject(raw)
        val drillType = DrillType.valueOf(obj.getString("drillType"))
        return DrillMovementProfile(
            drillType = drillType,
            profileVersion = obj.optInt("profileVersion", 1),
            userBodyProfile = obj.optJSONObject("userBodyProfile")?.let(::decodeUserBodyProfile),
            holdTemplate = obj.optJSONObject("holdTemplate")?.let { decodeHoldTemplate(it, drillType) },
            repTemplate = obj.optJSONObject("repTemplate")?.let { decodeRepTemplate(it, drillType) },
            createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
            updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis()),
        )
    }

    private fun bodyProfileJson(body: UserBodyProfile): JSONObject = JSONObject().apply {
        put("version", body.version)
        put("shoulderWidthNormalized", body.shoulderWidthNormalized)
        put("hipWidthNormalized", body.hipWidthNormalized)
        put("torsoLengthNormalized", body.torsoLengthNormalized)
        put("upperArmLengthNormalized", body.upperArmLengthNormalized)
        put("forearmLengthNormalized", body.forearmLengthNormalized)
        put("femurLengthNormalized", body.femurLengthNormalized)
        put("shinLengthNormalized", body.shinLengthNormalized)
        put("leftRightConsistency", body.leftRightConsistency)
    }

    private fun holdTemplateJson(hold: HoldTemplate): JSONObject = JSONObject().apply {
        put("drillType", hold.drillType.name)
        put("profileVersion", hold.profileVersion)
        put("minStableDurationMs", hold.minStableDurationMs)
        put("source", hold.source.name)
        put("metrics", JSONArray().apply {
            hold.metrics.forEach { metric ->
                put(
                    JSONObject().apply {
                        put("metricKey", metric.metricKey)
                        put("targetValue", metric.targetValue)
                        put("goodTolerance", metric.goodTolerance)
                        put("warnTolerance", metric.warnTolerance)
                        put("weight", metric.weight)
                    },
                )
            }
        })
    }

    private fun repTemplateJson(rep: RepTemplate): JSONObject = JSONObject().apply {
        put("drillType", rep.drillType.name)
        put("profileVersion", rep.profileVersion)
        put("expectedRepFrames", rep.expectedRepFrames)
        put("minRomThreshold", rep.minRomThreshold)
        put("source", rep.source.name)
        put("temporalMetrics", JSONArray().apply {
            rep.temporalMetrics.forEach { metric ->
                put(
                    JSONObject().apply {
                        put("metricKey", metric.metricKey)
                        put("meanSeries", JSONArray(metric.meanSeries))
                        put("toleranceSeries", JSONArray(metric.toleranceSeries))
                    },
                )
            }
        })
    }

    private fun decodeUserBodyProfile(obj: JSONObject): UserBodyProfile = UserBodyProfile(
        version = obj.optInt("version", 1),
        shoulderWidthNormalized = obj.optDouble("shoulderWidthNormalized", 0.0).toFloat(),
        hipWidthNormalized = obj.optDouble("hipWidthNormalized", 0.0).toFloat(),
        torsoLengthNormalized = obj.optDouble("torsoLengthNormalized", 0.0).toFloat(),
        upperArmLengthNormalized = obj.optDouble("upperArmLengthNormalized", 0.0).toFloat(),
        forearmLengthNormalized = obj.optDouble("forearmLengthNormalized", 0.0).toFloat(),
        femurLengthNormalized = obj.optDouble("femurLengthNormalized", 0.0).toFloat(),
        shinLengthNormalized = obj.optDouble("shinLengthNormalized", 0.0).toFloat(),
        leftRightConsistency = obj.optDouble("leftRightConsistency", 0.0).toFloat(),
    )

    private fun decodeHoldTemplate(obj: JSONObject, fallbackDrillType: DrillType): HoldTemplate {
        val metrics = obj.optJSONArray("metrics")?.toMetricList().orEmpty()
        return HoldTemplate(
            drillType = obj.optString("drillType").takeIf { it.isNotBlank() }?.let(DrillType::valueOf) ?: fallbackDrillType,
            profileVersion = obj.optInt("profileVersion", 1),
            metrics = if (metrics.isNotEmpty()) metrics else legacyMetricsFrom(obj),
            minStableDurationMs = obj.optLong("minStableDurationMs", 2000L),
            source = obj.optString("source").takeIf { it.isNotBlank() }
                ?.let { runCatching { HoldTemplateSource.valueOf(it) }.getOrNull() }
                ?: HoldTemplateSource.DEFAULT_BASELINE,
        )
    }

    private fun legacyMetricsFrom(obj: JSONObject): List<HoldMetricTemplate> {
        val alignmentTarget = obj.optInt("alignmentTarget", 80).coerceIn(0, 100) / 100f
        val stabilityTarget = obj.optInt("stabilityTarget", 80).coerceIn(0, 100) / 100f
        return listOf(
            HoldMetricTemplate("torso_line_deviation", 1f - alignmentTarget, 0.05f, 0.10f, 0.8f),
            HoldMetricTemplate("left_right_symmetry", stabilityTarget, 0.10f, 0.18f, 0.6f),
        )
    }

    private fun JSONArray.toMetricList(): List<HoldMetricTemplate> = buildList {
        for (index in 0 until length()) {
            val metric = optJSONObject(index) ?: continue
            val key = metric.optString("metricKey").takeIf { it.isNotBlank() } ?: continue
            add(
                HoldMetricTemplate(
                    metricKey = key,
                    targetValue = metric.optDouble("targetValue", 0.0).toFloat(),
                    goodTolerance = metric.optDouble("goodTolerance", 0.05).toFloat(),
                    warnTolerance = metric.optDouble("warnTolerance", 0.1).toFloat(),
                    weight = metric.optDouble("weight", 0.5).toFloat(),
                ),
            )
        }
    }
    private fun decodeRepTemplate(obj: JSONObject, fallbackDrillType: DrillType): RepTemplate {
        val temporalMetrics = obj.optJSONArray("temporalMetrics")?.toTemporalMetricProfiles().orEmpty()
        return RepTemplate(
            drillType = obj.optString("drillType").takeIf { it.isNotBlank() }?.let(DrillType::valueOf) ?: fallbackDrillType,
            profileVersion = obj.optInt("profileVersion", 1),
            temporalMetrics = temporalMetrics,
            expectedRepFrames = obj.optInt("expectedRepFrames", 1).coerceAtLeast(1),
            minRomThreshold = if (obj.has("minRomThreshold") && !obj.isNull("minRomThreshold")) obj.optDouble("minRomThreshold").toFloat() else null,
            source = obj.optString("source").takeIf { it.isNotBlank() }
                ?.let { raw -> RepTemplateSource.entries.firstOrNull { it.name == raw } }
                ?: RepTemplateSource.DEFAULT_BASELINE,
        )
    }

    private fun JSONArray.toTemporalMetricProfiles(): List<TemporalMetricProfile> {
        return List(length()) { index ->
            val metric = optJSONObject(index) ?: JSONObject()
            TemporalMetricProfile(
                metricKey = metric.optString("metricKey"),
                meanSeries = metric.optJSONArray("meanSeries")?.toFloatList().orEmpty(),
                toleranceSeries = metric.optJSONArray("toleranceSeries")?.toFloatList().orEmpty(),
            )
        }.filter { it.metricKey.isNotBlank() }
    }

    private fun JSONArray.toFloatList(): List<Float> = List(length()) { index -> optDouble(index, 0.0).toFloat() }
}
