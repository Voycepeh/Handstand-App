package com.inversioncoach.app.drills.catalog

import kotlin.math.cos

object StickFigureAnimator {
    val canonicalBones: List<Pair<String, String>> = listOf(
        "head" to "shoulder_left",
        "head" to "shoulder_right",
        "shoulder_left" to "wrist_left",
        "shoulder_right" to "wrist_right",
        "shoulder_left" to "hip_left",
        "shoulder_right" to "hip_right",
        "hip_left" to "ankle_left",
        "hip_right" to "ankle_right",
    )

    fun poseAt(
        template: SkeletonTemplate,
        progress: Float,
        mirrored: Boolean = false,
    ): Map<String, JointPoint> {
        if (template.keyframes.isEmpty()) return emptyMap()
        if (template.keyframes.size == 1) {
            return maybeMirror(template.keyframes.first().joints, mirrored && template.mirroredSupported)
        }

        val sorted = template.keyframes.sortedBy { it.progress }
        val normalizedProgress = normalizeProgress(progress, template.loop)
        val (left, right, local) = segmentFor(sorted, normalizedProgress, template.loop)
        val eased = easeInOut(local)

        val pose = (left.joints.keys + right.joints.keys).associateWith { joint ->
            val start = left.joints[joint] ?: right.joints[joint] ?: JointPoint(0.5f, 0.5f)
            val end = right.joints[joint] ?: start
            JointPoint(
                x = lerp(start.x, end.x, eased),
                y = lerp(start.y, end.y, eased),
            )
        }
        return maybeMirror(pose, mirrored && template.mirroredSupported)
    }

    fun interpolateTimeline(
        template: SkeletonTemplate,
        sampleCount: Int,
    ): List<Map<String, JointPoint>> {
        if (sampleCount <= 1) return listOf(poseAt(template, 0f))
        return List(sampleCount) { index ->
            val p = index.toFloat() / (sampleCount - 1).toFloat()
            poseAt(template, p)
        }
    }

    private fun normalizeProgress(progress: Float, loop: Boolean): Float {
        if (!loop) return progress.coerceIn(0f, 1f)
        val wrapped = progress % 1f
        return if (wrapped < 0f) wrapped + 1f else wrapped
    }

    private fun segmentFor(
        frames: List<SkeletonKeyframeTemplate>,
        progress: Float,
        loop: Boolean,
    ): Triple<SkeletonKeyframeTemplate, SkeletonKeyframeTemplate, Float> {
        for (i in 0 until frames.lastIndex) {
            val left = frames[i]
            val right = frames[i + 1]
            if (progress in left.progress..right.progress) {
                val span = (right.progress - left.progress).coerceAtLeast(1e-5f)
                return Triple(left, right, ((progress - left.progress) / span).coerceIn(0f, 1f))
            }
        }

        if (loop) {
            val left = frames.last()
            val right = frames.first()
            val loopSpan = (1f - left.progress + right.progress).coerceAtLeast(1e-5f)
            val local = if (progress >= left.progress) {
                (progress - left.progress) / loopSpan
            } else {
                (progress + 1f - left.progress) / loopSpan
            }
            return Triple(left, right, local.coerceIn(0f, 1f))
        }

        val last = frames.last()
        return Triple(last, last, 1f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun easeInOut(t: Float): Float = (0.5f - cos((t * Math.PI)).toFloat() / 2f)

    private fun maybeMirror(
        joints: Map<String, JointPoint>,
        mirrored: Boolean,
    ): Map<String, JointPoint> {
        if (!mirrored) return joints
        return joints.mapValues { (_, point) -> JointPoint(x = 1f - point.x, y = point.y) }
    }
}
