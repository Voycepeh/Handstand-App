package com.inversioncoach.app

import android.app.Application
import androidx.work.Configuration
import com.inversioncoach.app.drills.DrillSeeder
import com.inversioncoach.app.drills.catalog.DrillCatalogRepository
import com.inversioncoach.app.movementprofile.ReferenceTemplateLoader
import com.inversioncoach.app.movementprofile.toRecord
import com.inversioncoach.app.history.RetentionCleanupWorker
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.model.UploadJobStatus
import com.inversioncoach.app.upload.UploadProcessingNotifications
import com.inversioncoach.app.upload.UploadQueueCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class InversionCoachApp : Application(), Configuration.Provider {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        RetentionCleanupWorker.enqueuePeriodic(this)
        appScope.launch {
            val repo = ServiceLocator.repository(this@InversionCoachApp)
            UploadProcessingNotifications(this@InversionCoachApp).ensureChannel()
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
            val coordinator = UploadQueueCoordinator.get(this@InversionCoachApp)
            coordinator.reconcileAndKickoff("app_start")
            val activeQueueJob = ServiceLocator.uploadQueueRepository(this@InversionCoachApp)
                .getActiveJob()
                ?.status == UploadJobStatus.RUNNING
            repo.reconcileActiveUploadJobs(
                hasActiveWorker = activeQueueJob,
                reason = "app_start_queue_reconcile",
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
