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

    private fun db(context: Context): InversionCoachDatabase {
        return db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                InversionCoachDatabase::class.java,
                "inversion_coach.db",
            ).addMigrations(MIGRATION_11_12)
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
