package com.inversioncoach.app.storage.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

data class LegacyProfileSettings(
    val activeProfileName: String = "Profile 1",
    val profileNamesCsv: String = "Profile 1",
    val profileCalibrationsJson: String? = null,
    val userBodyProfileJson: String? = null,
)

object DatabaseMigrations {
    val CREATE_DRILL_MOVEMENT_PROFILES_SQL =
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

    val MIGRATION_16_17: Migration = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_profiles` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `isArchived` INTEGER NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_profiles_displayName` ON `user_profiles` (`displayName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_isActive` ON `user_profiles` (`isActive`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `profile_calibrations` (
                    `profileId` INTEGER NOT NULL,
                    `profileVersion` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL,
                    `calibrationPayloadJson` TEXT NOT NULL,
                    `appVersion` TEXT,
                    `calibrationMethod` TEXT,
                    PRIMARY KEY(`profileId`),
                    FOREIGN KEY(`profileId`) REFERENCES `user_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_profile_calibrations_profileId` ON `profile_calibrations` (`profileId`)")

            val settings = readLegacyProfileSettings(db)
            val names = settings.profileNamesCsv
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf("Profile 1") }

            val calibrationByName = decodeCalibrationMap(settings.profileCalibrationsJson)
            val activeName = settings.activeProfileName.takeIf { names.contains(it) } ?: names.first()

            val profileIds = linkedMapOf<String, Long>()
            names.forEach { name ->
                db.execSQL(
                    "INSERT INTO user_profiles(displayName, isActive, isArchived, createdAtMs, updatedAtMs) VALUES (?, ?, 0, ?, ?)",
                    arrayOf(name, if (name == activeName) 1 else 0, now, now),
                )
                val id = lastInsertedId(db)
                profileIds[name] = id
            }

            profileIds.forEach { (name, profileId) ->
                val payload = calibrationByName[name]
                if (!payload.isNullOrBlank()) {
                    db.execSQL(
                        "INSERT OR REPLACE INTO profile_calibrations(profileId, profileVersion, updatedAtMs, calibrationPayloadJson, appVersion, calibrationMethod) VALUES (?, 1, ?, ?, NULL, 'migrated_settings')",
                        arrayOf(profileId, now, payload),
                    )
                }
            }

            if (profileIds.isNotEmpty() && calibrationByName[activeName].isNullOrBlank() && !settings.userBodyProfileJson.isNullOrBlank()) {
                db.execSQL(
                    "INSERT OR REPLACE INTO profile_calibrations(profileId, profileVersion, updatedAtMs, calibrationPayloadJson, appVersion, calibrationMethod) VALUES (?, 1, ?, ?, NULL, 'migrated_legacy_user_body_profile')",
                    arrayOf(profileIds.getValue(activeName), now, settings.userBodyProfileJson),
                )
            }

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
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO user_settings_new(
                    id, cueFrequencySeconds, audioVolume, overlayIntensity, localOnlyPrivacyMode,
                    retainDays, debugOverlayEnabled, maxStorageMb, startupCountdownSeconds,
                    minSessionDurationSeconds, alignmentStrictness, customLineDeviation,
                    customMinimumGoodFormScore, customRepAcceptanceThreshold, customHoldAlignedThreshold,
                    drillCameraSideSelections
                )
                SELECT
                    id, cueFrequencySeconds, audioVolume, overlayIntensity, localOnlyPrivacyMode,
                    retainDays, debugOverlayEnabled, maxStorageMb, startupCountdownSeconds,
                    minSessionDurationSeconds, alignmentStrictness, customLineDeviation,
                    customMinimumGoodFormScore, customRepAcceptanceThreshold, customHoldAlignedThreshold,
                    drillCameraSideSelections
                FROM user_settings
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE user_settings")
            db.execSQL("ALTER TABLE user_settings_new RENAME TO user_settings")
        }

        private fun readLegacyProfileSettings(db: SupportSQLiteDatabase): LegacyProfileSettings {
            db.query(
                "SELECT activeProfileName, profileNamesCsv, profileCalibrationsJson, userBodyProfileJson FROM user_settings WHERE id = 1 LIMIT 1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) return LegacyProfileSettings()
                return LegacyProfileSettings(
                    activeProfileName = cursor.getString(0) ?: "Profile 1",
                    profileNamesCsv = cursor.getString(1) ?: "Profile 1",
                    profileCalibrationsJson = cursor.getString(2),
                    userBodyProfileJson = cursor.getString(3),
                )
            }
        }

        private fun decodeCalibrationMap(raw: String?): Map<String, String> = runCatching {
            if (raw.isNullOrBlank()) return@runCatching emptyMap()
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val payload = json.optString(key, "")
                    if (key.isNotBlank() && payload.isNotBlank()) put(key, payload)
                }
            }
        }.getOrDefault(emptyMap())

        private fun lastInsertedId(db: SupportSQLiteDatabase): Long {
            db.query("SELECT last_insert_rowid()", emptyArray()).use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
            return 0L
        }

    }

    val MIGRATION_17_18: Migration = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reference_template_records ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'REFERENCE_UPLOAD'")
            db.execSQL("ALTER TABLE reference_template_records ADD COLUMN sourceSessionId INTEGER")
            db.execSQL("ALTER TABLE reference_template_records ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE reference_template_records ADD COLUMN phasePosesJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE reference_template_records ADD COLUMN keyframesJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE reference_template_records ADD COLUMN fpsHint INTEGER")
            db.execSQL("ALTER TABLE reference_template_records ADD COLUMN durationMs INTEGER")
            db.execSQL("ALTER TABLE reference_template_records ADD COLUMN updatedAtMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE reference_template_records ADD COLUMN isBaseline INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE reference_template_records SET title = displayName WHERE title = ''")
            db.execSQL("UPDATE reference_template_records SET updatedAtMs = createdAtMs WHERE updatedAtMs = 0")

            db.execSQL("ALTER TABLE session_records ADD COLUMN drillId TEXT")
            db.execSQL("ALTER TABLE session_records ADD COLUMN referenceTemplateId TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_session_records_drillId ON session_records(drillId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_session_records_referenceTemplateId ON session_records(referenceTemplateId)")
        }
    }

    val MIGRATION_18_19: Migration = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadJobPipelineType TEXT")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadJobStatus TEXT NOT NULL DEFAULT 'IDLE'")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadJobOwnerToken TEXT")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadJobStartedAtMs INTEGER")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadJobUpdatedAtMs INTEGER")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadJobHeartbeatAtMs INTEGER")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadJobTerminalOutcome TEXT")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadJobFailureReason TEXT")
        }
    }


    val MIGRATION_19_20: Migration = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `upload_processing_jobs` (
                    `jobId` TEXT NOT NULL,
                    `sessionId` INTEGER,
                    `sourceUri` TEXT NOT NULL,
                    `trackingMode` TEXT NOT NULL,
                    `selectedDrillId` TEXT,
                    `selectedReferenceTemplateId` TEXT,
                    `isReferenceUpload` INTEGER NOT NULL,
                    `createDrillFromReferenceUpload` INTEGER NOT NULL,
                    `pendingDrillName` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `enqueueOrder` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `currentStage` TEXT NOT NULL,
                    `stageStartedAt` INTEGER,
                    `startedAt` INTEGER,
                    `completedAt` INTEGER,
                    `lastHeartbeatAt` INTEGER,
                    `lastProgressAt` INTEGER,
                    `processedFrames` INTEGER NOT NULL,
                    `totalFrames` INTEGER NOT NULL,
                    `lastTimestampMs` INTEGER,
                    `retryCount` INTEGER NOT NULL,
                    `maxRetries` INTEGER NOT NULL,
                    `failureReason` TEXT,
                    `timeoutReason` TEXT,
                    `isRecoverable` INTEGER NOT NULL,
                    `workerToken` TEXT,
                    PRIMARY KEY(`jobId`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_upload_processing_jobs_status_enqueueOrder` ON `upload_processing_jobs` (`status`, `enqueueOrder`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_upload_processing_jobs_sessionId` ON `upload_processing_jobs` (`sessionId`)")
        }
    }

    val MIGRATION_20_21: Migration = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session_records ADD COLUMN activeProcessingAttemptId TEXT")
            db.execSQL("ALTER TABLE session_records ADD COLUMN processingOwnerType TEXT")
            db.execSQL("ALTER TABLE session_records ADD COLUMN processingOwnerId TEXT")
            db.execSQL("ALTER TABLE session_records ADD COLUMN processingStartedAtMs INTEGER")
            db.execSQL("ALTER TABLE session_records ADD COLUMN processingLastProgressAtMs INTEGER")
        }
    }

    val MIGRATION_21_22: Migration = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE user_settings ADD COLUMN liveCoachingCameraFacing TEXT NOT NULL DEFAULT 'BACK'")
            db.execSQL("ALTER TABLE user_settings ADD COLUMN liveCoachingViewPreset TEXT NOT NULL DEFAULT 'FREESTYLE'")
            db.execSQL("ALTER TABLE user_settings ADD COLUMN liveCoachingZoomSelections TEXT NOT NULL DEFAULT ''")
        }
    }


    val ALL: Array<Migration> = arrayOf(
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
    )
}
