package com.inversioncoach.app.storage.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationsTest {
    @Test
    fun includesMigrationFrom13To14() {
        val has13To14 = DatabaseMigrations.ALL.any { migration ->
            migration.startVersion == 13 && migration.endVersion == 14
        }

        assertTrue("DatabaseMigrations.ALL must contain Migration(13, 14)", has13To14)
    }

    @Test
    fun migrationSqlForCalibrationTableAndSessionColumnsIsDefined() {
        assertTrue(DatabaseMigrations.CREATE_DRILL_MOVEMENT_PROFILES_SQL.contains("CREATE TABLE IF NOT EXISTS `drill_movement_profiles`"))
        assertEquals(
            "ALTER TABLE session_records ADD COLUMN calibrationProfileVersion INTEGER",
            DatabaseMigrations.ADD_SESSION_CALIBRATION_PROFILE_VERSION_SQL,
        )
        assertEquals(
            "ALTER TABLE session_records ADD COLUMN calibrationUpdatedAtMs INTEGER",
            DatabaseMigrations.ADD_SESSION_CALIBRATION_UPDATED_AT_SQL,
        )
    }
}
