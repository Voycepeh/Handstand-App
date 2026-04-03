package com.inversioncoach.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UploadJobStatus {
    IDLE,
    QUEUED,
    RUNNING,
    RETRYING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    STALLED,
}

enum class UploadJobStage {
    IMPORTING_RAW_VIDEO,
    ANALYZING_VIDEO,
    VALIDATING_INPUT,
    RENDERING_ANNOTATED_VIDEO,
    FINALIZING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Entity(
    tableName = "upload_processing_jobs",
    indices = [
        Index(value = ["status", "enqueueOrder"]),
        Index(value = ["sessionId"]),
    ],
)
data class UploadProcessingJob(
    @PrimaryKey val jobId: String,
    val sessionId: Long? = null,
    val sourceUri: String,
    val trackingMode: String,
    val selectedDrillId: String? = null,
    val selectedReferenceTemplateId: String? = null,
    val isReferenceUpload: Boolean = false,
    val createDrillFromReferenceUpload: Boolean = false,
    val pendingDrillName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val enqueueOrder: Long,
    val status: UploadJobStatus,
    val currentStage: UploadJobStage,
    val stageStartedAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val lastHeartbeatAt: Long? = null,
    val lastProgressAt: Long? = null,
    val processedFrames: Int = 0,
    val totalFrames: Int = 0,
    val lastTimestampMs: Long? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val failureReason: String? = null,
    val timeoutReason: String? = null,
    val isRecoverable: Boolean = true,
    val workerToken: String? = null,
)
