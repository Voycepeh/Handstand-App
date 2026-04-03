package com.inversioncoach.app.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inversioncoach.app.calibration.storage.CalibrationDao
import com.inversioncoach.app.calibration.storage.CalibrationEntity
import com.inversioncoach.app.model.BodyProfileRecord
import com.inversioncoach.app.model.CalibrationConfigRecord
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.MovementProfileRecord
import com.inversioncoach.app.model.ProfileCalibrationEntity
import com.inversioncoach.app.model.ReferenceAssetRecord
import com.inversioncoach.app.model.ReferenceTemplateRecord
import com.inversioncoach.app.model.SessionComparisonRecord
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.UploadProcessingJob
import com.inversioncoach.app.model.UserProfileEntity
import com.inversioncoach.app.model.UserProfileRecord
import com.inversioncoach.app.model.UserSettings

@Database(
    entities = [
        SessionRecord::class,
        UserSettings::class,
        FrameMetricRecord::class,
        IssueEvent::class,
        CalibrationEntity::class,
        UserProfileEntity::class,
        ProfileCalibrationEntity::class,
        UserProfileRecord::class,
        BodyProfileRecord::class,
        ReferenceTemplateRecord::class,
        SessionComparisonRecord::class,
        DrillDefinitionRecord::class,
        ReferenceAssetRecord::class,
        MovementProfileRecord::class,
        CalibrationConfigRecord::class,
        UploadProcessingJob::class,
    ],
    version = 21,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class InversionCoachDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun frameMetricDao(): FrameMetricDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun profileDao(): ProfileDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun bodyProfileDao(): BodyProfileDao
    abstract fun referenceTemplateDao(): ReferenceTemplateDao
    abstract fun sessionComparisonDao(): SessionComparisonDao
    abstract fun drillDefinitionDao(): DrillDefinitionDao
    abstract fun referenceAssetDao(): ReferenceAssetDao
    abstract fun movementProfileDao(): MovementProfileDao
    abstract fun calibrationConfigDao(): CalibrationConfigDao
    abstract fun uploadProcessingJobDao(): UploadProcessingJobDao
}
