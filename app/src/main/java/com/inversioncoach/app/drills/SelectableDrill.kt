package com.inversioncoach.app.drills

import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.model.DrillType

data class SelectableDrill(
    val id: String,
    val name: String,
    val description: String,
    val movementMode: String,
    val cameraView: String,
    val comparisonMode: String,
    val status: String,
    val source: String,
    val isArchived: Boolean,
    val isEditable: Boolean,
    val isReferenceEligible: Boolean,
    val legacyDrillType: DrillType,
    val previewSkeleton: SkeletonTemplate?,
)

fun DrillDefinitionRecord.toSelectableDrill(): SelectableDrill {
    val isArchived = status == DrillStatus.ARCHIVED
    return SelectableDrill(
        id = id,
        name = name,
        description = description,
        movementMode = movementMode,
        cameraView = cameraView,
        comparisonMode = cueConfigJson.comparisonModeOrDefault(),
        status = status,
        source = sourceType,
        isArchived = isArchived,
        isEditable = !isArchived,
        isReferenceEligible = status == DrillStatus.DRAFT || status == DrillStatus.READY,
        legacyDrillType = DrillDefinitionResolver.resolveLegacyDrillType(this),
        previewSkeleton = DrillStudioPayloadCodec.decodePreviewSkeleton(drillId = id, cueConfigJson = cueConfigJson),
    )
}

private fun String.comparisonModeOrDefault(): String =
    split('|')
        .firstOrNull { token -> token.startsWith("comparisonMode:") }
        ?.substringAfter(':')
        ?.ifBlank { null }
        ?: "POSE_TIMELINE"
