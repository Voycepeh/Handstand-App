package com.inversioncoach.app.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.inversioncoach.app.storage.ServiceLocator
import java.util.concurrent.TimeUnit

class RetentionCleanupWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        // Cleanup logic remains lightweight for now; keep DB and settings loaded so retention
        // behavior can be expanded without changing worker wiring.
        val repo = ServiceLocator.repository(applicationContext)
        repo.observeSettings()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "retention-cleanup"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
