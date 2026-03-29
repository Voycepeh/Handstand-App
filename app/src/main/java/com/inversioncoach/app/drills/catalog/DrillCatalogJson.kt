package com.inversioncoach.app.drills.catalog

import org.json.JSONArray
import org.json.JSONObject

object DrillCatalogJson {
    fun decode(raw: String): DrillCatalog {
        val root = JSONObject(raw)
        val catalog = DrillCatalog(
            schemaVersion = root.getInt("schemaVersion"),
            catalogId = root.getString("catalogId"),
            drills = root.getJSONArray("drills").toDrillTemplates(),
        )
        validate(catalog)
        return catalog
    }

    fun encode(catalog: DrillCatalog): String = JSONObject().apply {
        put("schemaVersion", catalog.schemaVersion)
        put("catalogId", catalog.catalogId)
        put("drills", JSONArray().apply { catalog.drills.forEach { put(it.toJson()) } })
    }.toString(2)

    private fun JSONArray.toDrillTemplates(): List<DrillTemplate> = buildList {
        for (i in 0 until length()) {
            add(getJSONObject(i).toDrillTemplate())
        }
    }

    private fun JSONObject.toDrillTemplate(): DrillTemplate = DrillTemplate(
        id = getString("id"),
        title = getString("title"),
        family = getString("family"),
        movementType = CatalogMovementType.valueOf(getString("movementType").uppercase()),
        tags = getJSONArray("tags").toStringList(),
        cameraView = CameraView.valueOf(getString("cameraView").uppercase()),
        supportedViews = getJSONArray("supportedViews").toCameraViews(),
        analysisPlane = AnalysisPlane.valueOf(getString("analysisPlane").uppercase()),
        comparisonMode = ComparisonMode.valueOf(getString("comparisonMode").uppercase()),
        phases = getJSONArray("phases").toPhases(),
        skeletonTemplate = getJSONObject("skeletonTemplate").toSkeletonTemplate(),
        calibration = getJSONObject("calibration").toCalibrationTemplate(),
    )

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) add(getString(i))
    }

    private fun JSONArray.toPhases(): List<DrillPhaseTemplate> = buildList {
        for (i in 0 until length()) {
            val obj = getJSONObject(i)
            add(
                DrillPhaseTemplate(
                    id = obj.getString("id"),
                    label = obj.getString("label"),
                    order = obj.getInt("order"),
                    progressWindow = obj.optJSONArray("progressWindow")?.let {
                        PhaseWindow(it.getDouble(0).toFloat(), it.getDouble(1).toFloat())
                    },
                ),
            )
        }
    }

    private fun JSONArray.toCameraViews(): List<CameraView> = buildList {
        for (i in 0 until length()) {
            add(CameraView.valueOf(getString(i).uppercase()))
        }
    }

    private fun JSONObject.toSkeletonTemplate(): SkeletonTemplate = SkeletonTemplate(
        id = getString("id"),
        loop = getBoolean("loop"),
        mirroredSupported = optBoolean("mirroredSupported", false),
        framesPerSecond = getInt("framesPerSecond"),
        keyframes = getJSONArray("keyframes").toKeyframes(),
    )

    private fun JSONArray.toKeyframes(): List<SkeletonKeyframeTemplate> = buildList {
        for (i in 0 until length()) {
            val obj = getJSONObject(i)
            val jointsObj = obj.getJSONObject("joints")
            val joints = buildMap {
                val keys = jointsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val point = jointsObj.getJSONArray(key)
                    put(key, JointPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat()))
                }
            }
            add(SkeletonKeyframeTemplate(progress = obj.getDouble("progress").toFloat(), joints = joints))
        }
    }

    private fun JSONObject.toCalibrationTemplate(): CalibrationTemplate {
        val thresholdsObj = getJSONObject("metricThresholds")
        val thresholds = buildMap {
            val keys = thresholdsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, thresholdsObj.getDouble(key).toFloat())
            }
        }

        val windowsObj = getJSONObject("phaseWindows")
        val windows = buildMap {
            val keys = windowsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val bounds = windowsObj.getJSONArray(key)
                put(key, PhaseWindow(start = bounds.getDouble(0).toFloat(), end = bounds.getDouble(1).toFloat()))
            }
        }

        return CalibrationTemplate(metricThresholds = thresholds, phaseWindows = windows)
    }

    private fun DrillTemplate.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("family", family)
        put("movementType", movementType.name.lowercase())
        put("tags", JSONArray(tags))
        put("cameraView", cameraView.name.lowercase())
        put("supportedViews", JSONArray(supportedViews.map { it.name.lowercase() }))
        put("analysisPlane", analysisPlane.name.lowercase())
        put("comparisonMode", comparisonMode.name.lowercase())
        put("phases", JSONArray().apply { phases.forEach { put(it.toJson()) } })
        put("skeletonTemplate", skeletonTemplate.toJson())
        put("calibration", calibration.toJson())
    }

    private fun DrillPhaseTemplate.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("order", order)
        progressWindow?.let { put("progressWindow", JSONArray().put(it.start).put(it.end)) }
    }

    private fun SkeletonTemplate.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("loop", loop)
        put("mirroredSupported", mirroredSupported)
        put("framesPerSecond", framesPerSecond)
        put("keyframes", JSONArray().apply { keyframes.forEach { put(it.toJson()) } })
    }

    private fun SkeletonKeyframeTemplate.toJson(): JSONObject = JSONObject().apply {
        put("progress", progress)
        put(
            "joints",
            JSONObject().apply {
                joints.forEach { (jointName, point) -> put(jointName, JSONArray().put(point.x).put(point.y)) }
            },
        )
    }

    private fun CalibrationTemplate.toJson(): JSONObject = JSONObject().apply {
        put("metricThresholds", JSONObject().apply { metricThresholds.forEach { (metric, value) -> put(metric, value) } })
        put(
            "phaseWindows",
            JSONObject().apply {
                phaseWindows.forEach { (phaseId, window) -> put(phaseId, JSONArray().put(window.start).put(window.end)) }
            },
        )
    }

    private fun validate(catalog: DrillCatalog) {
        catalog.drills.forEach { drill ->
            require(drill.skeletonTemplate.framesPerSecond > 0) {
                "drill '${drill.id}' has invalid framesPerSecond=${drill.skeletonTemplate.framesPerSecond}"
            }
            require(drill.skeletonTemplate.keyframes.isNotEmpty()) { "drill '${drill.id}' must include keyframes" }

            val sorted = drill.skeletonTemplate.keyframes.sortedBy { it.progress }
            require(sorted.first().progress >= 0f && sorted.last().progress <= 1f) {
                "drill '${drill.id}' keyframe progress must be within [0,1]"
            }
            require(sorted.zipWithNext().all { (a, b) -> b.progress >= a.progress }) {
                "drill '${drill.id}' keyframes must be sortable by progress"
            }

            val baselineJoints = sorted.first().joints.keys
            sorted.forEach { frame ->
                require(frame.joints.keys.containsAll(baselineJoints)) {
                    "drill '${drill.id}' keyframe at ${frame.progress} is missing baseline joints"
                }
            }

            val validPhaseIds = drill.phases.map { it.id }.toSet()
            val windowsFromCalibration = drill.calibration.phaseWindows.keys
            require(windowsFromCalibration.all { it in validPhaseIds }) {
                "drill '${drill.id}' calibration phaseWindows reference unknown phase ids"
            }

            drill.phases.forEach { phase ->
                phase.progressWindow?.let { window ->
                    require(window.start in 0f..1f && window.end in 0f..1f && window.start <= window.end) {
                        "drill '${drill.id}' phase '${phase.id}' has invalid progressWindow"
                    }
                }
            }
        }
    }
}
