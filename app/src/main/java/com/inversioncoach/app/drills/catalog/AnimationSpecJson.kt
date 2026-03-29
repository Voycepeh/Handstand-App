package com.inversioncoach.app.drills.catalog

import com.inversioncoach.app.motion.BodyJoint
import com.inversioncoach.app.motion.EasingType
import com.inversioncoach.app.motion.NormalizedPoint
import com.inversioncoach.app.motion.SkeletonAnimationSpec
import com.inversioncoach.app.motion.SkeletonKeyframe
import org.json.JSONArray
import org.json.JSONObject

object AnimationSpecJson {
    fun toJson(spec: SkeletonAnimationSpec): JSONObject = JSONObject().apply {
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

    fun fromJson(json: JSONObject): SkeletonAnimationSpec = SkeletonAnimationSpec(
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
            progress = frame.optDouble("progress", 0.0).toFloat(),
            joints = joints,
            easingToNext = EasingType.entries.firstOrNull { it.name == frame.optString("easingToNext") } ?: EasingType.LINEAR,
        )
    }
}
