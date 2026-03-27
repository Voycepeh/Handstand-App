package com.inversioncoach.app.calibration.rep

import com.inversioncoach.app.model.PoseFrame
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

class RepMetricExtractor {
    fun extract(frame: PoseFrame): RepMetricSnapshot {
        val joints = frame.joints.associateBy { it.name }

        fun point(name: String) = joints[name]
        fun angle(a: String, b: String, c: String): Float? {
            val p1 = point(a) ?: return null
            val p2 = point(b) ?: return null
            val p3 = point(c) ?: return null
            val abx = p1.x - p2.x
            val aby = p1.y - p2.y
            val cbx = p3.x - p2.x
            val cby = p3.y - p2.y
            val dot = abx * cbx + aby * cby
            val mag1 = sqrt(abx * abx + aby * aby)
            val mag2 = sqrt(cbx * cbx + cby * cby)
            val denom = (mag1 * mag2).coerceAtLeast(0.0001f)
            val cos = (dot / denom).coerceIn(-1f, 1f)
            return Math.toDegrees(acos(cos).toDouble()).toFloat()
        }

        val elbow = listOfNotNull(
            angle("left_shoulder", "left_elbow", "left_wrist"),
            angle("right_shoulder", "right_elbow", "right_wrist"),
        ).averageOrNull()

        val shoulder = listOfNotNull(
            angle("left_hip", "left_shoulder", "left_elbow"),
            angle("right_hip", "right_shoulder", "right_elbow"),
        ).averageOrNull()

        val hip = listOfNotNull(
            angle("left_shoulder", "left_hip", "left_knee"),
            angle("right_shoulder", "right_hip", "right_knee"),
        ).averageOrNull()

        val torsoDeviation = listOfNotNull(
            point("left_shoulder")?.x?.let { s -> point("left_hip")?.x?.let { abs(s - it) } },
            point("right_shoulder")?.x?.let { s -> point("right_hip")?.x?.let { abs(s - it) } },
        ).averageOrNull()

        val wristShoulderOffset = listOfNotNull(
            point("left_wrist")?.x?.let { w -> point("left_shoulder")?.x?.let { abs(w - it) } },
            point("right_wrist")?.x?.let { w -> point("right_shoulder")?.x?.let { abs(w - it) } },
        ).averageOrNull()

        return RepMetricSnapshot(
            values = buildMap {
                elbow?.let { put("elbow_flexion", it) }
                shoulder?.let { put("shoulder_flexion", it) }
                hip?.let { put("hip_angle", it) }
                torsoDeviation?.let { put("torso_deviation", it) }
                wristShoulderOffset?.let { put("wrist_shoulder_offset", it) }
            },
        )
    }
}

private fun Iterable<Float>.averageOrNull(): Float? {
    val list = toList()
    return if (list.isEmpty()) null else list.average().toFloat()
}
