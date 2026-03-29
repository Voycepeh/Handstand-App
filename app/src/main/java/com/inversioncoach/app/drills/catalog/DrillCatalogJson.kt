package com.inversioncoach.app.drills.catalog

import com.inversioncoach.app.motion.SkeletonAnimationSpec
import org.json.JSONArray
import org.json.JSONObject

object DrillCatalogJson {
    fun decodeCatalog(raw: String): List<DrillTemplate> {
        val root = JSONObject(raw)
        val drills = root.optJSONArray("drills") ?: JSONArray()
        return (0 until drills.length()).map { index -> decodeDrill(drills.getJSONObject(index)) }
    }

    fun encodeCatalog(drills: List<DrillTemplate>): String = JSONObject().apply {
        put("drills", JSONArray().apply { drills.forEach { put(encodeDrill(it)) } })
    }.toString(2)

    fun decodeDrill(json: JSONObject): DrillTemplate {
        val supportedViews = json.requireArray("supportedViews").toList { value -> CatalogCameraView.valueOf(value as String) }
        require(supportedViews.isNotEmpty()) { "supportedViews must be non-empty" }
        val defaultView = CatalogCameraView.valueOf(json.requireString("cameraView"))
        require(supportedViews.contains(defaultView)) { "cameraView must be in supportedViews" }

        val phases = json.requireArray("phases").toList { value ->
            val phase = value as JSONObject
            DrillPhaseTemplate(
                id = phase.requireString("id"),
                label = phase.requireString("label"),
                order = phase.requireInt("order"),
                progressWindow = phase.optJSONObject("progressWindow")?.let { window ->
                    PhaseWindow(window.requireFloat("start"), window.requireFloat("end"))
                } ?: PhaseWindow(0f, 1f),
            )
        }

        validatePhases(phases)

        val metricThresholds = json.optJSONObject("metricThresholds")?.let { thresholds ->
            thresholds.keys().asSequence().associateWith { key -> thresholds.optDouble(key).toFloat() }
        }.orEmpty()

        return DrillTemplate(
            id = json.requireString("id"),
            title = json.requireString("title"),
            family = json.optString("family", "general"),
            movementType = CatalogMovementType.valueOf(json.requireString("movementType")),
            cameraView = defaultView,
            supportedViews = supportedViews,
            analysisPlane = CatalogAnalysisPlane.valueOf(json.optString("analysisPlane", CatalogAnalysisPlane.SAGITTAL.name)),
            comparisonMode = CatalogComparisonMode.valueOf(json.optString("comparisonMode", CatalogComparisonMode.OVERLAY.name)),
            phases = phases,
            metricThresholds = metricThresholds,
            animationSpec = AnimationSpecJson.fromJson(json.getJSONObject("animationSpec")).also { validateAnimationSpec(it) },
        )
    }

    fun encodeDrill(drill: DrillTemplate): JSONObject {
        validateDrill(drill)
        return JSONObject().apply {
            put("id", drill.id)
            put("title", drill.title)
            put("family", drill.family)
            put("movementType", drill.movementType.name)
            put("cameraView", drill.cameraView.name)
            put("supportedViews", JSONArray().apply { drill.supportedViews.forEach { put(it.name) } })
            put("analysisPlane", drill.analysisPlane.name)
            put("comparisonMode", drill.comparisonMode.name)
            put("phases", JSONArray().apply {
                drill.phases.forEach { phase ->
                    put(JSONObject().apply {
                        put("id", phase.id)
                        put("label", phase.label)
                        put("order", phase.order)
                        put("progressWindow", JSONObject().apply {
                            put("start", phase.progressWindow.start)
                            put("end", phase.progressWindow.end)
                        })
                    })
                }
            })
            put("metricThresholds", JSONObject().apply { drill.metricThresholds.forEach { (k, v) -> put(k, v) } })
            put("animationSpec", AnimationSpecJson.toJson(drill.animationSpec))
        }
    }

    fun validateDrill(drill: DrillTemplate) {
        require(drill.id.isNotBlank()) { "id must be non-blank" }
        require(drill.title.isNotBlank()) { "title must be non-blank" }
        require(drill.supportedViews.isNotEmpty()) { "supportedViews must be non-empty" }
        require(drill.supportedViews.contains(drill.cameraView)) { "cameraView must be in supportedViews" }
        validatePhases(drill.phases)
        validateAnimationSpec(drill.animationSpec)
    }

    private fun validatePhases(phases: List<DrillPhaseTemplate>) {
        require(phases.map { it.id }.distinct().size == phases.size) { "phase ids must be unique" }
        require(phases.map { it.order }.distinct().size == phases.size) { "phase order must be unique" }
        phases.forEach { phase ->
            require(phase.id.isNotBlank()) { "phase id must be non-blank" }
            require(phase.label.isNotBlank()) { "phase label must be non-blank" }
            require(phase.progressWindow.start in 0f..1f) { "phase start must be within [0,1]" }
            require(phase.progressWindow.end in 0f..1f) { "phase end must be within [0,1]" }
            require(phase.progressWindow.start <= phase.progressWindow.end) { "phase window must satisfy start <= end" }
        }
    }

    private fun validateAnimationSpec(spec: SkeletonAnimationSpec) {
        require(spec.fpsHint > 0) { "fpsHint must be > 0" }
        require(spec.keyframes.isNotEmpty()) { "keyframes must be non-empty" }
        val progresses = spec.keyframes.map { frame ->
            require(frame.progress in 0f..1f) { "keyframe progress must be in [0,1]" }
            frame.progress
        }
        require(progresses.zipWithNext().all { (a, b) -> b >= a }) { "keyframes must be sorted by progress" }
        require(progresses.distinct().size == progresses.size) { "keyframe progress values must be unique" }
        if (!spec.loop) {
            require(progresses.first() == 0f) { "non-loop animation must start at 0" }
            require(progresses.last() == 1f) { "non-loop animation must end at 1" }
        }
    }

    private fun JSONObject.requireString(key: String): String = optString(key).takeIf { it.isNotBlank() }
        ?: error("Missing $key")

    private fun JSONObject.requireInt(key: String): Int = takeIf { has(key) }?.getInt(key) ?: error("Missing $key")

    private fun JSONObject.requireFloat(key: String): Float = takeIf { has(key) }?.getDouble(key)?.toFloat() ?: error("Missing $key")

    private fun JSONObject.requireArray(key: String): JSONArray = optJSONArray(key) ?: error("Missing $key")

    private fun <T> JSONArray.toList(transform: (Any) -> T): List<T> = (0 until length()).map { idx -> transform(get(idx)) }
}
