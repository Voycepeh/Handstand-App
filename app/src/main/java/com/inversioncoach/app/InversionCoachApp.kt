package com.inversioncoach.app

import android.app.Application
import androidx.work.Configuration
import com.inversioncoach.app.drills.DrillSeeder
import com.inversioncoach.app.drills.catalog.DrillCatalogRepository
import com.inversioncoach.app.movementprofile.ReferenceTemplateLoader
import com.inversioncoach.app.movementprofile.toRecord
import com.inversioncoach.app.history.RetentionCleanupWorker
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.upload.UploadProcessingOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log

class InversionCoachApp : Application(), Configuration.Provider {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        RetentionCleanupWorker.enqueuePeriodic(this)
        appScope.launch {
            UploadProcessingOrchestrator.reconcileState(this@InversionCoachApp)
        }
        appScope.launch {
            val repo = ServiceLocator.repository(this@InversionCoachApp)
            Log.i("InversionCoachApp", "process_recreation_reconcile_start")
            repo.reconcileActiveUploadJobs(
                hasActiveWorker = false,
                reason = "app_launch_process_recreation",
            )
            val now = System.currentTimeMillis()
            val existingDrills = repo.getAllDrills().first()
            val catalog = runCatching { DrillCatalogRepository(this@InversionCoachApp).loadCatalog() }.getOrNull()
            val reconciledSeeds = DrillSeeder.reconcileSeededDrills(existing = existingDrills, nowMs = now, catalog = catalog)
            if (reconciledSeeds.isNotEmpty()) {
                repo.seedDrills(reconciledSeeds)
            }
            DrillSeeder.seedCalibration(now).forEach { calibration ->
                repo.saveCalibrationConfig(calibration)
            }
            val loader = ReferenceTemplateLoader(this@InversionCoachApp)
            loader.loadBuiltInTemplates().forEach { template ->
                if (repo.getReferenceTemplate(template.id) == null) {
                    repo.saveReferenceTemplate(template.toRecord(now))
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
