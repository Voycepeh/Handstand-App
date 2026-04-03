package com.inversioncoach.app.calibration

import com.inversioncoach.app.storage.repository.SessionRepository

data class RuntimeBodyProfileResolution(
    val userProfileId: String?,
    val bodyProfileId: String?,
    val bodyProfileVersion: Int?,
    val bodyProfile: UserBodyProfile?,
    val usedDefaultBodyModel: Boolean,
)

class RuntimeBodyProfileResolver(
    private val sessionRepository: SessionRepository,
) {
    suspend fun resolve(): RuntimeBodyProfileResolution {
        val activeProfile = sessionRepository.getActiveProfile()
        val activeCalibration = UserBodyProfile.normalize(sessionRepository.getActiveProfileCalibration())
        val activeCalibrationVersion = sessionRepository.getActiveProfileCalibrationVersion()
        return RuntimeBodyProfileResolution(
            userProfileId = activeProfile?.id?.toString(),
            bodyProfileId = null,
            bodyProfileVersion = activeCalibrationVersion,
            bodyProfile = activeCalibration,
            usedDefaultBodyModel = activeCalibration == null,
        )
    }
}
