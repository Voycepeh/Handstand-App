package com.inversioncoach.app.calibration

data class UserBodyProfile(
    val version: Int = 1,
    val shoulderWidthNormalized: Float,
    val hipWidthNormalized: Float,
    val torsoLengthNormalized: Float,
    val upperArmLengthNormalized: Float,
    val forearmLengthNormalized: Float,
    val femurLengthNormalized: Float,
    val shinLengthNormalized: Float,
    val leftRightConsistency: Float,
) {
    fun encode(): String = listOf(
        version,
        shoulderWidthNormalized,
        hipWidthNormalized,
        torsoLengthNormalized,
        upperArmLengthNormalized,
        forearmLengthNormalized,
        femurLengthNormalized,
        shinLengthNormalized,
        leftRightConsistency,
    ).joinToString("|")

    companion object {
        fun decode(raw: String?): UserBodyProfile? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split('|')
            if (parts.size != 9) return null
            return runCatching {
                UserBodyProfile(
                    version = parts[0].toInt(),
                    shoulderWidthNormalized = parts[1].toFloat(),
                    hipWidthNormalized = parts[2].toFloat(),
                    torsoLengthNormalized = parts[3].toFloat(),
                    upperArmLengthNormalized = parts[4].toFloat(),
                    forearmLengthNormalized = parts[5].toFloat(),
                    femurLengthNormalized = parts[6].toFloat(),
                    shinLengthNormalized = parts[7].toFloat(),
                    leftRightConsistency = parts[8].toFloat(),
                )
            }.getOrNull()
        }
    }
}
