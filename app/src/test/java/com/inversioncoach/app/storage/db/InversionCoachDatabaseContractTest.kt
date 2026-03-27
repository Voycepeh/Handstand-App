package com.inversioncoach.app.storage.db

import androidx.room.Database
import com.inversioncoach.app.calibration.storage.CalibrationEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class InversionCoachDatabaseContractTest {
    @Test
    fun databaseRegistersCalibrationEntityAndVersion() {
        val annotation = InversionCoachDatabase::class.java.getAnnotation(Database::class.java)
        val entities = annotation.entities.toSet()

        assertTrue("CalibrationEntity must be registered in Room database entities", entities.contains(CalibrationEntity::class.java))
        assertTrue("Database version must include calibration migration", annotation.version >= 14)
    }

    @Test
    fun databaseExposesCalibrationDao() {
        val hasCalibrationDao = InversionCoachDatabase::class.java.methods.any { method ->
            method.name == "calibrationDao" && method.returnType == com.inversioncoach.app.calibration.storage.CalibrationDao::class.java
        }

        assertTrue("InversionCoachDatabase must expose calibrationDao()", hasCalibrationDao)
    }
}
