package com.inversioncoach.app.upload

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.inversioncoach.app.model.UploadJobStage
import com.inversioncoach.app.model.UploadJobStatus
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.upload.DefaultUploadVideoAnalysisRunner
import com.inversioncoach.app.ui.upload.UploadProgress
import com.inversioncoach.app.ui.upload.UploadStage
import com.inversioncoach.app.ui.upload.UploadTrackingMode
import kotlinx.coroutines.coroutineScope

class UploadProcessingWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = coroutineScope {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return@coroutineScope Result.failure()
        val queueRepo = ServiceLocator.uploadQueueRepository(applicationContext)
        val sessionRepo = ServiceLocator.repository(applicationContext)
        val notifications = UploadProcessingNotifications(applicationContext)
        val coordinator = UploadQueueCoordinator.get(applicationContext)

        val job = queueRepo.getJob(jobId) ?: return@coroutineScope Result.failure()
        val now = System.currentTimeMillis()
        queueRepo.save(
            job.copy(
                status = UploadJobStatus.RUNNING,
                startedAt = job.startedAt ?: now,
                updatedAt = now,
                stageStartedAt = now,
                lastHeartbeatAt = now,
                workerToken = id.toString(),
            ),
        )
        setForeground(foregroundInfo(jobId, "Preparing uploaded processing", null))

        val runner = DefaultUploadVideoAnalysisRunner(
            context = applicationContext,
            repository = sessionRepo,
            runtimeBodyProfileResolver = ServiceLocator.runtimeBodyProfileResolver(applicationContext),
        )
        val orchestrator = UploadAnalysisOrchestrator(runner)
        val tracking = runCatching { UploadTrackingMode.valueOf(job.trackingMode) }.getOrDefault(UploadTrackingMode.HOLD_BASED)

        val result = runCatching {
            orchestrator.execute(
                uri = Uri.parse(job.sourceUri),
                ownerToken = id.toString(),
                trackingMode = tracking,
                selectedDrillId = job.selectedDrillId,
                selectedReferenceTemplateId = job.selectedReferenceTemplateId,
                isReferenceUpload = job.isReferenceUpload,
                createDrillFromReferenceUpload = job.createDrillFromReferenceUpload,
                pendingDrillName = job.pendingDrillName,
                onSessionCreated = { sessionId ->
                    val current = kotlinx.coroutines.runBlocking { queueRepo.getJob(jobId) }
                    if (current != null) {
                        kotlinx.coroutines.runBlocking { queueRepo.save(current.copy(sessionId = sessionId, updatedAt = System.currentTimeMillis())) }
                    }
                },
                onProgress = { progress ->
                    kotlinx.coroutines.runBlocking { updateProgress(queueRepo, jobId, progress) }
                },
                onLog = {},
            )
        }

        val pendingNext = queueRepo.getNextQueuedJob() != null
        if (result.isSuccess) {
            val completed = queueRepo.getJob(jobId)?.copy(
                status = UploadJobStatus.COMPLETED,
                currentStage = UploadJobStage.COMPLETED,
                updatedAt = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis(),
                lastHeartbeatAt = System.currentTimeMillis(),
            )
            if (completed != null) queueRepo.save(completed)
            notifications.completed(jobId, completed?.sessionId)
            coordinator.reconcileAndKickoff("completed")
            Result.success()
        } else {
            val current = queueRepo.getJob(jobId) ?: job
            val retryable = current.isRecoverable && current.retryCount < current.maxRetries && !isPermanentFailure(result.exceptionOrNull())
            val failedStatus = if (retryable) UploadJobStatus.RETRYING else UploadJobStatus.FAILED
            queueRepo.save(
                current.copy(
                    status = failedStatus,
                    retryCount = if (retryable) current.retryCount + 1 else current.retryCount,
                    failureReason = result.exceptionOrNull()?.message ?: "Upload processing failed",
                    timeoutReason = if (retryable) "retry_backoff" else current.timeoutReason,
                    currentStage = if (retryable) current.currentStage else UploadJobStage.FAILED,
                    updatedAt = System.currentTimeMillis(),
                    completedAt = if (retryable) null else System.currentTimeMillis(),
                    workerToken = null,
                    lastHeartbeatAt = System.currentTimeMillis(),
                ),
            )
            if (!retryable) notifications.failed(jobId, pendingNext, current.sessionId)
            coordinator.reconcileAndKickoff(if (retryable) "retry" else "failed")
            Result.success()
        }
    }

    private suspend fun updateProgress(
        queueRepo: com.inversioncoach.app.storage.repository.UploadProcessingQueueRepository,
        jobId: String,
        progress: UploadProgress,
    ) {
        val job = queueRepo.getJob(jobId) ?: return
        val now = System.currentTimeMillis()
        val stage = progress.stage.toJobStage()
        queueRepo.save(
            job.copy(
                currentStage = stage,
                updatedAt = now,
                stageStartedAt = if (job.currentStage != stage) now else job.stageStartedAt,
                lastHeartbeatAt = now,
                lastProgressAt = now,
                processedFrames = progress.processedFrames ?: job.processedFrames,
                totalFrames = progress.totalFrames ?: job.totalFrames,
                lastTimestampMs = progress.lastTimestampMs ?: job.lastTimestampMs,
            ),
        )
        setForeground(foregroundInfo(jobId, stage.name.replace('_', ' '), (progress.percent * 100).toInt()))
    }

    private fun isPermanentFailure(error: Throwable?): Boolean {
        val message = error?.message?.uppercase().orEmpty()
        return listOf("UNSUPPORTED", "CORRUPT", "UNREADABLE", "INVALID").any { message.contains(it) }
    }

    private fun foregroundInfo(jobId: String, detail: String, progress: Int?): ForegroundInfo {
        val notification = UploadProcessingNotifications(applicationContext)
            .running(jobId, "Processing uploaded video", detail, progress)
        return ForegroundInfo(jobId.hashCode(), notification)
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val WORK_TAG = "uploaded-video-processing"
    }
}

private fun UploadStage.toJobStage(): UploadJobStage = when (this) {
    UploadStage.IMPORTING_RAW_VIDEO -> UploadJobStage.IMPORTING_RAW_VIDEO
    UploadStage.PREPARING_ANALYSIS,
    UploadStage.ANALYZING_VIDEO,
    -> UploadJobStage.ANALYZING_VIDEO
    UploadStage.RENDERING_OVERLAY,
    UploadStage.EXPORTING_ANNOTATED_VIDEO,
    -> UploadJobStage.RENDERING_ANNOTATED_VIDEO
    UploadStage.VERIFYING_OUTPUT -> UploadJobStage.VALIDATING_INPUT
    UploadStage.COMPLETED_ANNOTATED,
    UploadStage.COMPLETED_RAW_ONLY,
    -> UploadJobStage.COMPLETED
    UploadStage.FAILED -> UploadJobStage.FAILED
    UploadStage.CANCELLED -> UploadJobStage.CANCELLED
    else -> UploadJobStage.FINALIZING
}
