package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.CalibrationConfigRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CalibrationConfigRecord)

    @Query("SELECT * FROM calibration_config_records WHERE drillId = :drillId ORDER BY updatedAtMs DESC")
    fun observeByDrill(drillId: String): Flow<List<CalibrationConfigRecord>>

    @Query("SELECT * FROM calibration_config_records WHERE drillId = :drillId AND isActive = 1 LIMIT 1")
    suspend fun getActiveForDrill(drillId: String): CalibrationConfigRecord?
}
