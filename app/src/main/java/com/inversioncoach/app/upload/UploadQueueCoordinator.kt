package com.inversioncoach.app.upload

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.inversioncoach.app.model.UploadJobStage
import com.inversioncoach.app.model.UploadJobStatus
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.storage.repository.UploadProcessingQueueRepository

private const val TAG = "UploadQueueCoordinator"

class UploadQueueCoordinator private constructor(
    private val context: Context,
    private val queueRepository: UploadProcessingQueueRepository,
) {
    suspend fun enqueue(
        sourceUri: String,
        trackingMode: String,
        selectedDrillId: String?,
        selectedReferenceTemplateId: String?,
        isReferenceUpload: Boolean,
        createDrillFromReferenceUpload: Boolean,
        pendingDrillName: String?,
    ): EnqueueResult {
        val job = queueRepository.enqueue(
            sourceUri = sourceUri,
            trackingMode = trackingMode,
            selectedDrillId = selectedDrillId,
            selectedReferenceTemplateId = selectedReferenceTemplateId,
            isReferenceUpload = isReferenceUpload,
            createDrillFromReferenceUpload = createDrillFromReferenceUpload,
            pendingDrillName = pendingDrillName,
        ) ?: return EnqueueResult.QueueFull
        reconcileAndKickoff("enqueue")
        return EnqueueResult.Enqueued(job.jobId)
    }

    suspend fun cancel(jobId: String) {
        queueRepository.cancel(jobId)
        reconcileAndKickoff("cancel")
    }

    suspend fun reconcileAndKickoff(reason: String) {
        normalizeRetryingJobs()
        recoverDeadRunningJobs()
        val active = queueRepository.getActiveJob()?.takeIf { it.status == UploadJobStatus.RUNNING }
        if (active != null) return
        val next = queueRepository.getNextQueuedJob() ?: return
        Log.i(TAG, "queue_start_next reason=$reason jobId=${next.jobId}")
        val work = OneTimeWorkRequestBuilder<UploadProcessingWorker>()
            .setInputData(workDataOf(UploadProcessingWorker.KEY_JOB_ID to next.jobId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .addTag(UploadProcessingWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, work)
    }


    private suspend fun normalizeRetryingJobs() {
        val active = queueRepository.getActiveJob() ?: return
        if (active.status != UploadJobStatus.RETRYING) return
        val now = System.currentTimeMillis()
        if (now - active.updatedAt < RETRY_BACKOFF_MS) return
        queueRepository.save(active.copy(status = UploadJobStatus.QUEUED, updatedAt = now))
    }

    private suspend fun recoverDeadRunningJobs() {
        val active = queueRepository.getActiveJob()?.takeIf { it.status == UploadJobStatus.RUNNING } ?: return
        val now = System.currentTimeMillis()
        val heartbeatStale = active.lastHeartbeatAt?.let { now - it > HEARTBEAT_TIMEOUT_MS } ?: true
        val progressStale = active.lastProgressAt?.let { now - it > PROGRESS_TIMEOUT_MS } ?: false
        val stageStale = active.stageStartedAt?.let { now - it > stageTimeoutFor(active.currentStage) } ?: false
        val overallStale = active.startedAt?.let { now - it > OVERALL_JOB_TIMEOUT_MS } ?: false
        if (!heartbeatStale && !progressStale && !stageStale && !overallStale) return
        val recoverable = active.isRecoverable && active.retryCount < active.maxRetries
        val updated = if (recoverable) {
            active.copy(
                status = UploadJobStatus.RETRYING,
                retryCount = active.retryCount + 1,
                updatedAt = now,
                timeoutReason = timeoutReason(heartbeatStale, progressStale, stageStale, overallStale),
                failureReason = "RECOVERED_STALE_WORKER",
                workerToken = null,
            )
        } else {
            active.copy(
                status = UploadJobStatus.FAILED,
                completedAt = now,
                updatedAt = now,
                timeoutReason = timeoutReason(heartbeatStale, progressStale, stageStale, overallStale),
                currentStage = UploadJobStage.FAILED,
                failureReason = active.failureReason ?: "JOB_STALLED",
            )
        }
        queueRepository.save(updated)
    }

    companion object {
        private const val UNIQUE_WORK = "uploaded-video-queue-runner"
        private const val HEARTBEAT_TIMEOUT_MS = 60_000L
        private const val PROGRESS_TIMEOUT_MS = 90_000L
        private const val OVERALL_JOB_TIMEOUT_MS = 25 * 60_000L
        private const val RETRY_BACKOFF_MS = 15_000L

        fun get(context: Context): UploadQueueCoordinator {
            val appContext = context.applicationContext
            return UploadQueueCoordinator(
                appContext,
                ServiceLocator.uploadQueueRepository(appContext),
            )
        }
    }
}

sealed interface EnqueueResult {
    data class Enqueued(val jobId: String) : EnqueueResult
    data object QueueFull : EnqueueResult
}


internal fun stageTimeoutFor(stage: UploadJobStage): Long = when (stage) {
    UploadJobStage.IMPORTING_RAW_VIDEO -> 3 * 60_000L
    UploadJobStage.ANALYZING_VIDEO -> 12 * 60_000L
    UploadJobStage.VALIDATING_INPUT -> 2 * 60_000L
    UploadJobStage.RENDERING_ANNOTATED_VIDEO -> 12 * 60_000L
    UploadJobStage.FINALIZING -> 4 * 60_000L
    else -> 3 * 60_000L
}

internal fun timeoutReason(heartbeatStale: Boolean, progressStale: Boolean, stageStale: Boolean, overallStale: Boolean): String = when {
    overallStale -> "overall_timeout"
    stageStale -> "stage_timeout"
    progressStale -> "progress_timeout"
    heartbeatStale -> "heartbeat_timeout"
    else -> "unknown_timeout"
}
