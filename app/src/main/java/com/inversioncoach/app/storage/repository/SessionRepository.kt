package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.model.AnnotatedExportStage
import com.inversioncoach.app.model.AnnotatedExportStatus
import com.inversioncoach.app.model.CalibrationConfigRecord
import com.inversioncoach.app.model.ReferenceAssetRecord
import com.inversioncoach.app.model.ReferenceTemplateRecord
import com.inversioncoach.app.model.MovementProfileRecord
import com.inversioncoach.app.model.CleanupStatus
import com.inversioncoach.app.model.CompressionStatus
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.model.RetainedAssetType
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.model.FrameMetricRecord
import com.inversioncoach.app.model.IssueEvent
import com.inversioncoach.app.model.ProfileCalibrationEntity
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionComparisonRecord
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.calibration.UserBodyProfile
import com.inversioncoach.app.drills.DrillDefinitionValidator
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.storage.SessionBlobStorage
import com.inversioncoach.app.storage.db.FrameMetricDao
import com.inversioncoach.app.storage.db.ProfileDao
import com.inversioncoach.app.storage.db.SessionDao
import com.inversioncoach.app.storage.db.SessionComparisonDao
import com.inversioncoach.app.storage.db.UserSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private const val STALE_EXPORT_RECONCILIATION_MS = 2 * 60 * 1000L
private const val DEFAULT_PROFILE_NAME = "Profile 1"

data class UserProfileStatus(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val isCalibrated: Boolean,
)

