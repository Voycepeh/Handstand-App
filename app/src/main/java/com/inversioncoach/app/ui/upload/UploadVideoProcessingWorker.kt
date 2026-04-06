package com.inversioncoach.app.ui.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.R
import com.inversioncoach.app.model.UploadJobStage
import com.inversioncoach.app.model.UploadJobStatus
import com.inversioncoach.app.storage.ServiceLocator
import com.inversioncoach.app.ui.live.SessionDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadVideoProcessingWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        if (jobId.isBlank()) return@withContext Result.failure()

        val repository = ServiceLocator.repository(applicationContext)
        val queueRepo = ServiceLocator.uploadProcessingQueueRepository(applicationContext)
        val job = queueRepo.getJob(jobId) ?: return@withContext Result.failure()

        val workerToken = id.toString()
        queueRepo.save(
            job.copy(
                status = UploadJobStatus.RUNNING,
                startedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                currentStage = UploadJobStage.IMPORTING_RAW_VIDEO,
                workerToken = workerToken,
            ),
        )
        setForeground(createForegroundInfo("Starting upload analysis"))

        runCatching {
            DefaultUploadVideoAnalysisRunner(
                context = applicationContext,
                repository = repository,
            ).run(
                uri = Uri.parse(job.sourceUri),
                ownerToken = workerToken,
                trackingMode = runCatching { UploadTrackingMode.valueOf(job.trackingMode) }.getOrDefault(UploadTrackingMode.HOLD_BASED),
                selectedDrillId = job.selectedDrillId,
                selectedReferenceTemplateId = job.selectedReferenceTemplateId,
                isReferenceUpload = job.isReferenceUpload,
                createDrillFromReferenceUpload = job.createDrillFromReferenceUpload,
                pendingDrillName = job.pendingDrillName,
                onSessionCreated = { sessionId ->
                    val now = System.currentTimeMillis()
                    kotlinx.coroutines.runBlocking {
                        queueRepo.save((queueRepo.getJob(jobId) ?: return@runBlocking).copy(sessionId = sessionId, updatedAt = now))
                    }
                },
                onProgress = { progress ->
                    val now = System.currentTimeMillis()
                    val stage = progress.stage.toJobStage()
                    val existing = kotlinx.coroutines.runBlocking { queueRepo.getJob(jobId) }
                    if (existing != null) {
                        kotlinx.coroutines.runBlocking {
                            queueRepo.save(
                                existing.copy(
                                    status = UploadJobStatus.RUNNING,
                                    currentStage = stage,
                                    updatedAt = now,
                                    lastHeartbeatAt = now,
                                    lastProgressAt = now,
                                    processedFrames = progress.processedFrames ?: existing.processedFrames,
                                    totalFrames = progress.totalFrames ?: existing.totalFrames,
                                    lastTimestampMs = progress.lastTimestampMs ?: existing.lastTimestampMs,
                                    sessionId = existing.sessionId,
                                ),
                            )
                        }
                    }
                    setForegroundAsync(createForegroundInfo(progress.detail ?: progress.stage.label))
                },
                onLog = { line ->
                    SessionDiagnostics.logStructured(
                        event = "upload_worker_event",
                        sessionId = job.sessionId ?: -1L,
                        drillType = DrillType.FREESTYLE,
                        rawUri = null,
                        annotatedUri = null,
                        overlayFrameCount = 0,
                        failureReason = line,
                    )
                },
            )
        }.fold(
            onSuccess = { output ->
                val now = System.currentTimeMillis()
                val existing = queueRepo.getJob(jobId) ?: return@fold Result.success()
                queueRepo.save(
                    existing.copy(
                        status = UploadJobStatus.COMPLETED,
                        currentStage = UploadJobStage.COMPLETED,
                        updatedAt = now,
                        completedAt = now,
                        failureReason = output.exportFailureReason,
                    ),
                )
                Result.success()
            },
            onFailure = { error ->
                val now = System.currentTimeMillis()
                val existing = queueRepo.getJob(jobId)
                val shouldRetry = !isStopped && runAttemptCount < (existing?.maxRetries ?: 3)
                if (existing != null) {
                    queueRepo.save(
                        existing.copy(
                            status = when {
                                isStopped -> UploadJobStatus.CANCELLED
                                shouldRetry -> UploadJobStatus.RETRYING
                                else -> UploadJobStatus.FAILED
                            },
                            currentStage = when {
                                isStopped -> UploadJobStage.CANCELLED
                                shouldRetry -> existing.currentStage
                                else -> UploadJobStage.FAILED
                            },
                            updatedAt = now,
                            completedAt = if (isStopped || !shouldRetry) now else null,
                            retryCount = if (shouldRetry) existing.retryCount + 1 else existing.retryCount,
                            failureReason = error.message ?: "Upload worker failed",
                        ),
                    )
                }
                when {
                    isStopped -> Result.failure()
                    shouldRetry -> Result.retry()
                    else -> Result.failure()
                }
            },
        )
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        createChannelIfNeeded()
        return ForegroundInfo(NOTIFICATION_ID, notification(message))
    }

    private fun notification(message: String): Notification =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Upload analysis in progress")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private fun createChannelIfNeeded() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Upload analysis", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "uploaded-video-analysis"
        const val TAG = "upload-video-analysis"
        private const val KEY_JOB_ID = "job_id"
        private const val CHANNEL_ID = "upload_analysis"
        private const val NOTIFICATION_ID = 4021

        fun inputData(jobId: String): Data = Data.Builder().putString(KEY_JOB_ID, jobId).build()
    }
}

private fun UploadStage.toJobStage(): UploadJobStage = when (this) {
    UploadStage.IMPORTING_RAW_VIDEO, UploadStage.RAW_IMPORT_COMPLETE -> UploadJobStage.IMPORTING_RAW_VIDEO
    UploadStage.NORMALIZING_INPUT, UploadStage.PREPARING_ANALYSIS -> UploadJobStage.VALIDATING_INPUT
    UploadStage.ANALYZING_VIDEO -> UploadJobStage.ANALYZING_VIDEO
    UploadStage.RENDERING_OVERLAY, UploadStage.EXPORTING_ANNOTATED_VIDEO -> UploadJobStage.RENDERING_ANNOTATED_VIDEO
    UploadStage.VERIFYING_OUTPUT -> UploadJobStage.FINALIZING
    UploadStage.COMPLETED_ANNOTATED, UploadStage.COMPLETED_RAW_ONLY -> UploadJobStage.COMPLETED
    UploadStage.CANCELLED -> UploadJobStage.CANCELLED
    UploadStage.FAILED -> UploadJobStage.FAILED
    else -> UploadJobStage.VALIDATING_INPUT
}
