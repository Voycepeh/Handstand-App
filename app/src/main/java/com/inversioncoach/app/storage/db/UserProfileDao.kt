package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.UserProfileRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile_records WHERE isArchived = 0 ORDER BY createdAtMs ASC")
    fun observeAvailableProfiles(): Flow<List<UserProfileRecord>>

    @Query("SELECT * FROM user_profile_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserProfileRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: UserProfileRecord)

    @Query("UPDATE user_profile_records SET displayName = :displayName, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun rename(id: String, displayName: String, updatedAtMs: Long)

    @Query("UPDATE user_profile_records SET isArchived = 1, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun archive(id: String, updatedAtMs: Long)

    @Query("SELECT COUNT(*) FROM user_profile_records WHERE isArchived = 0")
    suspend fun countAvailableProfiles(): Int
}
