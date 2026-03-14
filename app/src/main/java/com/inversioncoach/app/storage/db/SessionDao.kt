package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.SessionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sessionRecord: SessionRecord): Long

    @Query("SELECT * FROM session_records ORDER BY startedAtMs DESC")
    fun observeAll(): Flow<List<SessionRecord>>

    @Query("SELECT * FROM session_records WHERE drillType = :drillType ORDER BY startedAtMs DESC")
    fun observeByDrill(drillType: DrillType): Flow<List<SessionRecord>>

    @Query("SELECT * FROM session_records WHERE id = :sessionId LIMIT 1")
    fun observeById(sessionId: Long): Flow<SessionRecord?>

    @Query("DELETE FROM session_records")
    suspend fun deleteAll()
}
