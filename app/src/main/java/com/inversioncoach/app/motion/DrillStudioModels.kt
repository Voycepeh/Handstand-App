package com.inversioncoach.app.motion

import com.inversioncoach.app.model.DrillType
import org.json.JSONArray
import org.json.JSONObject

enum class DrillStudioViewMode { SIDE, FRONT }
enum class DrillStudioComparisonMode { OFF, OVERLAY }

data class DrillStudioPhaseWindow(val start: Float, val end: Float)

data class DrillStudioDocument(
    val id: String,
    val seededDrillType: DrillType?,
    val displayName: String,
    val progressWindow: DrillStudioPhaseWindow,
    val metricThresholds: Map<String, Float>,
    val supportedView: DrillStudioViewMode,
    val defaultView: DrillStudioViewMode,
    val comparisonMode: DrillStudioComparisonMode,
    val animationSpec: SkeletonAnimationSpec,
)

object DrillStudioCodec {
    fun fromSeeded(drill: DrillDefinition): DrillStudioDocument = DrillStudioDocument(
        id = drill.id.name,
        seededDrillType = drill.id,
        displayName = drill.displayName,
        progressWindow = DrillStudioPhaseWindow(0f, 1f),
        metricThresholds = mapOf(
            "elbowBottomThresholdDeg" to ThresholdTuningStore.elbowBottomThresholdDeg,
            "trunkLeanMaxDeg" to ThresholdTuningStore.trunkLeanMaxDeg,
            "lineDeviationMaxNorm" to ThresholdTuningStore.lineDeviationMaxNorm,
            "tempoMinSec" to ThresholdTuningStore.tempoMinSec,
        ),
        supportedView = DrillStudioViewMode.SIDE,
        defaultView = DrillStudioViewMode.SIDE,
        comparisonMode = DrillStudioComparisonMode.OVERLAY,
        animationSpec = drill.animationSpec,
    )

    fun toJson(document: DrillStudioDocument): JSONObject = JSONObject().apply {
        put("id", document.id)
        put("seededDrillType", document.seededDrillType?.name)
        put("displayName", document.displayName)
        put("progressWindow", JSONObject().apply {
            put("start", document.progressWindow.start)
            put("end", document.progressWindow.end)
        })
        put("metricThresholds", JSONObject().apply {
            document.metricThresholds.forEach { (key, value) -> put(key, value) }
        })
        put("supportedView", document.supportedView.name)
        put("defaultView", document.defaultView.name)
        put("comparisonMode", document.comparisonMode.name)
        put("animationSpec", animationSpecJson(document.animationSpec))
    }

    fun fromJson(json: JSONObject): DrillStudioDocument {
        val progressWindow = json.optJSONObject("progressWindow") ?: JSONObject()
        val thresholds = json.optJSONObject("metricThresholds") ?: JSONObject()
        return DrillStudioDocument(
            id = json.optString("id"),
            seededDrillType = DrillType.fromStoredName(json.optString("seededDrillType")),
            displayName = json.optString("displayName"),
            progressWindow = DrillStudioPhaseWindow(
                start = progressWindow.optDouble("start", 0.0).toFloat().coerceIn(0f, 1f),
                end = progressWindow.optDouble("end", 1.0).toFloat().coerceIn(0f, 1f),
            ),
            metricThresholds = thresholds.keys().asSequence().associateWith { key -> thresholds.optDouble(key, 0.0).toFloat() },
            supportedView = DrillStudioViewMode.entries.firstOrNull { it.name == json.optString("supportedView") } ?: DrillStudioViewMode.SIDE,
            defaultView = DrillStudioViewMode.entries.firstOrNull { it.name == json.optString("defaultView") } ?: DrillStudioViewMode.SIDE,
            comparisonMode = DrillStudioComparisonMode.entries.firstOrNull { it.name == json.optString("comparisonMode") }
                ?: DrillStudioComparisonMode.OVERLAY,
            animationSpec = animationSpecFromJson(json.getJSONObject("animationSpec")),
        )
    }

    private fun animationSpecJson(spec: SkeletonAnimationSpec): JSONObject = JSONObject().apply {
        put("id", spec.id)
        put("fpsHint", spec.fpsHint)
        put("loop", spec.loop)
        put("mirroredSupported", spec.mirroredSupported)
        put("keyframes", JSONArray().apply {
            spec.keyframes.forEach { frame ->
                put(JSONObject().apply {
                    put("name", frame.name)
                    put("progress", frame.progress)
                    put("easingToNext", frame.easingToNext.name)
                    put("joints", JSONObject().apply {
                        frame.joints.forEach { (joint, point) ->
                            put(joint.name, JSONObject().apply {
                                put("x", point.x)
                                put("y", point.y)
                            })
                        }
                    })
                })
            }
        })
    }

    private fun animationSpecFromJson(json: JSONObject): SkeletonAnimationSpec = SkeletonAnimationSpec(
        id = json.optString("id"),
        fpsHint = json.optInt("fpsHint", 15),
        loop = json.optBoolean("loop", true),
        mirroredSupported = json.optBoolean("mirroredSupported", false),
        keyframes = json.optJSONArray("keyframes")?.toKeyframes().orEmpty(),
    )

    private fun JSONArray.toKeyframes(): List<SkeletonKeyframe> = (0 until length()).mapNotNull { index ->
        val frame = optJSONObject(index) ?: return@mapNotNull null
        val jointsJson = frame.optJSONObject("joints") ?: JSONObject()
        val joints = jointsJson.keys().asSequence().mapNotNull { jointName ->
            val joint = BodyJoint.entries.firstOrNull { it.name == jointName } ?: return@mapNotNull null
            val point = jointsJson.optJSONObject(jointName) ?: return@mapNotNull null
            joint to NormalizedPoint(
                x = point.optDouble("x", 0.5).toFloat(),
                y = point.optDouble("y", 0.5).toFloat(),
            )
        }.toMap()
        SkeletonKeyframe(
            name = frame.optString("name"),
            progress = frame.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
            joints = joints,
            easingToNext = EasingType.entries.firstOrNull { it.name == frame.optString("easingToNext") } ?: EasingType.LINEAR,
        )
    }
}
