package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.UploadJobStatus
import com.inversioncoach.app.model.UploadProcessingJob
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadProcessingJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: UploadProcessingJob)

    @Query("SELECT * FROM upload_processing_jobs ORDER BY enqueueOrder ASC")
    fun observeAll(): Flow<List<UploadProcessingJob>>

    @Query("SELECT * FROM upload_processing_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getById(jobId: String): UploadProcessingJob?

    @Query("SELECT * FROM upload_processing_jobs WHERE status IN ('RUNNING', 'RETRYING') ORDER BY enqueueOrder ASC LIMIT 1")
    suspend fun getActiveJob(): UploadProcessingJob?

    @Query("SELECT * FROM upload_processing_jobs WHERE status = 'QUEUED' ORDER BY enqueueOrder ASC LIMIT 1")
    suspend fun getNextQueuedJob(): UploadProcessingJob?

    @Query("SELECT * FROM upload_processing_jobs WHERE status IN ('QUEUED', 'RETRYING') ORDER BY enqueueOrder ASC")
    suspend fun getPendingJobs(): List<UploadProcessingJob>

    @Query("SELECT * FROM upload_processing_jobs WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') ORDER BY enqueueOrder ASC")
    suspend fun getNonTerminalJobs(): List<UploadProcessingJob>

    @Query("SELECT COUNT(*) FROM upload_processing_jobs WHERE status IN ('QUEUED', 'RETRYING')")
    suspend fun getActiveQueueCount(): Int

    @Query("UPDATE upload_processing_jobs SET status = :status, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun updateStatus(jobId: String, status: UploadJobStatus, updatedAt: Long)
}
