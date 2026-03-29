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
        val settings = userSettingsDao.getSettings()
        val profiles = listAvailableProfiles()
        val active = settings?.activeUserProfileId?.let { requestedId ->
            profiles.firstOrNull { it.id == requestedId }
        }
        if (active != null) {
            maybeMigrateLegacyBodyProfile(active)
            return active
        }
        val fallback = profiles.firstOrNull() ?: createProfile("Primary User")
        userSettingsDao.upsert((settings ?: com.inversioncoach.app.model.UserSettings()).copy(activeUserProfileId = fallback.id))
        maybeMigrateLegacyBodyProfile(fallback)
        return fallback
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
        val settings = userSettingsDao.getSettings() ?: com.inversioncoach.app.model.UserSettings()
        userSettingsDao.upsert(settings.copy(activeUserProfileId = profileId))
    }

    suspend fun renameProfile(profileId: String, newName: String) {
        val normalized = newName.trim().ifBlank { return }
        userProfileDao.rename(profileId, normalized, System.currentTimeMillis())
    }

    suspend fun archiveProfile(profileId: String): Boolean {
        if (userProfileDao.countAvailableProfiles() <= 1) return false
        val settings = userSettingsDao.getSettings() ?: com.inversioncoach.app.model.UserSettings()
        userProfileDao.archive(profileId, System.currentTimeMillis())
        if (settings.activeUserProfileId == profileId) {
            val fallback = listAvailableProfiles().firstOrNull { it.id != profileId }
            userSettingsDao.upsert(settings.copy(activeUserProfileId = fallback?.id))
        }
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

    suspend fun getLegacyBodyProfileFallback(): UserBodyProfile? {
        val settings = userSettingsDao.getSettings() ?: return null
        return UserBodyProfile.decode(settings.userBodyProfileJson)
    }

    private suspend fun maybeMigrateLegacyBodyProfile(activeProfile: UserProfileRecord) {
        val latest = bodyProfileDao.getLatestForUser(activeProfile.id)
        if (latest != null) return
        val settings = userSettingsDao.getSettings() ?: return
        val legacy = UserBodyProfile.decode(settings.userBodyProfileJson) ?: return
        val now = System.currentTimeMillis()
        bodyProfileDao.upsert(
            BodyProfileRecord(
                id = "body_${UUID.randomUUID()}",
                userProfileId = activeProfile.id,
                version = 1,
                payloadJson = legacy.encode(),
                createdAtMs = now,
                updatedAtMs = now,
            ),
        )
        userSettingsDao.upsert(settings.copy(userBodyProfileJson = null))
    }
}
