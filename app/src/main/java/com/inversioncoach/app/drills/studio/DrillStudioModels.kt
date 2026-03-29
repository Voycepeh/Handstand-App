package com.inversioncoach.app.drills.studio

import com.inversioncoach.app.drills.catalog.AnimationSpecJson
import com.inversioncoach.app.drills.catalog.CatalogAnalysisPlane
import com.inversioncoach.app.drills.catalog.CatalogCameraView
import com.inversioncoach.app.drills.catalog.CatalogComparisonMode
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.motion.SkeletonAnimationSpec
import org.json.JSONArray
import org.json.JSONObject

data class DrillStudioPhaseWindow(val start: Float, val end: Float)

data class DrillStudioPhase(
    val id: String,
    val label: String,
    val order: Int,
    val progressWindow: DrillStudioPhaseWindow,
    val anchorKeyframeName: String? = null,
    val thresholdOverrides: Map<String, Float> = emptyMap(),
)

data class DrillStudioDocument(
    val id: String,
    val seededCatalogDrillId: String?,
    val displayName: String,
    val family: String,
    val movementType: CatalogMovementType,
    val cameraView: CatalogCameraView,
    val supportedViews: List<CatalogCameraView>,
    val analysisPlane: CatalogAnalysisPlane,
    val comparisonMode: CatalogComparisonMode,
    val phases: List<DrillStudioPhase>,
    val metricThresholds: Map<String, Float>,
    val animationSpec: SkeletonAnimationSpec,
)

object DrillStudioCodec {
    fun toJson(document: DrillStudioDocument): JSONObject = JSONObject().apply {
        validateDocument(document)
        put("id", document.id)
        put("seededCatalogDrillId", document.seededCatalogDrillId)
        put("displayName", document.displayName)
        put("family", document.family)
        put("movementType", document.movementType.name)
        put("cameraView", document.cameraView.name)
        put("supportedViews", JSONArray().apply { document.supportedViews.forEach { put(it.name) } })
        put("analysisPlane", document.analysisPlane.name)
        put("comparisonMode", document.comparisonMode.name)
        put("phases", JSONArray().apply {
            document.phases.forEach { phase ->
                put(JSONObject().apply {
                    put("id", phase.id)
                    put("label", phase.label)
                    put("order", phase.order)
                    put("progressWindow", JSONObject().apply {
                        put("start", phase.progressWindow.start)
                        put("end", phase.progressWindow.end)
                    })
                    put("anchorKeyframeName", phase.anchorKeyframeName)
                    put("thresholdOverrides", JSONObject().apply {
                        phase.thresholdOverrides.forEach { (key, value) -> put(key, value) }
                    })
                })
            }
        })
        put("metricThresholds", JSONObject().apply {
            document.metricThresholds.forEach { (key, value) -> put(key, value) }
        })
        put("animationSpec", AnimationSpecJson.toJson(document.animationSpec))
    }

    fun fromJson(json: JSONObject): DrillStudioDocument {
        val supportedViews = json.optJSONArray("supportedViews")?.let { arr ->
            (0 until arr.length()).map { idx -> CatalogCameraView.valueOf(arr.getString(idx)) }
        }.orEmpty()
        require(supportedViews.isNotEmpty()) { "supportedViews must be non-empty" }
        val cameraView = CatalogCameraView.valueOf(json.optString("cameraView", CatalogCameraView.SIDE.name))
        require(supportedViews.contains(cameraView)) { "cameraView must be in supportedViews" }

        val phases = json.optJSONArray("phases")?.let { arr ->
            (0 until arr.length()).map { index ->
                val phase = arr.getJSONObject(index)
                val window = phase.getJSONObject("progressWindow")
                DrillStudioPhase(
                    id = phase.getString("id"),
                    label = phase.getString("label"),
                    order = phase.getInt("order"),
                    progressWindow = DrillStudioPhaseWindow(
                        start = window.getDouble("start").toFloat(),
                        end = window.getDouble("end").toFloat(),
                    ),
                    anchorKeyframeName = phase.optString("anchorKeyframeName").ifBlank { null },
                    thresholdOverrides = phase.optJSONObject("thresholdOverrides")?.let { overrides ->
                        overrides.keys().asSequence().associateWith { key -> overrides.getDouble(key).toFloat() }
                    }.orEmpty(),
                )
            }
        }.orEmpty()

        val thresholds = json.optJSONObject("metricThresholds")?.let { thresholdsJson ->
            thresholdsJson.keys().asSequence().associateWith { key -> thresholdsJson.getDouble(key).toFloat() }
        }.orEmpty()

        val document = DrillStudioDocument(
            id = json.optString("id"),
            seededCatalogDrillId = json.optString("seededCatalogDrillId").ifBlank { null },
            displayName = json.optString("displayName"),
            family = json.optString("family", "general"),
            movementType = CatalogMovementType.valueOf(json.optString("movementType", CatalogMovementType.HOLD.name)),
            cameraView = cameraView,
            supportedViews = supportedViews,
            analysisPlane = CatalogAnalysisPlane.valueOf(json.optString("analysisPlane", CatalogAnalysisPlane.SAGITTAL.name)),
            comparisonMode = CatalogComparisonMode.valueOf(json.optString("comparisonMode", CatalogComparisonMode.OVERLAY.name)),
            phases = phases,
            metricThresholds = thresholds,
            animationSpec = AnimationSpecJson.fromJson(json.getJSONObject("animationSpec")),
        )
        validateDocument(document)
        return document
    }

    private fun validateDocument(document: DrillStudioDocument) {
        require(document.id.isNotBlank()) { "id must be non-blank" }
        require(document.displayName.isNotBlank()) { "displayName must be non-blank" }
        require(document.supportedViews.isNotEmpty()) { "supportedViews must be non-empty" }
        require(document.supportedViews.contains(document.cameraView)) { "cameraView must be in supportedViews" }

        val frames = document.animationSpec.keyframes
        require(document.animationSpec.fpsHint > 0) { "fpsHint must be > 0" }
        require(frames.isNotEmpty()) { "keyframes must be non-empty" }
        val progress = frames.map { frame ->
            require(frame.progress in 0f..1f) { "keyframe progress must be in [0,1]" }
            frame.progress
        }
        require(progress.zipWithNext().all { (a, b) -> b >= a }) { "keyframes must be sorted by progress" }
        require(progress.distinct().size == progress.size) { "keyframe progress must be unique" }

        require(document.phases.map { it.id }.distinct().size == document.phases.size) { "phase ids must be unique" }
        require(document.phases.map { it.order }.distinct().size == document.phases.size) { "phase order must be unique" }
        val keyframeNames = frames.map { it.name }.toSet()
        document.phases.forEach { phase ->
            require(phase.id.isNotBlank()) { "phase id must be non-blank" }
            require(phase.label.isNotBlank()) { "phase label must be non-blank" }
            require(phase.progressWindow.start in 0f..1f) { "phase start must be within [0,1]" }
            require(phase.progressWindow.end in 0f..1f) { "phase end must be within [0,1]" }
            require(phase.progressWindow.start <= phase.progressWindow.end) { "phase window must satisfy start <= end" }
            if (phase.anchorKeyframeName != null) {
                require(keyframeNames.contains(phase.anchorKeyframeName)) { "anchor keyframe ${phase.anchorKeyframeName} missing" }
            }
        }
    }
}
