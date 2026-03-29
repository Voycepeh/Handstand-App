package com.inversioncoach.app.storage.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    const val CREATE_DRILL_MOVEMENT_PROFILES_SQL =
        """
        CREATE TABLE IF NOT EXISTS `drill_movement_profiles` (
            `drillType` TEXT NOT NULL,
            `profileVersion` INTEGER NOT NULL,
            `payloadJson` TEXT NOT NULL,
            `updatedAtMs` INTEGER NOT NULL,
            PRIMARY KEY(`drillType`)
        )
        """.trimIndent()

    const val ADD_SESSION_CALIBRATION_PROFILE_VERSION_SQL =
        "ALTER TABLE session_records ADD COLUMN calibrationProfileVersion INTEGER"
    const val ADD_SESSION_CALIBRATION_UPDATED_AT_SQL =
        "ALTER TABLE session_records ADD COLUMN calibrationUpdatedAtMs INTEGER"

    val MIGRATION_11_12: Migration = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE user_settings ADD COLUMN startupCountdownSeconds INTEGER NOT NULL DEFAULT 10",
            )
        }
    }

    val MIGRATION_12_13: Migration = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadPipelineStageLabel TEXT")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadAnalysisProcessedFrames INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadAnalysisTotalFrames INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadAnalysisTimestampMs INTEGER")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadProgressDetail TEXT")
        }
    }

    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_DRILL_MOVEMENT_PROFILES_SQL)
            db.execSQL(ADD_SESSION_CALIBRATION_PROFILE_VERSION_SQL)
            db.execSQL(ADD_SESSION_CALIBRATION_UPDATED_AT_SQL)
        }
    }



    val MIGRATION_14_15: Migration = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_settings_new` (
                    `id` INTEGER NOT NULL,
                    `cueFrequencySeconds` REAL NOT NULL,
                    `audioVolume` REAL NOT NULL,
                    `overlayIntensity` REAL NOT NULL,
                    `localOnlyPrivacyMode` INTEGER NOT NULL,
                    `retainDays` INTEGER NOT NULL,
                    `debugOverlayEnabled` INTEGER NOT NULL,
                    `maxStorageMb` INTEGER NOT NULL,
                    `startupCountdownSeconds` INTEGER NOT NULL,
                    `minSessionDurationSeconds` INTEGER NOT NULL,
                    `alignmentStrictness` TEXT NOT NULL,
                    `customLineDeviation` REAL NOT NULL,
                    `customMinimumGoodFormScore` INTEGER NOT NULL,
                    `customRepAcceptanceThreshold` INTEGER NOT NULL,
                    `customHoldAlignedThreshold` INTEGER NOT NULL,
                    `drillCameraSideSelections` TEXT NOT NULL,
                    `userBodyProfileJson` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO user_settings_new (
                    id, cueFrequencySeconds, audioVolume, overlayIntensity, localOnlyPrivacyMode,
                    retainDays, debugOverlayEnabled, maxStorageMb, startupCountdownSeconds,
                    minSessionDurationSeconds, alignmentStrictness, customLineDeviation,
                    customMinimumGoodFormScore, customRepAcceptanceThreshold, customHoldAlignedThreshold,
                    drillCameraSideSelections, userBodyProfileJson
                )
                SELECT
                    id, cueFrequencySeconds, audioVolume, overlayIntensity, localOnlyPrivacyMode,
                    retainDays, debugOverlayEnabled, maxStorageMb, startupCountdownSeconds,
                    minSessionDurationSeconds, alignmentStrictness, customLineDeviation,
                    customMinimumGoodFormScore, customRepAcceptanceThreshold, customHoldAlignedThreshold,
                    drillCameraSideSelections, userBodyProfileJson
                FROM user_settings
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE user_settings")
            db.execSQL("ALTER TABLE user_settings_new RENAME TO user_settings")
        }
    }



    val MIGRATION_15_16: Migration = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE user_settings ADD COLUMN activeProfileName TEXT NOT NULL DEFAULT 'Profile 1'")
            db.execSQL("ALTER TABLE user_settings ADD COLUMN profileNamesCsv TEXT NOT NULL DEFAULT 'Profile 1'")
            db.execSQL("ALTER TABLE user_settings ADD COLUMN profileCalibrationsJson TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
    )
}
