package com.inversioncoach.app.calibration

import kotlin.math.abs

class CalibrationEngine {
    fun buildProfile(session: CalibrationSession): UserBodyProfile? {
        val captures = session.captures().values.toList()
        if (captures.size < CalibrationStep.entries.size) return null
        fun avg(selector: (CalibrationCapture) -> Float): Float = captures.map(selector).average().toFloat()
        val shoulder = avg { it.shoulderWidth }
        val hip = avg { it.hipWidth }
        val torso = avg { it.torsoLength }
        val upperArm = avg { it.upperArmLength }
        val forearm = avg { it.forearmLength }
        val femur = avg { it.femurLength }
        val shin = avg { it.shinLength }
        val baseline = listOf(shoulder, hip, torso, upperArm, forearm, femur, shin).maxOrNull()?.takeIf { it > 0f } ?: return null
        return UserBodyProfile(
            shoulderWidthNormalized = shoulder / baseline,
            hipWidthNormalized = hip / baseline,
            torsoLengthNormalized = torso / baseline,
            upperArmLengthNormalized = upperArm / baseline,
            forearmLengthNormalized = forearm / baseline,
            femurLengthNormalized = femur / baseline,
            shinLengthNormalized = shin / baseline,
            leftRightConsistency = 1f - abs(upperArm - forearm) / baseline,
        )
    }
}
