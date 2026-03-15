package com.inversioncoach.app.biomechanics

import com.inversioncoach.app.model.JointPoint
import kotlin.math.abs

class CommonMetricsCalculator {

    fun calculate(pose: NormalizedPose, phaseTempo: Map<String, Float>, pathMetrics: Map<String, Float>): DerivedMetrics {
        val side = pose.dominantSide.name.lowercase()
        val j = pose.joints
        val m = pose.midpoints
        val shoulder = m["shoulder_mid"] ?: j["${side}_shoulder"]
        val hip = m["hip_mid"] ?: j["${side}_hip"]
        val knee = m["knee_mid"] ?: j["${side}_knee"]
        val ankle = m["ankle_mid"] ?: j["${side}_ankle"]
        val wrist = m["wrist_mid"] ?: j["${side}_wrist"]
        val elbow = j["${side}_elbow"]
        val ear = j["${side}_ear"] ?: j["nose"]

        val jointAngles = mapOf(
            "elbow_angle" to LandmarkMath.angle(shoulder, elbow, wrist),
            "shoulder_angle_proxy" to LandmarkMath.angle(hip, shoulder, elbow),
            "hip_angle" to LandmarkMath.angle(shoulder, hip, knee),
            "knee_angle" to LandmarkMath.angle(hip, knee, ankle),
        )

        val segmentVertical = mapOf(
            "wrist_to_shoulder" to LandmarkMath.segmentVerticalDeviationDegrees(wrist, shoulder),
            "shoulder_to_hip" to LandmarkMath.segmentVerticalDeviationDegrees(shoulder, hip),
            "hip_to_knee" to LandmarkMath.segmentVerticalDeviationDegrees(hip, knee),
            "knee_to_ankle" to LandmarkMath.segmentVerticalDeviationDegrees(knee, ankle),
        )

        val stackLineX = wrist?.x ?: shoulder?.x ?: 0.5f
        val offsets = mapOf(
            "shoulder_stack_offset" to normalizeX(shoulder, stackLineX, pose.torsoLength),
            "hip_stack_offset" to normalizeX(hip, stackLineX, pose.torsoLength),
            "knee_stack_offset" to normalizeX(knee, stackLineX, pose.torsoLength),
            "ankle_stack_offset" to normalizeX(ankle, stackLineX, pose.torsoLength),
        )

        val bodyLineDeviation = offsets.values.map { abs(it) }.average().toFloat()
        val kneeScore = scoreKneeExtension(jointAngles["knee_angle"] ?: 180f)
        val bananaScore = scoreBananaCurve(offsets)
        val pelvicScore = scorePelvicControl(offsets)
        val shoulderOpenScore = scoreShoulderOpenness(jointAngles["shoulder_angle_proxy"] ?: 0f)
        val scapScore = scoreScapularElevation(ear, shoulder, pose.torsoLength)

        return DerivedMetrics(
            timestampMs = pose.timestampMs,
            jointAngles = jointAngles,
            segmentVerticalDeviation = segmentVertical,
            stackOffsetsNorm = offsets,
            bodyLineDeviationNorm = bodyLineDeviation,
            kneeExtensionScore = kneeScore,
            bananaProxyScore = bananaScore,
            pelvicControlProxyScore = pelvicScore,
            shoulderOpennessScore = shoulderOpenScore,
            scapularElevationProxyScore = scapScore,
            tempoMetrics = phaseTempo,
            pathMetrics = pathMetrics,
            confidenceLevel = pose.confidenceLevel,
            confidence = pose.confidence,
        )
    }

    private fun normalizeX(point: JointPoint?, referenceX: Float, torsoLength: Float): Float {
        if (point == null) return 0f
        return LandmarkMath.normalizeByTorsoLength(LandmarkMath.signedHorizontalOffset(referenceX, point.x), torsoLength)
    }

    private fun scoreKneeExtension(kneeAngle: Float): Int = when {
        kneeAngle >= 175f -> 100
        kneeAngle >= 165f -> 80
        kneeAngle >= 155f -> 55
        else -> 25
    }

    private fun scoreBananaCurve(offsets: Map<String, Float>): Int {
        val shoulder = abs(offsets["shoulder_stack_offset"] ?: 0f)
        val hip = abs(offsets["hip_stack_offset"] ?: 0f)
        val ankle = abs(offsets["ankle_stack_offset"] ?: 0f)
        val curveDelta = (hip - ((shoulder + ankle) / 2f)).coerceAtLeast(0f)
        return when {
            curveDelta <= 0.03f -> 25
            curveDelta <= 0.07f -> 55
            curveDelta <= 0.12f -> 80
            else -> 100
        }
    }

    private fun scorePelvicControl(offsets: Map<String, Float>): Int {
        val shoulder = offsets["shoulder_stack_offset"] ?: 0f
        val hip = offsets["hip_stack_offset"] ?: 0f
        val pelvicOffsetDelta = abs(hip - shoulder)
        return when {
            pelvicOffsetDelta <= 0.03f -> 100
            pelvicOffsetDelta <= 0.07f -> 80
            pelvicOffsetDelta <= 0.12f -> 55
            else -> 25
        }
    }

    private fun scoreShoulderOpenness(shoulderAngle: Float): Int = when {
        shoulderAngle in 175f..185f -> 100
        shoulderAngle >= 165f -> 80
        shoulderAngle >= 155f -> 55
        else -> 25
    }

    private fun scoreScapularElevation(ear: JointPoint?, shoulder: JointPoint?, torso: Float): Int {
        if (ear == null || shoulder == null) return 55
        val earShoulderDistanceNorm = LandmarkMath.normalizeByTorsoLength(LandmarkMath.verticalDistance(ear, shoulder), torso)
        return when {
            earShoulderDistanceNorm <= 0.12f -> 100
            earShoulderDistanceNorm <= 0.20f -> 80
            earShoulderDistanceNorm <= 0.30f -> 55
            else -> 25
        }
    }
}