class SessionRepository(
    private val sessionDao: SessionDao,
    private val userSettingsDao: UserSettingsDao,
    private val frameMetricDao: FrameMetricDao,
    private val profileDao: ProfileDao,
    private val referenceTemplateDao: com.inversioncoach.app.storage.db.ReferenceTemplateDao,
    private val sessionComparisonDao: SessionComparisonDao,
    private val drillDefinitionDao: com.inversioncoach.app.storage.db.DrillDefinitionDao,
    private val referenceAssetDao: com.inversioncoach.app.storage.db.ReferenceAssetDao,
    private val movementProfileDao: com.inversioncoach.app.storage.db.MovementProfileDao,
    private val calibrationConfigDao: com.inversioncoach.app.storage.db.CalibrationConfigDao,
    private val sessionBlobStorage: SessionBlobStorage,
) {
    fun observeSessions(drillType: DrillType? = null): Flow<List<SessionRecord>> =
        if (drillType == null) sessionDao.observeAll() else sessionDao.observeByDrill(drillType)

    fun observeSession(sessionId: Long): Flow<SessionRecord?> = sessionDao.observeById(sessionId)
    fun observeReferenceTemplates(drillId: String? = null): Flow<List<ReferenceTemplateRecord>> =
        if (drillId == null) referenceTemplateDao.observeAll() else referenceTemplateDao.observeByDrillId(drillId)

    suspend fun getReferenceTemplate(templateId: String): ReferenceTemplateRecord? = referenceTemplateDao.getById(templateId)

    suspend fun saveReferenceTemplate(record: ReferenceTemplateRecord) = referenceTemplateDao.upsert(record)

    suspend fun saveSessionComparison(record: SessionComparisonRecord): Long = sessionComparisonDao.insert(record)

    fun observeSessionComparison(sessionId: Long): Flow<SessionComparisonRecord?> = sessionComparisonDao.observeLatestForSession(sessionId)
    suspend fun getLatestSessionComparison(sessionId: Long): SessionComparisonRecord? = sessionComparisonDao.getLatestForSession(sessionId)

    fun observeComparedSessionIds(): Flow<List<Long>> = sessionComparisonDao.observeComparedSessionIds()
    fun observeLatestComparisonScores(): Flow<Map<Long, Int>> =
        sessionComparisonDao.observeAll().map { rows ->
            rows.asSequence()
                .filter { it.sessionId != null }
                .groupBy { it.sessionId!! }
                .mapValues { (_, comparisons) -> comparisons.maxByOrNull { it.createdAtMs }?.overallSimilarityScore ?: 0 }
        }

    fun getAllDrills(): Flow<List<DrillDefinitionRecord>> = drillDefinitionDao.observeAll()
    fun getActiveDrills(): Flow<List<DrillDefinitionRecord>> = drillDefinitionDao.observeActive()
    suspend fun getDrill(drillId: String): DrillDefinitionRecord? = drillDefinitionDao.getById(drillId)
    suspend fun createDrill(record: DrillDefinitionRecord) = drillDefinitionDao.upsert(record)
    suspend fun updateDrill(record: DrillDefinitionRecord) = drillDefinitionDao.upsert(record)
    suspend fun validateAndMarkDrillReady(drillId: String): List<String> {
        val existing = drillDefinitionDao.getById(drillId) ?: return listOf("Drill not found.")
        val errors = DrillDefinitionValidator.validate(existing)
        if (errors.isEmpty()) {
            drillDefinitionDao.upsert(existing.copy(status = DrillStatus.READY, updatedAtMs = System.currentTimeMillis()))
        }
        return errors
    }
    suspend fun archiveDrill(drillId: String) {
        val existing = drillDefinitionDao.getById(drillId) ?: return
        drillDefinitionDao.upsert(existing.copy(status = DrillStatus.ARCHIVED, updatedAtMs = System.currentTimeMillis()))
    }
    suspend fun deleteDrill(drillId: String) {
        drillDefinitionDao.deleteById(drillId)
    }
    suspend fun seedDrills(records: List<DrillDefinitionRecord>) = drillDefinitionDao.upsertAll(records)

    suspend fun saveReferenceAsset(record: ReferenceAssetRecord) = referenceAssetDao.upsert(record)
    fun observeReferenceAssets(drillId: String): Flow<List<ReferenceAssetRecord>> = referenceAssetDao.observeByDrill(drillId)
    suspend fun saveMovementProfile(record: MovementProfileRecord) = movementProfileDao.upsert(record)
    suspend fun getMovementProfile(profileId: String): MovementProfileRecord? = movementProfileDao.getById(profileId)
    suspend fun saveCalibrationConfig(record: CalibrationConfigRecord) = calibrationConfigDao.upsert(record)
    fun observeCalibrationConfig(drillId: String): Flow<List<CalibrationConfigRecord>> = calibrationConfigDao.observeByDrill(drillId)
    fun getComparisonsForSession(sessionId: Long): Flow<List<SessionComparisonRecord>> = sessionComparisonDao.observeAllForSession(sessionId)
    fun getComparisonsForDrill(drillId: String): Flow<List<SessionComparisonRecord>> = sessionComparisonDao.observeByDrill(drillId)
    fun getTemplatesForDrill(drillId: String): Flow<List<ReferenceTemplateRecord>> = referenceTemplateDao.observeByDrillId(drillId)

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
            sessionComparisonDao.deleteBySessionId(session.id)
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
        sessionComparisonDao.deleteBySessionId(sessionId)
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
        sessionComparisonDao.deleteAll()
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
     * New code should use getActiveProfileCalibration()/saveCalibrationForActiveProfile().
     */
    suspend fun saveUserBodyProfile(profile: UserBodyProfile?) {
        val activeProfile = getActiveProfile() ?: return
        saveCalibration(activeProfile.id, profile)
    }

    /**
     * Legacy compatibility only: prefer getActiveProfileCalibration() for canonical profile-based lookup.
     */
    suspend fun getUserBodyProfile(): UserBodyProfile? = getActiveProfileCalibration()

    fun observeProfiles(): Flow<List<UserProfileStatus>> =
        profileDao.observeProfiles().map { profiles ->
            profiles.map { it.toStatus() }
        }

    fun observeProfileStatuses(): Flow<List<UserProfileStatus>> = observeProfiles()

    fun observeActiveProfile(): Flow<UserProfileStatus?> =
        profileDao.observeActiveProfile().map { it?.toStatus() }

    suspend fun listProfiles(): List<UserProfileStatus> =
        profileDao.observeProfiles().map { profiles -> profiles.map { it.toStatus() } }
            .firstOrNull()
            .orEmpty()

    suspend fun getActiveProfile(): UserProfileStatus? {
        val existing = profileDao.getActiveProfile()?.toStatus()
        if (existing != null) return existing

        val fallback = profileDao.getOldestUnarchivedProfile()
        if (fallback != null) {
            profileDao.setActiveProfile(fallback.id, System.currentTimeMillis())
            return profileDao.getActiveProfile()?.toStatus()
        }

        ensureDefaultProfileExists()
        return profileDao.getActiveProfile()?.toStatus()
    }

    suspend fun setActiveProfile(profileId: Long): Boolean {
        val updatedAtMs = System.currentTimeMillis()
        return profileDao.setActiveProfile(profileId = profileId, updatedAtMs = updatedAtMs)
    }

    suspend fun createProfile(displayName: String): Long? {
        val normalizedName = displayName.trim()
        if (normalizedName.isBlank()) return null
        val now = System.currentTimeMillis()
        val shouldActivate = getActiveProfile() == null
        val insertedId = runCatching {
            profileDao.insertProfile(
                com.inversioncoach.app.model.UserProfileEntity(
                    displayName = normalizedName,
                    isActive = shouldActivate,
                    isArchived = false,
                    createdAtMs = now,
                    updatedAtMs = now,
                ),
            )
        }.getOrNull() ?: return null
        return insertedId
    }

    suspend fun renameProfile(profileId: Long, displayName: String): Boolean {
        val normalizedName = displayName.trim()
        if (normalizedName.isBlank()) return false
        return runCatching {
            profileDao.renameProfile(profileId, normalizedName, System.currentTimeMillis()) > 0
        }.getOrDefault(false)
    }

    suspend fun archiveProfile(profileId: Long): Boolean {
        val profile = profileDao.getProfile(profileId) ?: return false
        val updatedAtMs = System.currentTimeMillis()
        val archived = profileDao.archiveProfile(profileId, updatedAtMs) > 0
        if (!archived) return false
        if (profile.isActive) {
            val fallback = profileDao.getOldestUnarchivedProfile()
            if (fallback != null) {
                profileDao.setActiveProfile(fallback.id, updatedAtMs)
            } else {
                createProfile(DEFAULT_PROFILE_NAME)
            }
        }
        return true
    }

    fun observeCalibration(profileId: Long): Flow<UserBodyProfile?> =
        profileDao.observeCalibration(profileId).map { calibration ->
            UserBodyProfile.decode(calibration?.calibrationPayloadJson)
        }

    suspend fun getCalibration(profileId: Long): UserBodyProfile? =
        UserBodyProfile.decode(profileDao.getCalibration(profileId)?.calibrationPayloadJson)

    suspend fun getActiveProfileCalibration(): UserBodyProfile? {
        val activeProfile = getActiveProfile() ?: return null
        return getCalibration(activeProfile.id)
    }

    suspend fun getActiveProfileCalibrationVersion(): Int? {
        val activeProfile = getActiveProfile() ?: return null
        return profileDao.getCalibration(activeProfile.id)?.profileVersion
    }

    suspend fun saveCalibration(profileId: Long, profile: UserBodyProfile?) {
        if (profile == null) {
            profileDao.deleteCalibration(profileId)
            return
        }
        val updatedAtMs = System.currentTimeMillis()
        val existingVersion = profileDao.getCalibration(profileId)?.profileVersion ?: 0
        profileDao.upsertCalibration(
            ProfileCalibrationEntity(
                profileId = profileId,
                profileVersion = existingVersion + 1,
                updatedAtMs = updatedAtMs,
                calibrationPayloadJson = profile.encode(),
                calibrationMethod = "structural_calibration",
            ),
        )
    }

    suspend fun saveCalibrationForActiveProfile(profile: UserBodyProfile?) {
        val activeProfile = getActiveProfile() ?: return
        saveCalibration(activeProfile.id, profile)
    }

    suspend fun hasCalibration(profileId: Long): Boolean = profileDao.hasCalibration(profileId)

    private suspend fun ensureDefaultProfileExists() {
        if (profileDao.countActiveProfiles() > 0) return
        val now = System.currentTimeMillis()
        profileDao.insertProfile(
            com.inversioncoach.app.model.UserProfileEntity(
                displayName = DEFAULT_PROFILE_NAME,
                isActive = true,
                isArchived = false,
                createdAtMs = now,
                updatedAtMs = now,
            ),
        )
    }

    private fun com.inversioncoach.app.storage.db.UserProfileWithCalibration.toStatus(): UserProfileStatus =
        UserProfileStatus(
            id = id,
            name = displayName,
            isActive = isActive,
            isCalibrated = hasCalibration,
        )

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
