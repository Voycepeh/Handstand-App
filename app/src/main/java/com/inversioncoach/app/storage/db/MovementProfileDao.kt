package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.MovementProfileRecord

@Dao
interface MovementProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MovementProfileRecord)

    @Query("SELECT * FROM movement_profile_records WHERE id = :profileId LIMIT 1")
    suspend fun getById(profileId: String): MovementProfileRecord?

    @Query("SELECT * FROM movement_profile_records WHERE drillId = :drillId ORDER BY createdAtMs DESC")
    suspend fun getByDrill(drillId: String): List<MovementProfileRecord>
}
