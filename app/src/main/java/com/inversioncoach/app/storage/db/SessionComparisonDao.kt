package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.SessionComparisonRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionComparisonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SessionComparisonRecord): Long

    @Query("SELECT * FROM session_comparison_records WHERE sessionId = :sessionId ORDER BY createdAtMs DESC LIMIT 1")
    fun observeLatestForSession(sessionId: Long): Flow<SessionComparisonRecord?>

    @Query("SELECT * FROM session_comparison_records WHERE sessionId = :sessionId ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getLatestForSession(sessionId: Long): SessionComparisonRecord?

    @Query("SELECT * FROM session_comparison_records WHERE sessionId = :sessionId ORDER BY createdAtMs DESC")
    fun observeAllForSession(sessionId: Long): Flow<List<SessionComparisonRecord>>

    @Query("SELECT * FROM session_comparison_records WHERE drillId = :drillId ORDER BY createdAtMs DESC")
    fun observeByDrill(drillId: String): Flow<List<SessionComparisonRecord>>

    @Query("SELECT DISTINCT sessionId FROM session_comparison_records WHERE sessionId IS NOT NULL")
    fun observeComparedSessionIds(): Flow<List<Long>>

    @Query("SELECT * FROM session_comparison_records ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<SessionComparisonRecord>>

    @Query("DELETE FROM session_comparison_records WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long)

    @Query("DELETE FROM session_comparison_records")
    suspend fun deleteAll()
}
