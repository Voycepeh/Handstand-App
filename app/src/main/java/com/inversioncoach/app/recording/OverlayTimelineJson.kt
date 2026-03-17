package com.inversioncoach.app.recording

import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.SessionMode
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.FreestyleViewMode
import org.json.JSONArray
import org.json.JSONObject

object OverlayTimelineJson {
    fun encode(timeline: OverlayTimeline): String = JSONObject().apply {
        put("version", timeline.version)
        put("startedAtMs", timeline.startedAtMs)
        put("sampleIntervalMs", timeline.sampleIntervalMs)
        put("frames", JSONArray().apply {
            timeline.frames.forEach { frame ->
                put(JSONObject().apply {
                    put("sessionId", frame.sessionId)
                    put("relativeTimestampMs", frame.relativeTimestampMs)
                    put("absoluteVideoPtsUs", frame.absoluteVideoPtsUs)
                    put("timestampMs", frame.timestampMs)
                    put("captureWidth", frame.captureWidth)
                    put("captureHeight", frame.captureHeight)
                    put("sourceFrameIndex", frame.sourceFrameIndex)
                    put("confidence", frame.confidence)
                    put("landmarks", encodeJoints(frame.landmarks))
                    put("smoothedLandmarks", encodeJoints(frame.smoothedLandmarks))
                    put("visibilityFlags", JSONObject(frame.visibilityFlags))
                    put("alignmentAngles", JSONObject(frame.alignmentAngles.mapValues { it.value.toDouble() }))
                    put("drillMetadata", JSONObject().apply {
                        put("sessionMode", frame.drillMetadata.sessionMode.name)
                        put("drillCameraSide", frame.drillMetadata.drillCameraSide?.name)
                        put("freestyleViewMode", frame.drillMetadata.freestyleViewMode.name)
                        put("showSkeleton", frame.drillMetadata.showSkeleton)
                        put("showIdealLine", frame.drillMetadata.showIdealLine)
                        put("bodyVisible", frame.drillMetadata.bodyVisible)
                        put("mirrorMode", frame.drillMetadata.mirrorMode)
                    })
                })
            }
        })
    }.toString()

    fun decode(raw: String): OverlayTimeline {
        val obj = JSONObject(raw)
        val frames = obj.optJSONArray("frames") ?: JSONArray()
        val parsed = buildList {
            for (i in 0 until frames.length()) {
                val frame = frames.getJSONObject(i)
                val metadata = frame.optJSONObject("drillMetadata") ?: JSONObject()
                val relativeTimestampMs = frame.optLong("relativeTimestampMs", frame.optLong("timestampMs").coerceAtLeast(0L))
                add(
                    OverlayTimelineFrame(
                        sessionId = frame.optLong("sessionId", 0L),
                        relativeTimestampMs = relativeTimestampMs,
                        absoluteVideoPtsUs = if (frame.has("absoluteVideoPtsUs") && !frame.isNull("absoluteVideoPtsUs")) frame.optLong("absoluteVideoPtsUs") else null,
                        timestampMs = frame.optLong("timestampMs", obj.optLong("startedAtMs") + relativeTimestampMs),
                        captureWidth = if (frame.has("captureWidth") && !frame.isNull("captureWidth")) frame.optInt("captureWidth") else null,
                        captureHeight = if (frame.has("captureHeight") && !frame.isNull("captureHeight")) frame.optInt("captureHeight") else null,
                        sourceFrameIndex = if (frame.has("sourceFrameIndex") && !frame.isNull("sourceFrameIndex")) frame.optLong("sourceFrameIndex") else null,
                        confidence = frame.optDouble("confidence", 0.0).toFloat(),
                        landmarks = decodeJoints(frame.optJSONArray("landmarks")),
                        smoothedLandmarks = decodeJoints(frame.optJSONArray("smoothedLandmarks")),
                        skeletonLines = emptyList(),
                        headPoint = null,
                        hipPoint = null,
                        idealLine = null,
                        alignmentAngles = decodeFloatMap(frame.optJSONObject("alignmentAngles")),
                        visibilityFlags = decodeBooleanMap(frame.optJSONObject("visibilityFlags")),
                        drillMetadata = OverlayDrillMetadata(
                            sessionMode = SessionMode.valueOf(metadata.optString("sessionMode", SessionMode.DRILL.name)),
                            drillCameraSide = metadata.optString("drillCameraSide").takeIf { it.isNotBlank() }?.let(DrillCameraSide::valueOf),
                            freestyleViewMode = FreestyleViewMode.valueOf(metadata.optString("freestyleViewMode", FreestyleViewMode.UNKNOWN.name)),
                            showSkeleton = metadata.optBoolean("showSkeleton", true),
                            showIdealLine = metadata.optBoolean("showIdealLine", true),
                            bodyVisible = metadata.optBoolean("bodyVisible", true),
                            mirrorMode = metadata.optBoolean("mirrorMode", false),
                        ),
                    ),
                )
            }
        }
        return OverlayTimeline(
            version = obj.optInt("version", 1),
            startedAtMs = obj.optLong("startedAtMs"),
            sampleIntervalMs = obj.optLong("sampleIntervalMs", OverlayTimelineRecorder.DEFAULT_SAMPLE_INTERVAL_MS),
            frames = parsed.sortedBy { it.timestampMs },
        )
    }

    private fun encodeJoints(joints: List<JointPoint>): JSONArray = JSONArray().apply {
        joints.forEach { joint ->
            put(JSONObject().apply {
                put("name", joint.name)
                put("x", joint.x)
                put("y", joint.y)
                put("z", joint.z)
                put("visibility", joint.visibility)
            })
        }
    }

    private fun decodeJoints(array: JSONArray?): List<JointPoint> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            add(
                JointPoint(
                    name = obj.getString("name"),
                    x = obj.optDouble("x").toFloat(),
                    y = obj.optDouble("y").toFloat(),
                    z = obj.optDouble("z").toFloat(),
                    visibility = obj.optDouble("visibility", 0.0).toFloat(),
                ),
            )
        }
    }

    private fun decodeBooleanMap(obj: JSONObject?): Map<String, Boolean> = buildMap {
        if (obj == null) return@buildMap
        obj.keys().forEach { put(it, obj.optBoolean(it)) }
    }

    private fun decodeFloatMap(obj: JSONObject?): Map<String, Float> = buildMap {
        if (obj == null) return@buildMap
        obj.keys().forEach { put(it, obj.optDouble(it).toFloat()) }
    }
}
