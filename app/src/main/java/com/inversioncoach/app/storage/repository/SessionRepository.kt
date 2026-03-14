package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.storage.SessionBlobStorage
import com.inversioncoach.app.storage.db.FrameMetricDao
import com.inversioncoach.app.storage.db.SessionDao
import com.inversioncoach.app.storage.db.UserSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(
    private val sessionDao: SessionDao,
    private val userSettingsDao: UserSettingsDao,
    private val frameMetricDao: FrameMetricDao,
    private val sessionBlobStorage: SessionBlobStorage,
) {
    fun observeSessions(drillType: DrillType? = null): Flow<List<SessionRecord>> =
        if (drillType == null) sessionDao.observeAll() else sessionDao.observeByDrill(drillType)

    fun observeSession(sessionId: Long): Flow<SessionRecord?> = sessionDao.observeById(sessionId)

    suspend fun saveSession(record: SessionRecord): Long = sessionDao.upsert(record)

    suspend fun saveRawVideoBlob(sessionId: Long, sourceUri: String): String? {
        val persistedUri = sessionBlobStorage.persistRawVideo(sessionId, sourceUri) ?: return null
        val session = sessionDao.getById(sessionId) ?: return persistedUri
        sessionDao.upsert(session.copy(rawVideoUri = persistedUri))
        return persistedUri
    }

    suspend fun saveAnnotatedVideoBlob(sessionId: Long, sourceUri: String): String? {
        val persistedUri = sessionBlobStorage.persistAnnotatedVideo(sessionId, sourceUri) ?: return null
        val session = sessionDao.getById(sessionId) ?: return persistedUri
        sessionDao.upsert(session.copy(annotatedVideoUri = persistedUri))
        return persistedUri
    }

    suspend fun saveSessionNotes(sessionId: Long, notes: String): String {
        val notesUri = sessionBlobStorage.persistNotes(sessionId, notes)
        val session = sessionDao.getById(sessionId)
        if (session != null) {
            sessionDao.upsert(session.copy(notesUri = notesUri))
        }
        return notesUri
    }

    fun readSessionNotes(sessionId: Long): String? = sessionBlobStorage.readNotes(sessionId)

    suspend fun deleteSessionBlob(sessionId: Long) = sessionBlobStorage.deleteSessionBlob(sessionId)

    suspend fun saveFrameMetric(record: FrameMetricRecord) = frameMetricDao.insertFrameMetric(record)

    suspend fun saveIssueEvent(event: IssueEvent) = frameMetricDao.insertIssueEvent(event)

    fun observeSessionFrameMetrics(sessionId: Long): Flow<List<FrameMetricRecord>> =
        frameMetricDao.observeSessionFrameMetrics(sessionId)

    fun observeIssueTimeline(sessionId: Long): Flow<List<IssueEvent>> =
        frameMetricDao.observeIssueTimeline(sessionId)

    suspend fun clearAllSessions() {
        sessionDao.getAllIds().forEach { sessionBlobStorage.deleteSessionBlob(it) }
        sessionDao.deleteAll()
        frameMetricDao.deleteAllFrameMetrics()
        frameMetricDao.deleteAllIssueEvents()
        sessionBlobStorage.deleteAllBlobs()
    }

    fun observeSettings(): Flow<UserSettings> =
        userSettingsDao.observeSettings().map { it ?: UserSettings() }

    suspend fun saveSettings(settings: UserSettings) = userSettingsDao.upsert(settings)
}
