package com.inversioncoach.app.storage.repository

import android.util.Log
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
import com.inversioncoach.app.model.RawPersistStatus
import com.inversioncoach.app.model.SessionRecord
import com.inversioncoach.app.model.SessionSource
import com.inversioncoach.app.model.SessionComparisonRecord
import com.inversioncoach.app.model.UploadJobPipelineType
import com.inversioncoach.app.model.UploadJobStatus
import com.inversioncoach.app.model.UserSettings
import com.inversioncoach.app.model.normalized
import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.drills.DrillMovementMode
import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.drills.DrillDefinitionValidator
import com.inversioncoach.app.drills.DrillDefinitionResolver
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.SelectableDrill
import com.inversioncoach.app.drills.toSelectableDrill
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.studio.ReferenceTemplateDraftSerializer
import com.inversioncoach.app.movementprofile.MovementProfileExtractor
import com.inversioncoach.app.movementprofile.ReferenceTemplateDefinition
import com.inversioncoach.app.movementprofile.ReferenceTemplateBuilder
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.storage.SessionBlobStorage
import com.inversioncoach.app.media.SessionMediaOwnership
import com.inversioncoach.app.storage.db.FrameMetricDao
import com.inversioncoach.app.storage.db.SessionDao
import com.inversioncoach.app.storage.db.SessionComparisonDao
import com.inversioncoach.app.storage.db.UserSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.UUID

private const val STALE_UPLOAD_HEARTBEAT_MS = 45 * 1000L
private const val UPLOAD_REPO_TAG = "UploadJobRepo"
private const val EXPORT_STATE_TAG = "ExportStateGuard"
internal fun projectSelectableDrills(drills: List<DrillDefinitionRecord>): List<SelectableDrill> =
    drills
        .associateBy { it.id }
        .values
        .map { it.toSelectableDrill() }
        .sortedWith(compareBy<SelectableDrill> { it.name.lowercase() }.thenBy { it.id })

