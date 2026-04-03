package com.inversioncoach.app.drills

import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import org.json.JSONObject
import java.util.Base64

internal object DrillStudioPayloadCodec {
    fun decodePreviewSkeleton(drillId: String, cueConfigJson: String): SkeletonTemplate? = runCatching {
        val encoded = DrillCueConfigCodec.parse(cueConfigJson).studioPayload ?: return null
        if (encoded.isBlank()) return null
        val decoded = String(Base64.getUrlDecoder().decode(encoded))
        val json = JSONObject(decoded)
        val keyframes = json.optJSONArray("keyframes")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val jointsJson = item.optJSONObject("joints") ?: JSONObject()
                val joints = jointsJson.keys().asSequence().associateWith { joint ->
                    val point = jointsJson.getJSONArray(joint)
                    JointPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
                }
                SkeletonKeyframeTemplate(
                    progress = item.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                    joints = joints,
                )
            }.sortedBy { it.progress }
        }.orEmpty()

        if (keyframes.isEmpty()) return null

        val fpsHint = json.optInt("fpsHint", 12).takeIf { it > 0 } ?: 12
        SkeletonTemplate(
            id = "preview-$drillId",
            loop = true,
            mirroredSupported = false,
            framesPerSecond = fpsHint,
            keyframes = keyframes,
        )
    }.getOrNull()
}
