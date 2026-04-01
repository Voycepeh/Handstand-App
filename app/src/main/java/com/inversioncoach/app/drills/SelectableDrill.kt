package com.inversioncoach.app.drills

import com.inversioncoach.app.model.DrillDefinitionRecord

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
    )
}

private fun String.comparisonModeOrDefault(): String =
    split('|')
        .firstOrNull { token -> token.startsWith("comparisonMode:") }
        ?.substringAfter(':')
        ?.ifBlank { null }
        ?: "POSE_TIMELINE"
