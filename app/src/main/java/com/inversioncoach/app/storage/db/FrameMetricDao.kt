package com.inversioncoach.app.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface FrameMetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrameMetric(record: FrameMetricRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssueEvent(event: IssueEvent)

    @Query("SELECT * FROM frame_metric_records WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeSessionFrameMetrics(sessionId: Long): Flow<List<FrameMetricRecord>>

    @Query("SELECT * FROM issue_events WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeIssueTimeline(sessionId: Long): Flow<List<IssueEvent>>

    @Query("DELETE FROM frame_metric_records WHERE sessionId = :sessionId")
    suspend fun deleteFrameMetricsForSession(sessionId: Long)

    @Query("DELETE FROM issue_events WHERE sessionId = :sessionId")
    suspend fun deleteIssueEventsForSession(sessionId: Long)

    @Query("DELETE FROM frame_metric_records")
    suspend fun deleteAllFrameMetrics()

    @Query("DELETE FROM issue_events")
    suspend fun deleteAllIssueEvents()
}
