package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.BodyProfileRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyProfileDao {
    @Query("SELECT * FROM body_profile_records WHERE userProfileId = :userProfileId ORDER BY updatedAtMs DESC LIMIT 1")
    suspend fun getLatestForUser(userProfileId: String): BodyProfileRecord?

    @Query("SELECT * FROM body_profile_records WHERE userProfileId = :userProfileId ORDER BY updatedAtMs DESC LIMIT 1")
    fun observeLatestForUser(userProfileId: String): Flow<BodyProfileRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BodyProfileRecord)

    @Query("DELETE FROM body_profile_records WHERE userProfileId = :userProfileId")
    suspend fun deleteForUser(userProfileId: String)
}
