package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.UploadJobStage
import com.inversioncoach.app.model.UploadJobStatus
import com.inversioncoach.app.model.UploadProcessingJob
import com.inversioncoach.app.storage.db.UploadProcessingJobDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class UploadProcessingQueueRepository(
    private val dao: UploadProcessingJobDao,
    private val maxPendingJobs: Int = 3,
) {
    private val enqueueMutex = Mutex()
    fun observeJobs(): Flow<List<UploadProcessingJob>> = dao.observeAll()

    suspend fun enqueue(
        sourceUri: String,
        trackingMode: String,
        selectedDrillId: String?,
        selectedReferenceTemplateId: String?,
        isReferenceUpload: Boolean,
        createDrillFromReferenceUpload: Boolean,
        pendingDrillName: String?,
    ): UploadProcessingJob? {
        return enqueueMutex.withLock {
            val now = System.currentTimeMillis()
            val queueCount = dao.getActiveQueueCount()
            if (queueCount >= maxPendingJobs) return@withLock null
            val job = UploadProcessingJob(
            jobId = "upload-job-${UUID.randomUUID()}",
            sourceUri = sourceUri,
            trackingMode = trackingMode,
            selectedDrillId = selectedDrillId,
            selectedReferenceTemplateId = selectedReferenceTemplateId,
            isReferenceUpload = isReferenceUpload,
            createDrillFromReferenceUpload = createDrillFromReferenceUpload,
            pendingDrillName = pendingDrillName,
            createdAt = now,
            updatedAt = now,
            enqueueOrder = now,
            status = UploadJobStatus.QUEUED,
            currentStage = UploadJobStage.IMPORTING_RAW_VIDEO,
            maxRetries = 3,
        )
            dao.upsert(job)
            job
        }
    }

    suspend fun getJob(jobId: String): UploadProcessingJob? = dao.getById(jobId)

    suspend fun getActiveJob(): UploadProcessingJob? = dao.getActiveJob()

    suspend fun getNextQueuedJob(): UploadProcessingJob? = dao.getNextQueuedJob()

    suspend fun getNonTerminalJobs(): List<UploadProcessingJob> = dao.getNonTerminalJobs()

    suspend fun getLatestJob(): UploadProcessingJob? = dao.getLatestJob()

    suspend fun save(job: UploadProcessingJob) = dao.upsert(job)

    suspend fun cancel(jobId: String) {
        val job = dao.getById(jobId) ?: return
        dao.upsert(job.copy(status = UploadJobStatus.CANCELLED, updatedAt = System.currentTimeMillis(), completedAt = System.currentTimeMillis(), currentStage = UploadJobStage.CANCELLED))
    }
}
