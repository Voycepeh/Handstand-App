package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.AnnotatedExportStage
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.CleanupStatus
import com.inversioncoach.app.model.CompressionStatus
import com.inversioncoach.app.model.RetainedAssetType
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.storage.SessionBlobStorage
import com.inversioncoach.app.storage.db.FrameMetricDao
import com.inversioncoach.app.storage.db.SessionDao
import com.inversioncoach.app.storage.db.UserSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val STALE_EXPORT_RECONCILIATION_MS = 2 * 60 * 1000L

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
        sessionDao.upsert(session.copy(rawVideoUri = persistedUri, rawMasterUri = persistedUri))
        enforceConfiguredStorageLimit()
        return persistedUri
    }

    suspend fun saveAnnotatedVideoBlob(sessionId: Long, sourceUri: String): String? {
        val persistedUri = sessionBlobStorage.persistAnnotatedVideo(sessionId, sourceUri) ?: return null
        val session = sessionDao.getById(sessionId) ?: return persistedUri
        sessionDao.upsert(session.copy(annotatedVideoUri = persistedUri, annotatedMasterUri = persistedUri))
        enforceConfiguredStorageLimit()
        return persistedUri
    }



    suspend fun saveAnnotatedFinalVideoBlob(sessionId: Long, sourceUri: String): String? {
        val persistedUri = sessionBlobStorage.persistAnnotatedFinalVideo(sessionId, sourceUri) ?: return null
        val session = sessionDao.getById(sessionId) ?: return persistedUri
        sessionDao.upsert(session.copy(annotatedFinalUri = persistedUri, annotatedVideoUri = persistedUri))
        enforceConfiguredStorageLimit()
        return persistedUri
    }

    suspend fun saveRawFinalVideoBlob(sessionId: Long, sourceUri: String): String? {
        val persistedUri = sessionBlobStorage.persistRawFinalVideo(sessionId, sourceUri) ?: return null
        val session = sessionDao.getById(sessionId) ?: return persistedUri
        sessionDao.upsert(session.copy(rawFinalUri = persistedUri, rawVideoUri = persistedUri))
        enforceConfiguredStorageLimit()
        return persistedUri
    }


    suspend fun updateAnnotatedExportProgress(
        sessionId: Long,
        stage: AnnotatedExportStage,
        percent: Int,
        etaSeconds: Int?,
        elapsedMs: Long?,
        failureDetail: String? = null,
        failureReason: String? = null,
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(
            session.copy(
                annotatedExportStage = stage,
                annotatedExportPercent = percent.coerceIn(0, 100),
                annotatedExportEtaSeconds = etaSeconds,
                annotatedExportElapsedMs = elapsedMs,
                annotatedExportFailureDetail = failureDetail,
                annotatedExportFailureReason = failureReason ?: session.annotatedExportFailureReason,
                annotatedExportLastUpdatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun reconcileRawPersistState(sessionId: Long): Boolean {
        val session = sessionDao.getById(sessionId) ?: return false
        val rawUri = session.rawFinalUri ?: session.rawVideoUri ?: session.rawMasterUri
        val isValid = rawUri?.let { uri ->
            runCatching {
                val path = android.net.Uri.parse(uri).path ?: return@runCatching false
                val file = java.io.File(path)
                file.exists() && file.length() > 0L
            }.getOrDefault(false)
        } ?: false
        if (!isValid) return false
        if (session.rawPersistStatus == RawPersistStatus.SUCCEEDED) return false
        sessionDao.upsert(session.copy(rawPersistStatus = RawPersistStatus.SUCCEEDED))
        return true
    }

    suspend fun updateAnnotatedExportStatus(sessionId: Long, status: AnnotatedExportStatus) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(session.copy(annotatedExportStatus = status))
    }

    suspend fun updateAnnotatedExportFailureReason(sessionId: Long, reason: String?) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(session.copy(annotatedExportFailureReason = reason))
    }

    suspend fun updateUploadPipelineProgress(
        sessionId: Long,
        stageLabel: String?,
        processedFrames: Int = 0,
        totalFrames: Int = 0,
        timestampMs: Long? = null,
        detail: String? = null,
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(
            session.copy(
                uploadPipelineStageLabel = stageLabel,
                uploadAnalysisProcessedFrames = processedFrames.coerceAtLeast(0),
                uploadAnalysisTotalFrames = totalFrames.coerceAtLeast(0),
                uploadAnalysisTimestampMs = timestampMs,
                uploadProgressDetail = detail,
            ),
        )
    }

    suspend fun reconcileStaleProcessingState(sessionId: Long, hasActiveExportJob: Boolean): Boolean {
        val session = sessionDao.getById(sessionId) ?: return false
        val lastUpdatedAt = session.annotatedExportLastUpdatedAt ?: 0L
        val ageMs = (System.currentTimeMillis() - lastUpdatedAt).coerceAtLeast(0L)
        val isStale =
            (session.annotatedExportStatus == AnnotatedExportStatus.VALIDATING_INPUT ||
                session.annotatedExportStatus == AnnotatedExportStatus.PROCESSING ||
                session.annotatedExportStatus == AnnotatedExportStatus.PROCESSING_SLOW) &&
                session.annotatedVideoUri.isNullOrBlank() &&
                !hasActiveExportJob &&
                lastUpdatedAt > 0L &&
                ageMs >= STALE_EXPORT_RECONCILIATION_MS
        if (!isStale) return false
        sessionDao.upsert(
            session.copy(
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                annotatedExportFailureReason = "EXPORT_JOB_LOST",
                annotatedExportFailureDetail = "Stale processing reconciled with no active export owner",
                annotatedExportStage = AnnotatedExportStage.FAILED,
                annotatedExportPercent = 100,
                annotatedExportLastUpdatedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun updateRawPersistStatus(sessionId: Long, status: RawPersistStatus) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(session.copy(rawPersistStatus = status))
    }

    suspend fun updateRawPersistFailureReason(sessionId: Long, reason: String?) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(session.copy(rawPersistFailureReason = reason))
    }


    suspend fun updateMediaPipelineState(
        sessionId: Long,
        block: (SessionRecord) -> SessionRecord,
    ) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(block(session))
    }

    suspend fun saveOverlayTimeline(sessionId: Long, timelineJson: String): String {
        val persistedUri = sessionBlobStorage.persistOverlayTimeline(sessionId, timelineJson)
        val session = sessionDao.getById(sessionId)
        if (session != null) {
            sessionDao.upsert(session.copy(overlayTimelineUri = persistedUri))
        }
        return persistedUri
    }

    fun readOverlayTimeline(sessionId: Long): String? = sessionBlobStorage.readOverlayTimeline(sessionId)
    suspend fun saveSessionNotes(sessionId: Long, notes: String): String {
        val notesUri = sessionBlobStorage.persistNotes(sessionId, notes)
        val session = sessionDao.getById(sessionId)
        if (session != null) {
            sessionDao.upsert(session.copy(notesUri = notesUri))
        }
        return notesUri
    }

    fun readSessionNotes(sessionId: Long): String? = sessionBlobStorage.readNotes(sessionId)

    fun saveSessionDiagnostics(sessionId: Long, diagnostics: String): String =
        sessionBlobStorage.persistDiagnostics(sessionId, diagnostics)

    fun readSessionDiagnostics(sessionId: Long): String? = sessionBlobStorage.readDiagnostics(sessionId)

    fun sessionStorageBytes(sessionId: Long): Long = sessionBlobStorage.sessionSizeBytes(sessionId)

    fun totalStorageBytes(): Long = sessionBlobStorage.totalSizeBytes()

    suspend fun enforceStorageLimit(maxStorageMb: Int) {
        val maxBytes = maxStorageMb.coerceAtLeast(1).toLong() * 1024L * 1024L
        val sessionsByOldest = sessionDao.getAllOldestFirst()

        var currentBytes = sessionBlobStorage.totalSizeBytes()
        for (session in sessionsByOldest) {
            if (currentBytes <= maxBytes) break
            val sessionBytes = sessionBlobStorage.sessionSizeBytes(session.id)
            if (sessionBytes <= 0L) continue
            frameMetricDao.deleteFrameMetricsForSession(session.id)
            frameMetricDao.deleteIssueEventsForSession(session.id)
            sessionDao.deleteById(session.id)
            sessionBlobStorage.deleteSessionBlob(session.id)
            currentBytes -= sessionBytes
        }
    }

    suspend fun deleteSessionBlob(sessionId: Long) = sessionBlobStorage.deleteSessionBlob(sessionId)

    suspend fun clearSessionVideos(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionBlobStorage.deleteVideoFiles(sessionId)
        sessionDao.upsert(
            session.copy(
                rawVideoUri = null,
                rawPersistStatus = RawPersistStatus.NOT_STARTED,
                rawPersistFailureReason = null,
                annotatedVideoUri = null,
                rawMasterUri = null,
                annotatedMasterUri = null,
                rawFinalUri = null,
                annotatedFinalUri = null,
                bestPlayableUri = null,
                annotatedExportStatus = AnnotatedExportStatus.NOT_STARTED,
                annotatedExportFailureReason = null,
                rawCompressionStatus = CompressionStatus.NOT_STARTED,
                annotatedCompressionStatus = CompressionStatus.NOT_STARTED,
                cleanupStatus = CleanupStatus.NOT_STARTED,
                retainedAssetType = RetainedAssetType.NONE,
                overlayFrameCount = 0,
                overlayTimelineUri = null,
            ),
        )
    }

    suspend fun deleteSession(sessionId: Long) {
        frameMetricDao.deleteFrameMetricsForSession(sessionId)
        frameMetricDao.deleteIssueEventsForSession(sessionId)
        sessionDao.deleteById(sessionId)
        sessionBlobStorage.deleteSessionBlob(sessionId)
    }

    suspend fun saveFrameMetric(record: FrameMetricRecord) = frameMetricDao.insertFrameMetric(record)

    suspend fun saveIssueEvent(event: IssueEvent) = frameMetricDao.insertIssueEvent(event)

    fun observeSessionFrameMetrics(sessionId: Long): Flow<List<FrameMetricRecord>> =
        frameMetricDao.observeSessionFrameMetrics(sessionId)

    fun observeIssueTimeline(sessionId: Long): Flow<List<IssueEvent>> =
        frameMetricDao.observeIssueTimeline(sessionId)


    fun deleteUri(uri: String?): Boolean = sessionBlobStorage.deleteUri(uri)

    fun sessionWorkingFile(sessionId: Long, fileName: String) = sessionBlobStorage.sessionWorkingFile(sessionId, fileName)

    suspend fun clearAllSessions() {
        sessionDao.getAllIds().forEach { sessionBlobStorage.deleteSessionBlob(it) }
        sessionDao.deleteAll()
        frameMetricDao.deleteAllFrameMetrics()
        frameMetricDao.deleteAllIssueEvents()
        sessionBlobStorage.deleteAllBlobs()
    }

    fun observeSettings(): Flow<UserSettings> =
        userSettingsDao.observeSettings().map { it ?: UserSettings() }

    suspend fun saveSettings(settings: UserSettings) {
        userSettingsDao.upsert(settings)
        enforceStorageLimit(settings.maxStorageMb)
    }

    suspend fun getDrillCameraSide(drillType: DrillType): DrillCameraSide? {
        val settings = userSettingsDao.getSettings() ?: return null
        return decodeDrillSides(settings.drillCameraSideSelections)[drillType]
    }

    suspend fun saveDrillCameraSide(drillType: DrillType, side: DrillCameraSide) {
        val settings = userSettingsDao.getSettings() ?: UserSettings()
        val updated = decodeDrillSides(settings.drillCameraSideSelections).toMutableMap().apply {
            this[drillType] = side
        }
        userSettingsDao.upsert(settings.copy(drillCameraSideSelections = encodeDrillSides(updated)))
    }

    suspend fun saveUserBodyProfile(profile: UserBodyProfile?) {
        val settings = userSettingsDao.getSettings() ?: UserSettings()
        userSettingsDao.upsert(settings.copy(userBodyProfileJson = profile?.encode()))
    }

    suspend fun getUserBodyProfile(): UserBodyProfile? {
        val settings = userSettingsDao.getSettings() ?: return null
        return UserBodyProfile.decode(settings.userBodyProfileJson)
    }

    private suspend fun enforceConfiguredStorageLimit() {
        val settings = userSettingsDao.getSettings() ?: UserSettings()
        enforceStorageLimit(settings.maxStorageMb)
    }

    private fun encodeDrillSides(map: Map<DrillType, DrillCameraSide>): String =
        map.entries.joinToString(separator = ";") { "${it.key.name}:${it.value.name}" }

    private fun decodeDrillSides(raw: String): Map<DrillType, DrillCameraSide> =
        raw.split(';')
            .mapNotNull { token ->
                val parts = token.split(':')
                if (parts.size != 2) return@mapNotNull null
                val drill = DrillType.fromStoredName(parts[0]) ?: return@mapNotNull null
                val side = DrillCameraSide.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
                drill to side
            }.toMap()
}
