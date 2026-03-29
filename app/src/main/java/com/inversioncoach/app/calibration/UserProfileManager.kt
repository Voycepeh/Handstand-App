package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.BodyProfileRecord
import com.inversioncoach.app.model.UserProfileRecord
import com.inversioncoach.app.storage.db.BodyProfileDao
import com.inversioncoach.app.storage.db.UserProfileDao
import com.inversioncoach.app.storage.db.UserSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

data class ActiveProfileContext(
    val userProfile: UserProfileRecord,
    val bodyProfileRecord: BodyProfileRecord?,
    val bodyProfile: UserBodyProfile?,
)

class UserProfileManager(
    private val userProfileDao: UserProfileDao,
    private val bodyProfileDao: BodyProfileDao,
    private val userSettingsDao: UserSettingsDao,
) {
    fun observeAvailableProfiles(): Flow<List<UserProfileRecord>> = userProfileDao.observeAvailableProfiles()

    suspend fun listAvailableProfiles(): List<UserProfileRecord> = observeAvailableProfiles().firstOrNull().orEmpty()

    suspend fun getOrCreateActiveProfile(): UserProfileRecord {
        val profiles = listAvailableProfiles()
        return profiles.firstOrNull() ?: createProfile("Primary User")
    }

    suspend fun createProfile(name: String): UserProfileRecord {
        val now = System.currentTimeMillis()
        val normalized = name.trim().ifBlank { "User" }
        val record = UserProfileRecord(
            id = "user_${UUID.randomUUID()}",
            displayName = normalized,
            createdAtMs = now,
            updatedAtMs = now,
            isArchived = false,
        )
        userProfileDao.upsert(record)
        return record
    }

    suspend fun setActiveProfile(profileId: String) {
        // Active-profile persistence moved to profile-based storage; no-op compatibility shim.
    }

    suspend fun renameProfile(profileId: String, newName: String) {
        val normalized = newName.trim().ifBlank { return }
        userProfileDao.rename(profileId, normalized, System.currentTimeMillis())
    }

    suspend fun archiveProfile(profileId: String): Boolean {
        if (userProfileDao.countAvailableProfiles() <= 1) return false
        userProfileDao.archive(profileId, System.currentTimeMillis())
        return true
    }

    suspend fun resolveActiveProfileContext(): ActiveProfileContext {
        val activeProfile = getOrCreateActiveProfile()
        val bodyRecord = bodyProfileDao.getLatestForUser(activeProfile.id)
        return ActiveProfileContext(
            userProfile = activeProfile,
            bodyProfileRecord = bodyRecord,
            bodyProfile = UserBodyProfile.decode(bodyRecord?.payloadJson),
        )
    }

    suspend fun saveBodyProfileForActiveUser(profile: UserBodyProfile): BodyProfileRecord {
        val activeProfile = getOrCreateActiveProfile()
        val latest = bodyProfileDao.getLatestForUser(activeProfile.id)
        val now = System.currentTimeMillis()
        val record = BodyProfileRecord(
            id = "body_${UUID.randomUUID()}",
            userProfileId = activeProfile.id,
            version = (latest?.version ?: 0) + 1,
            payloadJson = profile.encode(),
            createdAtMs = now,
            updatedAtMs = now,
        )
        bodyProfileDao.upsert(record)
        return record
    }

    suspend fun clearBodyProfileForActiveUser() {
        val active = getOrCreateActiveProfile()
        bodyProfileDao.deleteForUser(active.id)
    }

    suspend fun getLegacyBodyProfileFallback(): UserBodyProfile? = null
}
