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
    private val userProfileManager: UserProfileManager?,
    private val sessionRepository: SessionRepository? = null,
) {
    suspend fun resolve(): RuntimeBodyProfileResolution {
        val manager = userProfileManager
        if (manager != null) {
            val active = manager.resolveActiveProfileContext()
            val body = active.bodyProfile ?: manager.getLegacyBodyProfileFallback()
            return RuntimeBodyProfileResolution(
                userProfileId = active.userProfile.id,
                bodyProfileId = active.bodyProfileRecord?.id,
                bodyProfileVersion = active.bodyProfileRecord?.version,
                bodyProfile = body,
                usedDefaultBodyModel = body == null,
            )
        }

        val repository = sessionRepository
        if (repository != null) {
            val activeProfile = repository.getActiveProfile()
            val activeCalibration = repository.getActiveProfileCalibration()
            val activeCalibrationVersion = repository.getActiveProfileCalibrationVersion()
            return RuntimeBodyProfileResolution(
                userProfileId = activeProfile?.id?.toString(),
                bodyProfileId = null,
                bodyProfileVersion = activeCalibrationVersion,
                bodyProfile = activeCalibration,
                usedDefaultBodyModel = activeCalibration == null,
            )
        }

        return RuntimeBodyProfileResolution(
            userProfileId = null,
            bodyProfileId = null,
            bodyProfileVersion = null,
            bodyProfile = null,
            usedDefaultBodyModel = true,
        )
    }
}
