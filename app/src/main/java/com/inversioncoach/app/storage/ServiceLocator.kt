package com.inversioncoach.app.storage

import android.content.Context
import androidx.room.Room
import com.inversioncoach.app.biomechanics.AlignmentMetricsEngine
import com.inversioncoach.app.calibration.CalibrationProfileProvider
import com.inversioncoach.app.calibration.DefaultCalibrationProfileProvider
import com.inversioncoach.app.calibration.DrillMovementProfileRepository
import com.inversioncoach.app.coaching.CueEngine
import com.inversioncoach.app.calibration.storage.DrillMovementProfileJson
import com.inversioncoach.app.calibration.storage.RoomDrillMovementProfileRepository
import com.inversioncoach.app.storage.db.DatabaseMigrations
import com.inversioncoach.app.storage.db.InversionCoachDatabase
import com.inversioncoach.app.storage.repository.SessionRepository

object ServiceLocator {
    @Volatile
    private var db: InversionCoachDatabase? = null
    @Volatile
    private var sessionRepository: SessionRepository? = null
    @Volatile
    private var calibrationProvider: CalibrationProfileProvider? = null
    @Volatile
    private var drillMovementProfileRepository: DrillMovementProfileRepository? = null

    fun db(context: Context): InversionCoachDatabase {
        return db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                InversionCoachDatabase::class.java,
                "inversion_coach.db",
            ).addMigrations(*DatabaseMigrations.ALL)
                .fallbackToDestructiveMigration()
                .build()
                .also { db = it }
        }
    }

    fun repository(context: Context): SessionRepository {
        return sessionRepository ?: synchronized(this) {
            sessionRepository ?: run {
                val db = db(context)
                SessionRepository(
                    db.sessionDao(),
                    db.userSettingsDao(),
                    db.frameMetricDao(),
                    db.referenceTemplateDao(),
                    db.sessionComparisonDao(),
                    db.drillDefinitionDao(),
                    db.referenceAssetDao(),
                    db.movementProfileDao(),
                    db.calibrationConfigDao(),
                    SessionBlobStorage(context.applicationContext),
                ).also { sessionRepository = it }
            }
        }
    }

    fun metricsEngine(): AlignmentMetricsEngine = AlignmentMetricsEngine()

    fun cueEngine(): CueEngine = CueEngine()

    fun calibrationProfileProvider(context: Context): CalibrationProfileProvider {
        return calibrationProvider ?: synchronized(this) {
            calibrationProvider ?: DefaultCalibrationProfileProvider(
                drillMovementProfileRepository(context),
            ).also { calibrationProvider = it }
        }
    }

    fun drillMovementProfileRepository(context: Context): DrillMovementProfileRepository {
        return drillMovementProfileRepository ?: synchronized(this) {
            drillMovementProfileRepository ?: RoomDrillMovementProfileRepository(
                dao = db(context).calibrationDao(),
                json = DrillMovementProfileJson(),
            ).also { drillMovementProfileRepository = it }
        }
    }

}
