package com.inversioncoach.app.storage.repository

import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.drills.DrillMovementMode
import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.ui.drillstudio.toDrillDefinitionRecord
import java.util.UUID

internal data class UploadAuthoringWritebackResult(
    val drill: DrillDefinitionRecord,
    val created: Boolean,
)

internal interface UploadAuthoringDrillStore {
    suspend fun getDrill(drillId: String): DrillDefinitionRecord?
    suspend fun createDrill(record: DrillDefinitionRecord)
    suspend fun updateDrill(record: DrillDefinitionRecord)
}

internal class SessionRepositoryUploadAuthoringStore(
    private val repository: SessionRepository,
) : UploadAuthoringDrillStore {
    override suspend fun getDrill(drillId: String): DrillDefinitionRecord? = repository.getDrill(drillId)
    override suspend fun createDrill(record: DrillDefinitionRecord) = repository.createDrill(record)
    override suspend fun updateDrill(record: DrillDefinitionRecord) = repository.updateDrill(record)
}

internal suspend fun ensureDrillForUploadWriteback(
    store: UploadAuthoringDrillStore,
    preferredDrill: DrillDefinitionRecord?,
    preferredDrillId: String?,
    createDrillFromReferenceUpload: Boolean,
    pendingDrillName: String?,
): UploadAuthoringWritebackResult? {
    preferredDrill?.let { return UploadAuthoringWritebackResult(drill = it, created = false) }
    preferredDrillId?.let { id ->
        store.getDrill(id)?.let { return UploadAuthoringWritebackResult(drill = it, created = false) }
    }
    if (!createDrillFromReferenceUpload) return null

    val now = System.currentTimeMillis()
    val created = DrillDefinitionRecord(
        id = "user_drill_${UUID.randomUUID()}",
        name = pendingDrillName?.trim().takeUnless { it.isNullOrBlank() } ?: "Custom Drill",
        description = "Created from uploaded reference video.",
        movementMode = DrillMovementMode.HOLD,
        cameraView = DrillCameraView.FREESTYLE,
        phaseSchemaJson = "setup|hold",
        keyJointsJson = "left_shoulder|right_shoulder|left_hip|right_hip",
        normalizationBasisJson = "HIPS",
        cueConfigJson = "legacyDrillType:FREE_HANDSTAND",
        sourceType = DrillSourceType.USER_CREATED,
        status = DrillStatus.DRAFT,
        version = 1,
        createdAtMs = now,
        updatedAtMs = now,
    )
    store.createDrill(created)
    return UploadAuthoringWritebackResult(drill = created, created = true)
}

internal suspend fun persistUploadAuthoredDrillTemplate(
    store: UploadAuthoringDrillStore,
    record: DrillDefinitionRecord,
    template: DrillTemplate,
    ready: Boolean,
): DrillDefinitionRecord {
    val updated = template.toDrillDefinitionRecord(
        existingId = record.id,
        existing = record,
        ready = ready,
    )
    store.updateDrill(updated)
    return updated
}
