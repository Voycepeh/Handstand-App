package com.inversioncoach.app.calibration

data class RuntimeBodyProfileResolution(
    val userProfileId: String?,
    val bodyProfileId: String?,
    val bodyProfileVersion: Int?,
    val bodyProfile: UserBodyProfile?,
    val usedDefaultBodyModel: Boolean,
)

class RuntimeBodyProfileResolver(
    private val userProfileManager: UserProfileManager?,
) {
    suspend fun resolve(): RuntimeBodyProfileResolution {
        val manager = userProfileManager ?: return RuntimeBodyProfileResolution(
            userProfileId = null,
            bodyProfileId = null,
            bodyProfileVersion = null,
            bodyProfile = null,
            usedDefaultBodyModel = true,
        )
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
}