class SessionRepository(
    private val sessionDao: SessionDao,
    private val userSettingsDao: UserSettingsDao,
    private val frameMetricDao: FrameMetricDao,
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
    fun observeHistorySessions(drillId: String? = null): Flow<List<SessionRecord>> =
        observeSessions().map { sessions -> sessions.filterForDrill(drillId) }

    fun observeReferenceTemplates(drillId: String? = null): Flow<List<ReferenceTemplateRecord>> =
        if (drillId == null) referenceTemplateDao.observeAll() else referenceTemplateDao.observeByDrillId(drillId)

    suspend fun getReferenceTemplate(templateId: String): ReferenceTemplateRecord? = referenceTemplateDao.getById(templateId)

    suspend fun getReferenceTemplateDefinition(templateId: String): ReferenceTemplateDefinition? =
        referenceTemplateDao.getById(templateId)?.let { ReferenceTemplatePersistenceCodec.decodeTemplateDefinition(it) }

    fun getReferenceProfileIds(record: ReferenceTemplateRecord): List<String> =
        ReferenceTemplatePersistenceCodec.decodeReferenceProfileIds(record.sourceProfileIdsJson)

    suspend fun saveReferenceTemplate(record: ReferenceTemplateRecord) = referenceTemplateDao.upsert(record)
    suspend fun updateTemplateFromDraft(
        templateId: String,
        draft: DrillTemplate,
        displayName: String? = null,
        setAsBaseline: Boolean? = null,
    ): ReferenceTemplateRecord? {
        val existing = referenceTemplateDao.getById(templateId) ?: return null
        val now = System.currentTimeMillis()
        val updated = buildUpdatedTemplateRecord(existing, draft, displayName, setAsBaseline, now)
        return persistTemplateWithBaselinePolicy(drillId = existing.drillId, template = updated)
    }

    suspend fun createTemplateFromDraft(
        drillId: String,
        draft: DrillTemplate,
        displayName: String,
        basedOnTemplateId: String? = null,
        setAsBaseline: Boolean = false,
    ): ReferenceTemplateRecord {
        val parent = basedOnTemplateId?.let { referenceTemplateDao.getById(it) }
        val now = System.currentTimeMillis()
        val created = buildNewTemplateRecord(
            drillId = drillId,
            draft = draft,
            displayName = displayName,
            parent = parent,
            setAsBaseline = setAsBaseline,
            now = now,
        )
        return persistTemplateWithBaselinePolicy(drillId = drillId, template = created)
    }
    fun listTemplatesForDrill(drillId: String): Flow<List<ReferenceTemplateRecord>> = referenceTemplateDao.observeByDrillId(drillId)
    fun getSessionsForDrill(drillId: String): Flow<List<SessionRecord>> = sessionDao.observeByDrillId(drillId)

    suspend fun createTemplateFromReferenceUpload(
        drillId: String,
        sourceProfile: MovementProfileRecord,
        title: String,
        sourceSessionId: Long? = null,
        isBaseline: Boolean = false,
    ): ReferenceTemplateRecord {
        val now = System.currentTimeMillis()
        val extractor = MovementProfileExtractor()
        val template = ReferenceTemplateBuilder().buildFromSingleReference(
            drillId = drillId,
            displayName = title,
            sourceProfileId = sourceProfile.id,
            snapshot = extractor.toSnapshot(sourceProfile),
            createdAtMs = now,
            sourceType = "REFERENCE_UPLOAD",
            sourceSessionId = sourceSessionId,
            isBaseline = isBaseline,
        )
        return persistTemplateWithBaselinePolicy(drillId = drillId, template = template)
    }

    suspend fun createDrillFromReferenceUpload(
        drillName: String,
        description: String,
        sourceProfile: MovementProfileRecord,
        sourceSessionId: Long? = null,
    ): Pair<DrillDefinitionRecord, ReferenceTemplateRecord> {
        val now = System.currentTimeMillis()
        val drillId = "user_drill_${UUID.randomUUID()}"
        val drill = DrillDefinitionRecord(
            id = drillId,
            name = drillName,
            description = description,
            movementMode = DrillMovementMode.HOLD,
            cameraView = DrillCameraView.FREESTYLE,
            phaseSchemaJson = "setup|stack|hold",
            keyJointsJson = "shoulders|hips|ankles",
            normalizationBasisJson = "hips",
            cueConfigJson = "legacyDrillType:FREE_HANDSTAND",
            sourceType = DrillSourceType.USER_CREATED,
            status = DrillStatus.DRAFT,
            version = 1,
            createdAtMs = now,
            updatedAtMs = now,
        )
        drillDefinitionDao.upsert(drill)
        val linkedProfile = sourceProfile.copy(drillId = drillId)
        movementProfileDao.upsert(linkedProfile)
        val template = createTemplateFromReferenceUpload(
            drillId = drillId,
            sourceProfile = linkedProfile,
            title = "$drillName Reference",
            sourceSessionId = sourceSessionId,
            isBaseline = true,
        )
        return drill to template
    }

    suspend fun promoteSessionToReference(
        sessionId: Long,
        targetDrillId: String,
        referenceName: String? = null,
        setAsBaseline: Boolean = false,
    ): ReferenceTemplateRecord? {
        val profile = movementProfileDao.getByAssetId("asset-$sessionId") ?: movementProfileDao.getById("profile-$sessionId") ?: return null
        val session = sessionDao.getById(sessionId) ?: return null
        val drill = drillDefinitionDao.getById(targetDrillId) ?: return null
        val resolvedReferenceName = referenceName?.takeIf { it.isNotBlank() }
            ?: session.title.takeIf { it.isNotBlank() }?.let { "$it Reference" }
            ?: "Reference ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}"
        val template = createTemplateFromReferenceUpload(
            drillId = drill.id,
            sourceProfile = profile,
            title = resolvedReferenceName,
            sourceSessionId = sessionId,
            isBaseline = false,
        ).copy(sourceType = "SESSION_PROMOTION", sourceSessionId = sessionId)
        referenceTemplateDao.upsert(template)
        val updatedSession = when {
            session.drillId.isNullOrBlank() -> session.copy(drillId = targetDrillId, referenceTemplateId = template.id)
            session.drillId == targetDrillId -> session.copy(referenceTemplateId = template.id)
            else -> session.copy(referenceTemplateId = template.id)
        }
        sessionDao.upsert(updatedSession)
        return if (setAsBaseline) setBaselineTemplateForDrill(targetDrillId, template = template) else template
    }

    @Deprecated("Use promoteSessionToReference with explicit targetDrillId.")
    suspend fun promoteSessionToTemplate(
        sessionId: Long,
        drillId: String,
        title: String,
        setAsBaseline: Boolean = false,
    ): ReferenceTemplateRecord? = promoteSessionToReference(
        sessionId = sessionId,
        targetDrillId = drillId,
        referenceName = title,
        setAsBaseline = setAsBaseline,
    )

    suspend fun setBaselineTemplateForDrill(
        drillId: String,
        templateId: String? = null,
        template: ReferenceTemplateRecord? = null,
    ): ReferenceTemplateRecord? {
        val resolved = template ?: templateId?.let { referenceTemplateDao.getById(it) } ?: return null
        if (resolved.drillId != drillId) return null
        val updated = resolved.copy(isBaseline = true, updatedAtMs = System.currentTimeMillis())
        referenceTemplateDao.setBaselineForDrill(drillId, updated)
        return updated
    }

    private suspend fun persistTemplateWithBaselinePolicy(
        drillId: String,
        template: ReferenceTemplateRecord,
    ): ReferenceTemplateRecord {
        return if (template.isBaseline) {
            setBaselineTemplateForDrill(drillId = drillId, template = template) ?: template
        } else {
            referenceTemplateDao.upsert(template)
            template
        }
    }

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
    fun observeDrillLibrary(): Flow<List<SelectableDrill>> =
        drillDefinitionDao.observeAll().map(::projectSelectableDrills)

    fun observeSelectableTrainingDrills(): Flow<List<SelectableDrill>> =
        observeDrillLibrary().map { drills -> drills.filterNot { it.isArchived } }

    fun observeManageDrills(): Flow<List<SelectableDrill>> = observeSelectableTrainingDrills()

    fun observeDrillStudioDrills(): Flow<List<SelectableDrill>> =
        observeDrillLibrary().map { drills -> drills.filter { it.isEditable } }

    fun observeReferenceEligibleDrills(): Flow<List<SelectableDrill>> =
        observeDrillLibrary().map { drills ->
            drills.filter { drill -> drill.isReferenceEligible && !drill.isArchived }
        }
    suspend fun getDrill(drillId: String): DrillDefinitionRecord? = drillDefinitionDao.getById(drillId)
    suspend fun resolveDrillIdForLegacyType(drillType: DrillType): String? {
        val drills = drillDefinitionDao.observeAll().firstOrNull().orEmpty()
        return drills.firstOrNull { DrillDefinitionResolver.resolveLegacyDrillType(it) == drillType }?.id
    }
    suspend fun getActiveTemplateForDrill(drillId: String): ReferenceTemplateRecord? =
        DrillCurrentReferenceResolver.resolve(referenceTemplateDao.observeByDrillId(drillId).firstOrNull().orEmpty())
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
    fun getCurrentReferenceForDrill(drillId: String): Flow<ReferenceTemplateRecord?> =
        referenceTemplateDao.observeByDrillId(drillId).map(DrillCurrentReferenceResolver::resolve)
    fun getReferenceTemplatesForDrill(drillId: String): Flow<List<ReferenceTemplateRecord>> = referenceTemplateDao.observeByDrillId(drillId)
    fun getRecentSessionsForDrill(drillId: String): Flow<List<SessionRecord>> = sessionDao.observeByDrillId(drillId)

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
        val rawUri = SessionMediaOwnership.canonicalRawUri(session)
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
        if (session.annotatedExportStatus != status) {
            Log.i(
                EXPORT_STATE_TAG,
                "status_transition sessionId=$sessionId from=${session.annotatedExportStatus} to=$status",
            )
        }
        sessionDao.upsert(
            session.copy(
                annotatedExportStatus = status,
                annotatedExportLastUpdatedAt = System.currentTimeMillis(),
            ),
        )
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
                uploadJobUpdatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markUploadJobStarted(
        sessionId: Long,
        ownerToken: String,
        pipelineType: UploadJobPipelineType = UploadJobPipelineType.UPLOADED_VIDEO_ANALYSIS,
        stageLabel: String? = null,
        detail: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(
            session.copy(
                uploadJobPipelineType = pipelineType,
                uploadJobStatus = UploadJobStatus.PROCESSING,
                uploadJobOwnerToken = ownerToken,
                uploadJobStartedAtMs = now,
                uploadJobUpdatedAtMs = now,
                uploadJobHeartbeatAtMs = now,
                uploadJobTerminalOutcome = null,
                uploadJobFailureReason = null,
                uploadPipelineStageLabel = stageLabel ?: session.uploadPipelineStageLabel,
                uploadProgressDetail = detail ?: session.uploadProgressDetail,
            ),
        )
    }

    suspend fun markUploadJobHeartbeat(
        sessionId: Long,
        stageLabel: String? = null,
        detail: String? = null,
        processedFrames: Int? = null,
        totalFrames: Int? = null,
        timestampMs: Long? = null,
    ) {
        val now = System.currentTimeMillis()
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(
            session.copy(
                uploadJobStatus = UploadJobStatus.PROCESSING,
                uploadJobUpdatedAtMs = now,
                uploadJobHeartbeatAtMs = now,
                uploadPipelineStageLabel = stageLabel ?: session.uploadPipelineStageLabel,
                uploadProgressDetail = detail ?: session.uploadProgressDetail,
                uploadAnalysisProcessedFrames = processedFrames ?: session.uploadAnalysisProcessedFrames,
                uploadAnalysisTotalFrames = totalFrames ?: session.uploadAnalysisTotalFrames,
                uploadAnalysisTimestampMs = timestampMs ?: session.uploadAnalysisTimestampMs,
            ),
        )
    }

    suspend fun markUploadJobTerminal(
        sessionId: Long,
        status: UploadJobStatus,
        outcome: String,
        failureReason: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(
            session.copy(
                uploadJobStatus = status,
                uploadJobUpdatedAtMs = now,
                uploadJobHeartbeatAtMs = now,
                uploadJobTerminalOutcome = outcome,
                uploadJobFailureReason = failureReason,
                uploadJobOwnerToken = null,
            ),
        )
    }

    suspend fun getActiveUploadedSession(): SessionRecord? = sessionDao.getActiveUploadSession()

    suspend fun reconcileActiveUploadJobs(
        hasActiveWorker: Boolean,
        reason: String,
    ): SessionRecord? {
        val active = sessionDao.getActiveUploadSession(source = SessionSource.UPLOADED_VIDEO, status = UploadJobStatus.PROCESSING) ?: return null
        val heartbeat = active.uploadJobHeartbeatAtMs ?: active.uploadJobUpdatedAtMs ?: active.annotatedExportLastUpdatedAt ?: 0L
        val now = System.currentTimeMillis()
        val stale = heartbeat > 0 && now - heartbeat >= STALE_UPLOAD_HEARTBEAT_MS
        Log.i(
            UPLOAD_REPO_TAG,
            "reconcile sessionId=${active.id} hasActiveWorker=$hasActiveWorker stale=$stale heartbeatAgeMs=${now - heartbeat} reason=$reason",
        )
        if (hasActiveWorker || !stale) return active
        Log.w(UPLOAD_REPO_TAG, "stall_detected sessionId=${active.id} reason=$reason")
        sessionDao.upsert(
            active.copy(
                uploadJobStatus = UploadJobStatus.STALLED,
                uploadJobUpdatedAtMs = now,
                uploadJobTerminalOutcome = "stalled",
                uploadJobFailureReason = "Heartbeat timed out ($reason)",
                uploadJobOwnerToken = null,
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                annotatedExportFailureReason = "UPLOAD_JOB_STALLED",
                annotatedExportFailureDetail = "Processing stopped unexpectedly",
                annotatedExportStage = AnnotatedExportStage.FAILED,
            ),
        )
        return sessionDao.getById(active.id)
    }

    suspend fun reconcileStaleProcessingState(sessionId: Long, hasActiveExportJob: Boolean): Boolean {
        return recoverStaleAnnotatedExportState(
            sessionId = sessionId,
            hasActiveExportWork = hasActiveExportJob,
            trigger = "legacy_reconcile_call",
        )
    }

    suspend fun recoverStaleAnnotatedExportState(
        sessionId: Long,
        hasActiveExportWork: Boolean,
        trigger: String,
    ): Boolean {
        val session = sessionDao.getById(sessionId) ?: return false
        val isStale =
            session.annotatedExportStatus in setOf(
                AnnotatedExportStatus.VALIDATING_INPUT,
                AnnotatedExportStatus.PROCESSING,
                AnnotatedExportStatus.PROCESSING_SLOW,
            ) &&
                session.annotatedVideoUri.isNullOrBlank() &&
                !hasActiveExportWork
        if (!isStale) return false
        val reason = "EXPORT_INTERRUPTED_OR_STALE"
        Log.w(
            EXPORT_STATE_TAG,
            "stale_recovery sessionId=$sessionId trigger=$trigger status=${session.annotatedExportStatus} reason=$reason rawReady=${session.rawPersistStatus == RawPersistStatus.SUCCEEDED}",
        )
        sessionDao.upsert(
            session.copy(
                annotatedExportStatus = AnnotatedExportStatus.ANNOTATED_FAILED,
                annotatedExportFailureReason = reason,
                annotatedExportFailureDetail = "Recovered stale in-flight export ($trigger)",
                annotatedExportStage = AnnotatedExportStage.FAILED,
                annotatedExportPercent = 100,
                annotatedExportLastUpdatedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun recoverStaleAnnotatedExports(
        activeExportSessionIds: Set<Long>,
        trigger: String,
    ): Int {
        val sessions = sessionDao.getAllOldestFirst()
        var recovered = 0
        sessions.forEach { session ->
            val changed = recoverStaleAnnotatedExportState(
                sessionId = session.id,
                hasActiveExportWork = activeExportSessionIds.contains(session.id),
                trigger = trigger,
            )
            if (changed) recovered += 1
        }
        if (recovered > 0) {
            Log.i(
                EXPORT_STATE_TAG,
                "bulk_stale_recovery trigger=$trigger recovered=$recovered activeCount=${activeExportSessionIds.size}",
            )
        }
        return recovered
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
        sessionBlobStorage.cleanupStaleCacheArtifacts()
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
        userSettingsDao.observeSettings().map { (it ?: UserSettings()).normalized() }

    suspend fun saveSettings(settings: UserSettings) {
        val normalizedSettings = settings.normalized()
        userSettingsDao.upsert(normalizedSettings)
        enforceStorageLimit(normalizedSettings.maxStorageMb)
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

    private suspend fun enforceConfiguredStorageLimit() {
        val settings = (userSettingsDao.getSettings() ?: UserSettings()).normalized()
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

internal fun buildUpdatedTemplateRecord(
    existing: ReferenceTemplateRecord,
    draft: DrillTemplate,
    displayName: String?,
    setAsBaseline: Boolean?,
    now: Long,
): ReferenceTemplateRecord {
    val payload = ReferenceTemplateDraftSerializer.toPayload(draft)
    return existing.copy(
        displayName = displayName ?: existing.displayName,
        title = draft.title.ifBlank { displayName ?: existing.displayName },
        phasePosesJson = payload.phasePosesJson,
        keyframesJson = payload.keyframesJson,
        fpsHint = payload.fpsHint,
        durationMs = payload.durationMs,
        updatedAtMs = now,
        isBaseline = setAsBaseline ?: existing.isBaseline,
    )
}

internal fun buildNewTemplateRecord(
    drillId: String,
    draft: DrillTemplate,
    displayName: String,
    parent: ReferenceTemplateRecord?,
    setAsBaseline: Boolean,
    now: Long,
): ReferenceTemplateRecord {
    val payload = ReferenceTemplateDraftSerializer.toPayload(draft)
    return ReferenceTemplateRecord(
        id = "template_${UUID.randomUUID()}",
        drillId = drillId,
        displayName = displayName,
        templateType = parent?.templateType ?: "SINGLE_REFERENCE",
        sourceType = parent?.sourceType ?: "DRILL_STUDIO",
        sourceSessionId = parent?.sourceSessionId,
        title = draft.title.ifBlank { displayName },
        phasePosesJson = payload.phasePosesJson,
        keyframesJson = payload.keyframesJson,
        fpsHint = payload.fpsHint,
        durationMs = payload.durationMs,
        updatedAtMs = now,
        isBaseline = setAsBaseline,
        sourceProfileIdsJson = parent?.sourceProfileIdsJson ?: "",
        checkpointJson = parent?.checkpointJson ?: "{}",
        toleranceJson = parent?.toleranceJson ?: "{}",
        createdAtMs = now,
    )
}
