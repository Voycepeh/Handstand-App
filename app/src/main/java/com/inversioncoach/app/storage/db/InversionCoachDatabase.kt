package com.inversioncoach.app.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.UserSettings

@Database(
    entities = [SessionRecord::class, UserSettings::class, FrameMetricRecord::class, IssueEvent::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class InversionCoachDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun frameMetricDao(): FrameMetricDao
}
