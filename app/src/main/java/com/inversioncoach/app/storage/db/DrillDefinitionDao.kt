package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.DrillDefinitionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DrillDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DrillDefinitionRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<DrillDefinitionRecord>)

    @Query("SELECT * FROM drill_definition_records ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<DrillDefinitionRecord>>

    @Query("SELECT * FROM drill_definition_records WHERE status = 'READY' ORDER BY updatedAtMs DESC")
    fun observeActive(): Flow<List<DrillDefinitionRecord>>

    @Query("SELECT * FROM drill_definition_records WHERE id = :drillId LIMIT 1")
    suspend fun getById(drillId: String): DrillDefinitionRecord?

    @Query("DELETE FROM drill_definition_records WHERE id = :drillId")
    suspend fun deleteById(drillId: String)
}
