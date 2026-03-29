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
import org.json.JSONObject

private const val STALE_EXPORT_RECONCILIATION_MS = 2 * 60 * 1000L
private const val DEFAULT_PROFILE_NAME = "Profile 1"

data class UserProfileStatus(
    val name: String,
    val isActive: Boolean,
    val isCalibrated: Boolean,
)

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

    /**
     * Legacy compatibility only: `userBodyProfileJson` predates profile-based calibration storage.
     * New code should use getCalibrationForActiveProfile()/saveCalibrationForProfile().
     */
    suspend fun saveUserBodyProfile(profile: UserBodyProfile?) {
        val settings = userSettingsDao.getSettings() ?: UserSettings()
        userSettingsDao.upsert(settings.copy(userBodyProfileJson = profile?.encode()))
    }

    /**
     * Legacy compatibility only: prefer getCalibrationForActiveProfile() for canonical profile-based lookup.
     */
    suspend fun getUserBodyProfile(): UserBodyProfile? {
        val settings = userSettingsDao.getSettings() ?: return null
        return UserBodyProfile.decode(settings.userBodyProfileJson)
    }

    fun observeProfileStatuses(): Flow<List<UserProfileStatus>> =
        observeSettings().map { settings ->
            val normalized = normalizeProfileState(settings)
            profileStatusesFrom(normalized)
        }

    suspend fun listProfiles(): List<UserProfileStatus> {
        val settings = normalizeProfileState(userSettingsDao.getSettings() ?: UserSettings())
        return profileStatusesFrom(settings)
    }

    suspend fun getActiveProfile(): UserProfileStatus {
        return listProfiles().firstOrNull { it.isActive } ?: UserProfileStatus(DEFAULT_PROFILE_NAME, true, false)
    }

    suspend fun setActiveProfile(profileName: String): Boolean {
        if (profileName.isBlank()) return false
        val settings = normalizeProfileState(userSettingsDao.getSettings() ?: UserSettings())
        val profiles = parseProfileNames(settings.profileNamesCsv)
        if (!profiles.contains(profileName)) return false
        userSettingsDao.upsert(settings.copy(activeProfileName = profileName))
        return true
    }

    suspend fun createProfile(profileName: String): Boolean {
        val normalizedName = profileName.trim()
        if (normalizedName.isBlank()) return false
        val settings = normalizeProfileState(userSettingsDao.getSettings() ?: UserSettings())
        val profiles = parseProfileNames(settings.profileNamesCsv)
        if (profiles.contains(normalizedName)) return false
        userSettingsDao.upsert(settings.copy(profileNamesCsv = (profiles + normalizedName).joinToString(",")))
        return true
    }

    suspend fun renameProfile(oldName: String, newName: String): Boolean {
        val targetName = newName.trim()
        if (oldName.isBlank() || targetName.isBlank()) return false
        val settings = normalizeProfileState(userSettingsDao.getSettings() ?: UserSettings())
        val profiles = parseProfileNames(settings.profileNamesCsv)
        if (!profiles.contains(oldName) || profiles.contains(targetName)) return false
        val renamedProfiles = profiles.map { if (it == oldName) targetName else it }
        val calibrations = decodeProfileCalibrations(settings.profileCalibrationsJson).toMutableMap()
        val oldCalibration = calibrations.remove(oldName)
        if (!oldCalibration.isNullOrBlank()) calibrations[targetName] = oldCalibration
        val nextActive = if (settings.activeProfileName == oldName) targetName else settings.activeProfileName
        userSettingsDao.upsert(
            settings.copy(
                profileNamesCsv = renamedProfiles.joinToString(","),
                activeProfileName = nextActive,
                profileCalibrationsJson = encodeProfileCalibrations(calibrations),
            ),
        )
        return true
    }

    suspend fun archiveProfile(profileName: String): Boolean {
        val settings = normalizeProfileState(userSettingsDao.getSettings() ?: UserSettings())
        val profiles = parseProfileNames(settings.profileNamesCsv)
        if (!profiles.contains(profileName)) return false
        val remaining = profiles.filterNot { it == profileName }
        val nextProfiles = if (remaining.isEmpty()) listOf(DEFAULT_PROFILE_NAME) else remaining
        val calibrations = decodeProfileCalibrations(settings.profileCalibrationsJson).toMutableMap().apply { remove(profileName) }
        val nextActive = when {
            settings.activeProfileName != profileName -> settings.activeProfileName
            nextProfiles.contains(DEFAULT_PROFILE_NAME) -> DEFAULT_PROFILE_NAME
            else -> nextProfiles.first()
        }
        userSettingsDao.upsert(
            settings.copy(
                activeProfileName = nextActive,
                profileNamesCsv = nextProfiles.joinToString(","),
                profileCalibrationsJson = encodeProfileCalibrations(calibrations),
            ),
        )
        return true
    }

    suspend fun activeProfileName(): String = getActiveProfile().name

    suspend fun saveCalibrationForProfile(profileName: String, profile: UserBodyProfile?) {
        val settings = normalizeProfileState(userSettingsDao.getSettings() ?: UserSettings())
        val profiles = parseProfileNames(settings.profileNamesCsv)
        val safeProfileName = profileName.takeIf { it.isNotBlank() } ?: settings.activeProfileName
        val map = decodeProfileCalibrations(settings.profileCalibrationsJson).toMutableMap()
        if (profile == null) map.remove(safeProfileName) else map[safeProfileName] = profile.encode()
        val normalizedNames = if (profiles.contains(safeProfileName)) profiles else profiles + safeProfileName
        userSettingsDao.upsert(
            settings.copy(
                activeProfileName = safeProfileName,
                profileNamesCsv = normalizedNames.joinToString(","),
                profileCalibrationsJson = encodeProfileCalibrations(map),
            ),
        )
    }

    suspend fun getCalibrationForProfile(profileName: String): UserBodyProfile? {
        val settings = normalizeProfileState(userSettingsDao.getSettings() ?: UserSettings())
        val encoded = decodeProfileCalibrations(settings.profileCalibrationsJson)[profileName]
        return UserBodyProfile.decode(encoded)
    }

    suspend fun getCalibrationForActiveProfile(): UserBodyProfile? {
        val active = activeProfileName()
        return getCalibrationForProfile(active)
    }

    private fun normalizeProfileState(settings: UserSettings): UserSettings {
        val names = parseProfileNames(settings.profileNamesCsv)
        val active = settings.activeProfileName.takeIf { names.contains(it) } ?: names.first()
        var calibrationMap = decodeProfileCalibrations(settings.profileCalibrationsJson)
        val hasCanonicalCalibration = calibrationMap[active]?.isNotBlank() == true

        // Legacy read fallback: migrate one-time legacy userBodyProfileJson into canonical active profile calibration.
        if (!hasCanonicalCalibration && !settings.userBodyProfileJson.isNullOrBlank()) {
            calibrationMap = calibrationMap.toMutableMap().apply { put(active, settings.userBodyProfileJson) }
        }

        return settings.copy(
            activeProfileName = active,
            profileNamesCsv = names.joinToString(","),
            profileCalibrationsJson = encodeProfileCalibrations(calibrationMap),
        )
    }

    private fun profileStatusesFrom(settings: UserSettings): List<UserProfileStatus> {
        val names = parseProfileNames(settings.profileNamesCsv)
        val active = settings.activeProfileName.ifBlank { names.firstOrNull().orEmpty() }
        val calibrations = decodeProfileCalibrations(settings.profileCalibrationsJson)
        return names.map { name ->
            UserProfileStatus(name = name, isActive = name == active, isCalibrated = !calibrations[name].isNullOrBlank())
        }
    }

    private fun parseProfileNames(raw: String): List<String> {
        val parsed = raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
        return if (parsed.isEmpty()) listOf(DEFAULT_PROFILE_NAME) else parsed
    }

    private fun decodeProfileCalibrations(raw: String?): Map<String, String> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyMap()
        val obj = JSONObject(raw)
        val map = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val encoded = obj.optString(key, "")
            if (key.isNotBlank() && encoded.isNotBlank()) map[key] = encoded
        }
        map
    }.getOrDefault(emptyMap())

    private fun encodeProfileCalibrations(map: Map<String, String>): String? {
        if (map.isEmpty()) return null
        return JSONObject().apply { map.forEach { (profileName, encodedProfile) -> put(profileName, encodedProfile) } }.toString()
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
