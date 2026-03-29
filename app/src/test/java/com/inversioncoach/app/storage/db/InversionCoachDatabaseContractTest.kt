package com.inversioncoach.app.storage.db

import androidx.room.Database
import com.inversioncoach.app.calibration.storage.CalibrationEntity
import com.inversioncoach.app.model.CalibrationConfigRecord
import com.inversioncoach.app.model.BodyProfileRecord
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.model.MovementProfileRecord
import com.inversioncoach.app.model.ReferenceAssetRecord
import com.inversioncoach.app.model.ReferenceTemplateRecord
import com.inversioncoach.app.model.SessionComparisonRecord
import com.inversioncoach.app.model.UserProfileRecord
import org.junit.Assert.assertTrue
import org.junit.Test

class InversionCoachDatabaseContractTest {
    @Test
    fun databaseRegistersCalibrationEntityAndVersion() {
        val annotation = InversionCoachDatabase::class.java.getAnnotation(Database::class.java)
        val entities = annotation.entities.toSet()

        assertTrue("CalibrationEntity must be registered in Room database entities", entities.contains(CalibrationEntity::class.java))
        assertTrue("ReferenceTemplateRecord must be registered in Room database entities", entities.contains(ReferenceTemplateRecord::class.java))
        assertTrue("SessionComparisonRecord must be registered in Room database entities", entities.contains(SessionComparisonRecord::class.java))
        assertTrue("DrillDefinitionRecord must be registered", entities.contains(DrillDefinitionRecord::class.java))
        assertTrue("ReferenceAssetRecord must be registered", entities.contains(ReferenceAssetRecord::class.java))
        assertTrue("MovementProfileRecord must be registered", entities.contains(MovementProfileRecord::class.java))
        assertTrue("CalibrationConfigRecord must be registered", entities.contains(CalibrationConfigRecord::class.java))
        assertTrue("UserProfileRecord must be registered", entities.contains(UserProfileRecord::class.java))
        assertTrue("BodyProfileRecord must be registered", entities.contains(BodyProfileRecord::class.java))
        assertTrue("Database version must include body profile migrations", annotation.version >= 17)
    }

    @Test
    fun databaseExposesCalibrationDao() {
        val hasCalibrationDao = InversionCoachDatabase::class.java.methods.any { method ->
            method.name == "calibrationDao" && method.returnType == com.inversioncoach.app.calibration.storage.CalibrationDao::class.java
        }

        assertTrue("InversionCoachDatabase must expose calibrationDao()", hasCalibrationDao)
    }

    @Test
    fun databaseExposesReferenceAndComparisonDaos() {
        val methods = InversionCoachDatabase::class.java.methods
        val hasDrillDao = methods.any { it.name == "drillDefinitionDao" && it.returnType == DrillDefinitionDao::class.java }
        val hasReferenceAssetDao = methods.any { it.name == "referenceAssetDao" && it.returnType == ReferenceAssetDao::class.java }
        val hasMovementProfileDao = methods.any { it.name == "movementProfileDao" && it.returnType == MovementProfileDao::class.java }
        val hasCalibrationConfigDao = methods.any { it.name == "calibrationConfigDao" && it.returnType == CalibrationConfigDao::class.java }
        val hasReferenceDao = methods.any { method ->
            method.name == "referenceTemplateDao" && method.returnType == ReferenceTemplateDao::class.java
        }
        val hasComparisonDao = methods.any { method ->
            method.name == "sessionComparisonDao" && method.returnType == SessionComparisonDao::class.java
        }
        val hasUserProfileDao = methods.any { it.name == "userProfileDao" && it.returnType == UserProfileDao::class.java }
        val hasBodyProfileDao = methods.any { it.name == "bodyProfileDao" && it.returnType == BodyProfileDao::class.java }

        assertTrue("InversionCoachDatabase must expose drillDefinitionDao()", hasDrillDao)
        assertTrue("InversionCoachDatabase must expose referenceAssetDao()", hasReferenceAssetDao)
        assertTrue("InversionCoachDatabase must expose movementProfileDao()", hasMovementProfileDao)
        assertTrue("InversionCoachDatabase must expose calibrationConfigDao()", hasCalibrationConfigDao)
        assertTrue("InversionCoachDatabase must expose referenceTemplateDao()", hasReferenceDao)
        assertTrue("InversionCoachDatabase must expose sessionComparisonDao()", hasComparisonDao)
        assertTrue("InversionCoachDatabase must expose userProfileDao()", hasUserProfileDao)
        assertTrue("InversionCoachDatabase must expose bodyProfileDao()", hasBodyProfileDao)
    }
}
