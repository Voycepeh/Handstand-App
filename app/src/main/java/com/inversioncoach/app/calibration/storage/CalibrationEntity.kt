package com.inversioncoach.app.calibration.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.inversioncoach.app.model.DrillType

@Entity(tableName = "drill_movement_profiles")
data class CalibrationEntity(
    @PrimaryKey val drillType: DrillType,
    val profileVersion: Int,
    val payloadJson: String,
    val updatedAtMs: Long,
)
