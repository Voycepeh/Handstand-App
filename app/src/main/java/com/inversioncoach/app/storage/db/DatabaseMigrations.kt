package com.inversioncoach.app.storage.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
                CREATE TABLE IF NOT EXISTS `reference_template_records` (
                    `id` TEXT NOT NULL,
                    `drillType` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `assetPath` TEXT NOT NULL,
                    `phaseOrderJson` TEXT NOT NULL,
                    `isBuiltIn` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `session_comparison_records` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sessionId` INTEGER NOT NULL,
                    `referenceTemplateId` TEXT NOT NULL,
                    `overallSimilarityScore` INTEGER NOT NULL,
                    `timingScore` INTEGER NOT NULL,
                    `alignmentScore` INTEGER NOT NULL,
                    `stabilityScore` INTEGER NOT NULL,
                    `phaseScoresJson` TEXT NOT NULL,
                    `topDifferencesJson` TEXT NOT NULL,
                    `comparedAtMs` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_15_16: Migration = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `drill_definition_records` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `movementMode` TEXT NOT NULL,
                    `cameraView` TEXT NOT NULL,
                    `phaseSchemaJson` TEXT NOT NULL,
                    `keyJointsJson` TEXT NOT NULL,
                    `normalizationBasisJson` TEXT NOT NULL,
                    `cueConfigJson` TEXT NOT NULL,
                    `sourceType` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `version` INTEGER NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reference_asset_records` (
                    `id` TEXT NOT NULL,
                    `drillId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `ownerType` TEXT NOT NULL,
                    `sourceType` TEXT NOT NULL,
                    `videoUri` TEXT,
                    `poseUri` TEXT,
                    `profileUri` TEXT,
                    `thumbnailUri` TEXT,
                    `isReference` INTEGER NOT NULL,
                    `qualityLabel` TEXT,
                    `createdAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `movement_profile_records` (
                    `id` TEXT NOT NULL,
                    `assetId` TEXT NOT NULL,
                    `drillId` TEXT NOT NULL,
                    `extractionVersion` INTEGER NOT NULL,
                    `poseTimelineJson` TEXT NOT NULL,
                    `normalizedFeatureJson` TEXT NOT NULL,
                    `repSegmentsJson` TEXT NOT NULL,
                    `holdSegmentsJson` TEXT NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `calibration_config_records` (
                    `id` TEXT NOT NULL,
                    `drillId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `configJson` TEXT NOT NULL,
                    `scoringVersion` INTEGER NOT NULL,
                    `featureVersion` INTEGER NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reference_template_records_new` (
                    `id` TEXT NOT NULL,
                    `drillId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `templateType` TEXT NOT NULL,
                    `sourceProfileIdsJson` TEXT NOT NULL,
                    `checkpointJson` TEXT NOT NULL,
                    `toleranceJson` TEXT NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `reference_template_records_new` (`id`, `drillId`, `displayName`, `templateType`, `sourceProfileIdsJson`, `checkpointJson`, `toleranceJson`, `createdAtMs`)
                SELECT 
                    `id`,
                    COALESCE(`drillType`, 'legacy_drill'),
                    COALESCE(`name`, 'Template'),
                    'SINGLE_REFERENCE',
                    '',
                    COALESCE(`phaseOrderJson`, '{}'),
                    '{}',
                    COALESCE(`updatedAtMs`, CAST(strftime('%s','now') AS INTEGER) * 1000)
                FROM `reference_template_records`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE IF EXISTS `reference_template_records`")
            db.execSQL("ALTER TABLE `reference_template_records_new` RENAME TO `reference_template_records`")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `session_comparison_records_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sessionId` INTEGER,
                    `subjectAssetId` TEXT NOT NULL,
                    `subjectProfileId` TEXT NOT NULL,
                    `drillId` TEXT NOT NULL,
                    `templateId` TEXT NOT NULL,
                    `overallSimilarityScore` INTEGER NOT NULL,
                    `phaseScoresJson` TEXT NOT NULL,
                    `differencesJson` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `scoringVersion` INTEGER NOT NULL,
                    `createdAtMs` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `session_comparison_records_new` (`id`, `sessionId`, `subjectAssetId`, `subjectProfileId`, `drillId`, `templateId`, `overallSimilarityScore`, `phaseScoresJson`, `differencesJson`, `summary`, `scoringVersion`, `createdAtMs`)
                SELECT 
                    `id`,
                    `sessionId`,
                    '',
                    '',
                    COALESCE(`referenceTemplateId`, 'legacy_drill'),
                    COALESCE(`referenceTemplateId`, 'legacy_template'),
                    `overallSimilarityScore`,
                    COALESCE(`phaseScoresJson`, '{}'),
                    COALESCE(`topDifferencesJson`, ''),
                    'Migrated comparison',
                    1,
                    COALESCE(`comparedAtMs`, CAST(strftime('%s','now') AS INTEGER) * 1000)
                FROM `session_comparison_records`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE IF EXISTS `session_comparison_records`")
            db.execSQL("ALTER TABLE `session_comparison_records_new` RENAME TO `session_comparison_records`")
        }
    }

    val MIGRATION_16_17: Migration = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_settings_new` (
                    `id` INTEGER NOT NULL,
                    `cueStyle` TEXT NOT NULL,
                    `cueFrequencySeconds` REAL NOT NULL,
                    `audioVolume` REAL NOT NULL,
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
                INSERT INTO `user_settings_new` (
                    `id`,
                    `cueStyle`,
                    `cueFrequencySeconds`,
                    `audioVolume`,
                    `localOnlyPrivacyMode`,
                    `retainDays`,
                    `debugOverlayEnabled`,
                    `maxStorageMb`,
                    `startupCountdownSeconds`,
                    `minSessionDurationSeconds`,
                    `alignmentStrictness`,
                    `customLineDeviation`,
                    `customMinimumGoodFormScore`,
                    `customRepAcceptanceThreshold`,
                    `customHoldAlignedThreshold`,
                    `drillCameraSideSelections`,
                    `userBodyProfileJson`
                )
                SELECT
                    `id`,
                    `cueStyle`,
                    `cueFrequencySeconds`,
                    `audioVolume`,
                    `localOnlyPrivacyMode`,
                    `retainDays`,
                    `debugOverlayEnabled`,
                    `maxStorageMb`,
                    `startupCountdownSeconds`,
                    `minSessionDurationSeconds`,
                    `alignmentStrictness`,
                    `customLineDeviation`,
                    `customMinimumGoodFormScore`,
                    `customRepAcceptanceThreshold`,
                    `customHoldAlignedThreshold`,
                    `drillCameraSideSelections`,
                    `userBodyProfileJson`
                FROM `user_settings`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `user_settings`")
            db.execSQL("ALTER TABLE `user_settings_new` RENAME TO `user_settings`")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
    )
}
