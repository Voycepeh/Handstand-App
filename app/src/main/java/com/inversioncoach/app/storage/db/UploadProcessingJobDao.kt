package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.SINGLE_ACTIVE_UPLOAD_JOB_ID
import com.inversioncoach.app.model.UploadProcessingJobRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadProcessingJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: UploadProcessingJobRecord)

    @Query("SELECT * FROM upload_processing_jobs WHERE id = :id LIMIT 1")
    fun observeById(id: String = SINGLE_ACTIVE_UPLOAD_JOB_ID): Flow<UploadProcessingJobRecord?>

    @Query("SELECT * FROM upload_processing_jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String = SINGLE_ACTIVE_UPLOAD_JOB_ID): UploadProcessingJobRecord?

    @Query("DELETE FROM upload_processing_jobs WHERE id = :id")
    suspend fun deleteById(id: String = SINGLE_ACTIVE_UPLOAD_JOB_ID)
}
