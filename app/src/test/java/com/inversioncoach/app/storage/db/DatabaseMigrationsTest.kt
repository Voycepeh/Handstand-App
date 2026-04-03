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
    fun includesMigrationFrom14To15() {
        val has14To15 = DatabaseMigrations.ALL.any { migration ->
            migration.startVersion == 14 && migration.endVersion == 15
        }

        assertTrue("DatabaseMigrations.ALL must contain Migration(14, 15)", has14To15)
    }

    @Test
    fun includesMigrationFrom15To16() {
        val has15To16 = DatabaseMigrations.ALL.any { migration ->
            migration.startVersion == 15 && migration.endVersion == 16
        }

        assertTrue("DatabaseMigrations.ALL must contain Migration(15, 16)", has15To16)
    }

    @Test
    fun includesMigrationFrom16To17() {
        val has16To17 = DatabaseMigrations.ALL.any { migration ->
            migration.startVersion == 16 && migration.endVersion == 17
        }

        assertTrue("DatabaseMigrations.ALL must contain Migration(16, 17)", has16To17)
    }

    @Test
    fun includesMigrationFrom19To20() {
        val has19To20 = DatabaseMigrations.ALL.any { migration ->
            migration.startVersion == 19 && migration.endVersion == 20
        }

        assertTrue("DatabaseMigrations.ALL must contain Migration(19, 20)", has19To20)
    }

    @Test
    fun includesMigrationFrom20To21() {
        val has20To21 = DatabaseMigrations.ALL.any { migration ->
            migration.startVersion == 20 && migration.endVersion == 21
        }

        assertTrue("DatabaseMigrations.ALL must contain Migration(20, 21)", has20To21)
    }

    @Test
    fun migrationsAreUniqueAndContiguous() {
        val pairs = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals("Migration pairs should be unique to avoid merge-conflict drift", pairs.toSet().size, pairs.size)

        val expected = (11..20).map { it to it + 1 }
        assertEquals("Migrations should cover every version hop from 11 to 21", expected, pairs)
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
