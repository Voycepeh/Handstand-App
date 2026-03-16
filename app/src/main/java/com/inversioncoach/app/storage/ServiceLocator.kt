package com.inversioncoach.app.storage

import android.content.Context
import androidx.room.Room
import com.inversioncoach.app.biomechanics.AlignmentMetricsEngine
import com.inversioncoach.app.coaching.CueEngine
import com.inversioncoach.app.storage.db.InversionCoachDatabase
import com.inversioncoach.app.storage.repository.SessionRepository

object ServiceLocator {
    @Volatile
    private var db: InversionCoachDatabase? = null

    private fun db(context: Context): InversionCoachDatabase {
        return db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                InversionCoachDatabase::class.java,
                "inversion_coach.db",
            ).fallbackToDestructiveMigration().build().also { db = it }
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
