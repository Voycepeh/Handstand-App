package com.inversioncoach.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UploadJobStage {
    IMPORTING,
    ANALYZING,
    VALIDATING,
    RENDERING,
    COMPLETED,
    FAILED,
    STALLED,
    CANCELLED,
}

enum class UploadJobTerminalStatus {
    NONE,
    COMPLETED,
    FAILED,
    STALLED,
    CANCELLED,
}

@Entity(tableName = "upload_processing_jobs")
data class UploadProcessingJobRecord(
    @PrimaryKey val id: String = SINGLE_ACTIVE_UPLOAD_JOB_ID,
    val workId: String? = null,
    val sessionId: Long? = null,
    val stage: UploadJobStage = UploadJobStage.IMPORTING,
    val processedFrames: Int = 0,
    val totalFrames: Int = 0,
    val startedAt: Long,
    val updatedAt: Long,
    val lastHeartbeatAt: Long,
    val terminalStatus: UploadJobTerminalStatus = UploadJobTerminalStatus.NONE,
    val reason: String? = null,
)

const val SINGLE_ACTIVE_UPLOAD_JOB_ID = "active_upload_job"
