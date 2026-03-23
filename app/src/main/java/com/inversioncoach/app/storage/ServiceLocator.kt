package com.inversioncoach.app.storage

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inversioncoach.app.biomechanics.AlignmentMetricsEngine
import com.inversioncoach.app.coaching.CueEngine
import com.inversioncoach.app.storage.db.InversionCoachDatabase
import com.inversioncoach.app.storage.repository.SessionRepository

object ServiceLocator {
    @Volatile
    private var db: InversionCoachDatabase? = null

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE user_settings ADD COLUMN startupCountdownSeconds INTEGER NOT NULL DEFAULT 10",
            )
        }
    }
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadPipelineStageLabel TEXT")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadAnalysisProcessedFrames INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadAnalysisTotalFrames INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadAnalysisTimestampMs INTEGER")
            db.execSQL("ALTER TABLE session_records ADD COLUMN uploadProgressDetail TEXT")
        }
    }

    private fun db(context: Context): InversionCoachDatabase {
        return db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                InversionCoachDatabase::class.java,
                "inversion_coach.db",
            ).addMigrations(MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build()
                .also { db = it }
        }
    }

    fun repository(context: Context): SessionRepository {
        val db = db(context)
        return SessionRepository(
            db.sessionDao(),
            db.userSettingsDao(),
            db.frameMetricDao(),
            SessionBlobStorage(context.applicationContext),
        )
    }

    fun metricsEngine(): AlignmentMetricsEngine = AlignmentMetricsEngine()

    fun cueEngine(): CueEngine = CueEngine()
}
