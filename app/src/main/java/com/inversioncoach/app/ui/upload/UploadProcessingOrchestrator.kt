package com.inversioncoach.app.ui.upload

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inversioncoach.app.MainActivity
import com.inversioncoach.app.R
import com.inversioncoach.app.calibration.RuntimeBodyProfileResolver
import com.inversioncoach.app.model.SINGLE_ACTIVE_UPLOAD_JOB_ID
import com.inversioncoach.app.model.UploadJobStage
import com.inversioncoach.app.model.UploadJobTerminalStatus
import com.inversioncoach.app.model.UploadProcessingJobRecord
import com.inversioncoach.app.storage.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

private const val ACTIVE_WORK_NAME = "uploaded-video-processing"
private const val HEARTBEAT_STALE_MS = 90_000L
private const val CHANNEL_ID = "upload_processing"
private const val ONGOING_NOTIFICATION_ID = 3301
private const val COMPLETION_NOTIFICATION_ID = 3302
private const val FAILURE_NOTIFICATION_ID = 3303

object UploadProcessingOrchestrator {
    suspend fun start(
        context: Context,
        videoUri: Uri,
        trackingMode: UploadTrackingMode,
        selectedDrillId: String?,
        selectedReferenceTemplateId: String?,
        isReferenceUpload: Boolean,
        createDrillFromReferenceUpload: Boolean,
        pendingDrillName: String,
    ): Result<Unit> {
        val appContext = context.applicationContext
        val dao = ServiceLocator.db(appContext).uploadProcessingJobDao()
        reconcileState(appContext)
        val existing = dao.getById()
        if (existing != null && existing.terminalStatus == UploadJobTerminalStatus.NONE) {
            return Result.failure(IllegalStateException("Another upload is already processing."))
        }

        val now = System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<UploadedVideoProcessingWorker>()
            .setInputData(
                workDataOf(
                    UploadedVideoProcessingWorker.KEY_VIDEO_URI to videoUri.toString(),
                    UploadedVideoProcessingWorker.KEY_TRACKING_MODE to trackingMode.name,
                    UploadedVideoProcessingWorker.KEY_DRILL_ID to selectedDrillId,
                    UploadedVideoProcessingWorker.KEY_REFERENCE_TEMPLATE_ID to selectedReferenceTemplateId,
                    UploadedVideoProcessingWorker.KEY_IS_REFERENCE_UPLOAD to isReferenceUpload,
                    UploadedVideoProcessingWorker.KEY_CREATE_DRILL_FROM_REFERENCE to createDrillFromReferenceUpload,
                    UploadedVideoProcessingWorker.KEY_PENDING_DRILL_NAME to pendingDrillName,
                ),
            )
            .build()
        dao.upsert(
            UploadProcessingJobRecord(
                id = SINGLE_ACTIVE_UPLOAD_JOB_ID,
                workId = request.id.toString(),
                stage = UploadJobStage.IMPORTING,
                processedFrames = 0,
                totalFrames = 0,
                startedAt = now,
                updatedAt = now,
                lastHeartbeatAt = now,
                terminalStatus = UploadJobTerminalStatus.NONE,
                reason = null,
            ),
        )
        WorkManager.getInstance(appContext).enqueueUniqueWork(ACTIVE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        return Result.success(Unit)
    }

    suspend fun reconcileState(context: Context) {
        val appContext = context.applicationContext
        val dao = ServiceLocator.db(appContext).uploadProcessingJobDao()
        val job = dao.getById() ?: return
        if (job.terminalStatus != UploadJobTerminalStatus.NONE) return
        val isAlive = job.workId?.let { workId ->
            val info = WorkManager.getInstance(appContext).getWorkInfoById(UUID.fromString(workId)).get()
            info != null && !info.state.isFinished
        } ?: false
        if (isAlive) return
        val now = System.currentTimeMillis()
        if (now - job.lastHeartbeatAt < HEARTBEAT_STALE_MS) return
        dao.upsert(job.copy(stage = UploadJobStage.STALLED, terminalStatus = UploadJobTerminalStatus.STALLED, updatedAt = now, reason = "Worker heartbeat stale"))
    }

    suspend fun cancel(context: Context) {
        val appContext = context.applicationContext
        val dao = ServiceLocator.db(appContext).uploadProcessingJobDao()
        val job = dao.getById() ?: return
        job.workId?.let { WorkManager.getInstance(appContext).cancelWorkById(UUID.fromString(it)) }
        val now = System.currentTimeMillis()
        dao.upsert(job.copy(stage = UploadJobStage.CANCELLED, terminalStatus = UploadJobTerminalStatus.CANCELLED, updatedAt = now, lastHeartbeatAt = now, reason = "Cancelled by user"))
    }

    fun observeActiveJob(context: Context): Flow<UploadProcessingJobRecord?> =
        ServiceLocator.db(context.applicationContext).uploadProcessingJobDao().observeById()
}

class UploadedVideoProcessingWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_VIDEO_URI = "videoUri"
        const val KEY_TRACKING_MODE = "trackingMode"
        const val KEY_DRILL_ID = "drillId"
        const val KEY_REFERENCE_TEMPLATE_ID = "referenceTemplateId"
        const val KEY_IS_REFERENCE_UPLOAD = "isReferenceUpload"
        const val KEY_CREATE_DRILL_FROM_REFERENCE = "createDrillFromReferenceUpload"
        const val KEY_PENDING_DRILL_NAME = "pendingDrillName"
    }

    override suspend fun doWork(): Result {
        UploadNotificationHelper.ensureChannel(applicationContext)
        setForeground(progressForeground("Starting uploaded-video processing", 0))
        val repository = ServiceLocator.repository(applicationContext)
        val runtimeResolver: RuntimeBodyProfileResolver? = ServiceLocator.runtimeBodyProfileResolver(applicationContext)
        val dao = ServiceLocator.db(applicationContext).uploadProcessingJobDao()
        val runner = DefaultUploadVideoAnalysisRunner(applicationContext, repository, runtimeResolver)
        val uri = Uri.parse(inputData.getString(KEY_VIDEO_URI) ?: return Result.failure())
        val trackingMode = inputData.getString(KEY_TRACKING_MODE)?.let { UploadTrackingMode.valueOf(it) } ?: return Result.failure()
        val selectedDrillId = inputData.getString(KEY_DRILL_ID)
        val selectedReferenceTemplateId = inputData.getString(KEY_REFERENCE_TEMPLATE_ID)
        val isReferenceUpload = inputData.getBoolean(KEY_IS_REFERENCE_UPLOAD, false)
        val createDrillFromReferenceUpload = inputData.getBoolean(KEY_CREATE_DRILL_FROM_REFERENCE, false)
        val pendingDrillName = inputData.getString(KEY_PENDING_DRILL_NAME)

        var sessionId: Long? = null
        return runCatching {
            runner.run(
                uri = uri,
                trackingMode = trackingMode,
                selectedDrillId = selectedDrillId,
                selectedReferenceTemplateId = selectedReferenceTemplateId,
                isReferenceUpload = isReferenceUpload,
                createDrillFromReferenceUpload = createDrillFromReferenceUpload,
                pendingDrillName = pendingDrillName,
                onSessionCreated = { sid ->
                    sessionId = sid
                    heartbeat(dao, UploadJobStage.IMPORTING, 0, 0, sid)
                },
                onProgress = { progress ->
                    val stage = when (progress.stage) {
                        UploadStage.IMPORTING_RAW_VIDEO, UploadStage.RAW_IMPORT_COMPLETE -> UploadJobStage.IMPORTING
                        UploadStage.PREPARING_ANALYSIS, UploadStage.ANALYZING_VIDEO -> UploadJobStage.ANALYZING
                        UploadStage.VERIFYING_OUTPUT -> UploadJobStage.VALIDATING
                        UploadStage.RENDERING_OVERLAY, UploadStage.EXPORTING_ANNOTATED_VIDEO -> UploadJobStage.RENDERING
                        UploadStage.COMPLETED_ANNOTATED, UploadStage.COMPLETED_RAW_ONLY -> UploadJobStage.COMPLETED
                        UploadStage.CANCELLED -> UploadJobStage.CANCELLED
                        UploadStage.FAILED -> UploadJobStage.FAILED
                        else -> UploadJobStage.IMPORTING
                    }
                    heartbeat(dao, stage, progress.processedFrames ?: 0, progress.totalFrames ?: 0, sessionId)
                    runCatching {
                        setForegroundAsync(progressForeground(progress.detail ?: stage.name, (progress.percent * 100f).toInt().coerceIn(0, 100))).get()
                    }
                },
                onLog = {},
            )
        }.fold(
            onSuccess = { result ->
                val now = System.currentTimeMillis()
                val completed = dao.getById() ?: return Result.success()
                dao.upsert(completed.copy(sessionId = result.sessionId, stage = UploadJobStage.COMPLETED, terminalStatus = UploadJobTerminalStatus.COMPLETED, updatedAt = now, lastHeartbeatAt = now, reason = null))
                UploadNotificationHelper.notifyComplete(applicationContext, result.sessionId)
                Result.success()
            },
            onFailure = { error ->
                val now = System.currentTimeMillis()
                val failed = dao.getById()
                if (failed != null) {
                    val cancelled = error is kotlinx.coroutines.CancellationException || isStopped
                    dao.upsert(
                        failed.copy(
                            sessionId = sessionId,
                            stage = if (cancelled) UploadJobStage.CANCELLED else UploadJobStage.FAILED,
                            terminalStatus = if (cancelled) UploadJobTerminalStatus.CANCELLED else UploadJobTerminalStatus.FAILED,
                            updatedAt = now,
                            lastHeartbeatAt = now,
                            reason = error.message,
                        ),
                    )
                }
                UploadNotificationHelper.notifyFailure(applicationContext, sessionId)
                Result.failure()
            },
        )
    }

    private fun heartbeat(
        dao: com.inversioncoach.app.storage.db.UploadProcessingJobDao,
        stage: UploadJobStage,
        processedFrames: Int,
        totalFrames: Int,
        sessionId: Long?,
    ) {
        val now = System.currentTimeMillis()
        kotlinx.coroutines.runBlocking {
            val current = dao.getById() ?: return@runBlocking
            dao.upsert(
                current.copy(
                    sessionId = sessionId ?: current.sessionId,
                    stage = stage,
                    processedFrames = processedFrames,
                    totalFrames = totalFrames,
                    updatedAt = now,
                    lastHeartbeatAt = now,
                    terminalStatus = UploadJobTerminalStatus.NONE,
                ),
            )
        }
    }

    private fun progressForeground(text: String, progress: Int): ForegroundInfo {
        val notification = UploadNotificationHelper.buildOngoing(applicationContext, text, progress)
        return ForegroundInfo(
            ONGOING_NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }
}

object UploadNotificationHelper {
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            android.app.NotificationChannel(CHANNEL_ID, "Uploaded video processing", android.app.NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun buildOngoing(context: Context, text: String, progress: Int): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = android.app.PendingIntent.getActivity(context, 40, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Processing uploaded video")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress <= 0)
            .build()
    }

    fun notifyComplete(context: Context, sessionId: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openSessionId", sessionId)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(context, 41, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(context).notify(
            COMPLETION_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Uploaded video ready")
                .setContentText("Tap to view results")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build(),
        )
    }

    fun notifyFailure(context: Context, sessionId: Long?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            sessionId?.let { putExtra("openSessionId", it) }
        }
        val pendingIntent = android.app.PendingIntent.getActivity(context, 42, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(context).notify(
            FAILURE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Uploaded video failed")
                .setContentText("Tap to reopen and recover")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build(),
        )
    }
}
