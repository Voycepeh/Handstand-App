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

    private fun JSONObject.toSkeletonTemplate(): SkeletonTemplate {
        val phasePoses = optJSONArray("phasePoses")?.toPhasePoses().orEmpty()
        val explicitKeyframes = optJSONArray("keyframes")?.toKeyframes().orEmpty()
        val fallbackKeyframes = if (explicitKeyframes.isNotEmpty()) {
            explicitKeyframes
        } else {
            phasePoses.toKeyframesFromPhasePoses()
        }
        return SkeletonTemplate(
            id = getString("id"),
            loop = getBoolean("loop"),
            mirroredSupported = optBoolean("mirroredSupported", false),
            framesPerSecond = getInt("framesPerSecond"),
            phasePoses = phasePoses,
            keyframes = fallbackKeyframes,
        )
    }

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
            add(
                SkeletonKeyframeTemplate(
                    progress = obj.getDouble("progress").toFloat(),
                    joints = normalizeJointNames(joints),
                ),
            )
        }
    }

    private fun JSONArray.toPhasePoses(): List<PhasePoseTemplate> = buildList {
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
            add(
                PhasePoseTemplate(
                    phaseId = obj.getString("phaseId"),
                    name = obj.optString("name", obj.getString("phaseId")),
                    joints = normalizeJointNames(joints),
                    holdDurationMs = obj.optInt("holdDurationMs").takeIf { it > 0 },
                    transitionDurationMs = obj.optInt("transitionDurationMs", 700).coerceAtLeast(100),
                ),
            )
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
        if (phasePoses.isNotEmpty()) {
            put("phasePoses", JSONArray().apply { phasePoses.forEach { put(it.toJson()) } })
        }
        put("keyframes", JSONArray().apply {
            keyframesForExport().forEach { put(it.toJson()) }
        })
    }

    private fun PhasePoseTemplate.toJson(): JSONObject = JSONObject().apply {
        put("phaseId", phaseId)
        put("name", name)
        holdDurationMs?.let { put("holdDurationMs", it) }
        put("transitionDurationMs", transitionDurationMs)
        put(
            "joints",
            JSONObject().apply {
                normalizeJointNames(joints).forEach { (jointName, point) -> put(jointName, JSONArray().put(point.x).put(point.y)) }
            },
        )
    }

    private fun SkeletonTemplate.keyframesForExport(): List<SkeletonKeyframeTemplate> {
        if (phasePoses.isEmpty()) return keyframes
        return phasePoses.toKeyframesFromPhasePoses()
    }

    private fun List<PhasePoseTemplate>.toKeyframesFromPhasePoses(): List<SkeletonKeyframeTemplate> {
        if (isEmpty()) return emptyList()
        val phasePoses = this
        if (phasePoses.size == 1) return listOf(
            SkeletonKeyframeTemplate(progress = 0f, joints = phasePoses.first().joints),
            SkeletonKeyframeTemplate(progress = 1f, joints = phasePoses.first().joints),
        )
        val totalDuration = phasePoses.sumOf { (it.holdDurationMs ?: 0) + it.transitionDurationMs }.coerceAtLeast(1)
        var elapsed = 0
        return phasePoses.map { pose ->
            val progress = elapsed.toFloat() / totalDuration.toFloat()
            elapsed += (pose.holdDurationMs ?: 0) + pose.transitionDurationMs
            SkeletonKeyframeTemplate(progress = progress.coerceIn(0f, 1f), joints = pose.joints)
        } + SkeletonKeyframeTemplate(progress = 1f, joints = phasePoses.last().joints)
    }

    private fun SkeletonKeyframeTemplate.toJson(): JSONObject = JSONObject().apply {
        put("progress", progress)
        put(
            "joints",
            JSONObject().apply {
                normalizeJointNames(joints).forEach { (jointName, point) -> put(jointName, JSONArray().put(point.x).put(point.y)) }
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
            drill.skeletonTemplate.phasePoses.forEach { pose ->
                require(pose.phaseId in validPhaseIds) {
                    "drill '${drill.id}' phasePose references unknown phase '${pose.phaseId}'"
                }
            }
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

    private fun normalizeJointNames(joints: Map<String, JointPoint>): Map<String, JointPoint> {
        val aliases = mapOf(
            "left_shoulder" to "shoulder_left",
            "right_shoulder" to "shoulder_right",
            "left_hip" to "hip_left",
            "right_hip" to "hip_right",
            "left_ankle" to "ankle_left",
            "right_ankle" to "ankle_right",
            "left_wrist" to "wrist_left",
            "right_wrist" to "wrist_right",
            "nose" to "head",
        )
        return joints.entries.associate { (name, point) -> (aliases[name] ?: name) to point }
    }
}
