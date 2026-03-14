package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.db.FrameMetricDao
import com.inversioncoach.app.storage.db.SessionDao
import com.inversioncoach.app.storage.db.UserSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(
    private val sessionDao: SessionDao,
    private val userSettingsDao: UserSettingsDao,
    private val frameMetricDao: FrameMetricDao,
) {
    fun observeSessions(drillType: DrillType? = null): Flow<List<SessionRecord>> =
        if (drillType == null) sessionDao.observeAll() else sessionDao.observeByDrill(drillType)

    suspend fun saveSession(record: SessionRecord): Long = sessionDao.upsert(record)

    suspend fun saveFrameMetric(record: FrameMetricRecord) = frameMetricDao.insertFrameMetric(record)

    suspend fun saveIssueEvent(event: IssueEvent) = frameMetricDao.insertIssueEvent(event)

    fun observeSessionFrameMetrics(sessionId: Long): Flow<List<FrameMetricRecord>> =
        frameMetricDao.observeSessionFrameMetrics(sessionId)

    fun observeIssueTimeline(sessionId: Long): Flow<List<IssueEvent>> =
        frameMetricDao.observeIssueTimeline(sessionId)

    suspend fun clearAllSessions() {
        sessionDao.deleteAll()
        frameMetricDao.deleteAllFrameMetrics()
        frameMetricDao.deleteAllIssueEvents()
    }

    fun observeSettings(): Flow<UserSettings> =
        userSettingsDao.observeSettings().map { it ?: UserSettings() }

    suspend fun saveSettings(settings: UserSettings) = userSettingsDao.upsert(settings)
}
