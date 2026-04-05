package com.inversioncoach.app.drillpackage.io

import com.inversioncoach.app.drillpackage.model.DrillManifest
import com.inversioncoach.app.drillpackage.model.DrillPackage
import com.inversioncoach.app.drillpackage.model.PortableAssetRef
import com.inversioncoach.app.drillpackage.model.PortableDrill
import com.inversioncoach.app.drillpackage.model.PortableJoint2D
import com.inversioncoach.app.drillpackage.model.PortablePhase
import com.inversioncoach.app.drillpackage.model.PortablePose
import com.inversioncoach.app.drillpackage.model.PortableViewType
import com.inversioncoach.app.drillpackage.model.SchemaVersion
import org.json.JSONArray
import org.json.JSONObject

object DrillPackageJsonCodec {
    fun encode(pkg: DrillPackage): String = JSONObject().apply {
        put("manifest", pkg.manifest.toJson())
        put("drills", JSONArray().apply { pkg.drills.forEach { put(it.toJson()) } })
    }.toString(2)

    fun decode(raw: String): DrillPackage {
        val root = JSONObject(raw)
        val manifest = root.getJSONObject("manifest").toManifest()
        val drills = root.optJSONArray("drills").toList { getJSONObject(it).toPortableDrill() }
        return DrillPackage(manifest = manifest, drills = drills)
    }

    private fun DrillManifest.toJson(): JSONObject = JSONObject().apply {
        put("packageId", packageId)
        put("schemaVersion", JSONObject().apply {
            put("major", schemaVersion.major)
            put("minor", schemaVersion.minor)
        })
        put("source", source)
        put("exportedAtMs", exportedAtMs)
        put("assets", JSONArray().apply { assets.forEach { put(it.toJson()) } })
    }

    private fun PortableAssetRef.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("uri", uri)
    }

    private fun PortableDrill.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("family", family)
        put("movementType", movementType)
        put("cameraView", cameraView.name)
        put("supportedViews", JSONArray().apply { supportedViews.forEach { put(it.name) } })
        put("comparisonMode", comparisonMode)
        put("normalizationBasis", normalizationBasis)
        put("keyJoints", JSONArray().apply { keyJoints.forEach { put(it) } })
        put("tags", JSONArray().apply { tags.forEach { put(it) } })
        put("phases", JSONArray().apply { phases.forEach { put(it.toJson()) } })
        put("poses", JSONArray().apply { poses.forEach { put(it.toJson()) } })
        put("metricThresholds", JSONObject(metricThresholds))
        put("extensions", JSONObject(extensions))
    }

    private fun PortablePhase.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("order", order)
        put("windowStart", windowStart)
        put("windowEnd", windowEnd)
    }

    private fun PortablePose.toJson(): JSONObject = JSONObject().apply {
        put("phaseId", phaseId)
        put("name", name)
        put("viewType", viewType.name)
        put("joints", JSONObject(joints.mapValues { (_, pt) -> pt.toJson() }))
        put("holdDurationMs", holdDurationMs)
        put("transitionDurationMs", transitionDurationMs)
    }

    private fun PortableJoint2D.toJson(): JSONObject = JSONObject().apply {
        put("x", x)
        put("y", y)
        put("visibility", visibility)
        put("confidence", confidence)
    }

    private fun JSONObject.toManifest(): DrillManifest {
        val schema = getJSONObject("schemaVersion")
        return DrillManifest(
            packageId = getString("packageId"),
            schemaVersion = SchemaVersion(
                major = schema.getInt("major"),
                minor = schema.optInt("minor", 0),
            ),
            source = optString("source", "unknown"),
            exportedAtMs = optLong("exportedAtMs", 0L),
            assets = optJSONArray("assets").toList { getJSONObject(it).toAssetRef() },
        )
    }

    private fun JSONObject.toAssetRef(): PortableAssetRef = PortableAssetRef(
        id = getString("id"),
        type = getString("type"),
        uri = getString("uri"),
    )

    private fun JSONObject.toPortableDrill(): PortableDrill = PortableDrill(
        id = getString("id"),
        title = getString("title"),
        description = optString("description"),
        family = optString("family", "runtime"),
        movementType = getString("movementType"),
        cameraView = PortableViewType.valueOf(optString("cameraView", PortableViewType.SIDE.name)),
        supportedViews = optJSONArray("supportedViews").toList { PortableViewType.valueOf(getString(it)) },
        comparisonMode = optString("comparisonMode", "POSE_TIMELINE"),
        normalizationBasis = optString("normalizationBasis", "HIPS"),
        keyJoints = optJSONArray("keyJoints").toList { getString(it) },
        tags = optJSONArray("tags").toList { getString(it) },
        phases = optJSONArray("phases").toList { getJSONObject(it).toPhase() },
        poses = optJSONArray("poses").toList { getJSONObject(it).toPose() },
        metricThresholds = optJSONObject("metricThresholds")?.let { json ->
            json.keys().asSequence().associateWith { key -> json.optDouble(key, 0.0).toFloat() }
        }.orEmpty(),
        extensions = optJSONObject("extensions")?.let { json ->
            json.keys().asSequence().associateWith { key -> json.optString(key, "") }
        }.orEmpty(),
    )

    private fun JSONObject.toPhase(): PortablePhase = PortablePhase(
        id = getString("id"),
        label = optString("label", getString("id")),
        order = optInt("order", 0),
        windowStart = optDouble("windowStart", 0.0).toFloat(),
        windowEnd = optDouble("windowEnd", 1.0).toFloat(),
    )

    private fun JSONObject.toPose(): PortablePose = PortablePose(
        phaseId = getString("phaseId"),
        name = optString("name", getString("phaseId")),
        viewType = PortableViewType.valueOf(optString("viewType", PortableViewType.SIDE.name)),
        joints = getJSONObject("joints").let { jointsJson ->
            jointsJson.keys().asSequence().associateWith { key -> jointsJson.getJSONObject(key).toJoint() }
        },
        holdDurationMs = optInt("holdDurationMs").takeIf { has("holdDurationMs") },
        transitionDurationMs = optInt("transitionDurationMs", 700),
    )

    private fun JSONObject.toJoint(): PortableJoint2D = PortableJoint2D(
        x = getDouble("x").toFloat(),
        y = getDouble("y").toFloat(),
        visibility = optDouble("visibility").takeIf { has("visibility") }?.toFloat(),
        confidence = optDouble("confidence").takeIf { has("confidence") }?.toFloat(),
    )

    private inline fun <T> JSONArray?.toList(block: JSONArray.(Int) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { index -> block(index) }
    }
}
