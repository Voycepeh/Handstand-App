package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.ReferenceTemplateRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ReferenceTemplateRecord)

    @Query("SELECT * FROM reference_template_records ORDER BY displayName ASC")
    fun observeAll(): Flow<List<ReferenceTemplateRecord>>

    @Query("SELECT * FROM reference_template_records WHERE drillId = :drillId ORDER BY createdAtMs DESC")
    fun observeByDrillId(drillId: String): Flow<List<ReferenceTemplateRecord>>

    @Query("SELECT * FROM reference_template_records WHERE id = :templateId LIMIT 1")
    suspend fun getById(templateId: String): ReferenceTemplateRecord?
}
