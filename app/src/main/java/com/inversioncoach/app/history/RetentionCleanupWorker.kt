package com.inversioncoach.app.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inversioncoach.app.storage.ServiceLocator

class RetentionCleanupWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        // MVP: hook for retention cleanup; can query DB and delete old app-private analysis blobs.
        val repo = ServiceLocator.repository(applicationContext)
        repo.observeSettings() // touch flow to ensure DB warmup
        return Result.success()
    }
}
