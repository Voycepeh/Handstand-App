package com.inversioncoach.app.calibration.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.DrillType

@Dao
interface CalibrationDao {
    @Query("SELECT * FROM drill_movement_profiles WHERE drillType = :drillType LIMIT 1")
    suspend fun get(drillType: DrillType): CalibrationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CalibrationEntity)

    @Query("DELETE FROM drill_movement_profiles WHERE drillType = :drillType")
    suspend fun delete(drillType: DrillType)
}
