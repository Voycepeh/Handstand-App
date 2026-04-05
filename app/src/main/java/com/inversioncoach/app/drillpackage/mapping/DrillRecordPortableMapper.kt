package com.inversioncoach.app.drillpackage.mapping

import com.inversioncoach.app.drillpackage.model.PortableDrill
import com.inversioncoach.app.drillpackage.model.PortablePhase
import com.inversioncoach.app.drillpackage.model.PortableViewType
import com.inversioncoach.app.drills.DrillCameraView
import com.inversioncoach.app.drills.DrillCueConfigCodec
import com.inversioncoach.app.drills.DrillSourceType
import com.inversioncoach.app.model.DrillDefinitionRecord

object DrillRecordPortableMapper {
    fun toPortableDrill(record: DrillDefinitionRecord): PortableDrill {
        val preservedPortable = PortableDrillLegacyPayloadCodec.decodeFromCueConfig(record.cueConfigJson)
        val phases = record.phaseSchemaJson.split('|').filter { it.isNotBlank() }
        val keyJoints = record.keyJointsJson.split('|').filter { it.isNotBlank() }
        val parsedCue = DrillCueConfigCodec.parse(record.cueConfigJson)

        val baseline = preservedPortable ?: PortableDrill(
            id = record.id,
            title = record.name,
            description = record.description,
            family = "runtime",
            movementType = record.movementMode,
            cameraView = record.cameraView.toPortableView(),
            supportedViews = listOf(record.cameraView.toPortableView()).distinct(),
            comparisonMode = parsedCue.comparisonMode,
            normalizationBasis = record.normalizationBasisJson,
            keyJoints = keyJoints.map(PortableJointNames::canonicalize),
            tags = listOf(record.sourceType),
            phases = phases.mapIndexed { index, phase ->
                PortablePhase(
                    id = phase,
                    label = phase.replaceFirstChar { it.uppercase() },
                    order = index,
                )
            },
            poses = emptyList(),
            metricThresholds = emptyMap(),
            extensions = emptyMap(),
        )

        return baseline.copy(
            id = record.id,
            title = record.name,
            description = record.description,
            movementType = record.movementMode,
            normalizationBasis = record.normalizationBasisJson,
            keyJoints = if (keyJoints.isNotEmpty()) keyJoints.map(PortableJointNames::canonicalize) else baseline.keyJoints,
            phases = if (phases.isNotEmpty()) phases.mapIndexed { index, phase ->
                baseline.phases.firstOrNull { it.id == phase }?.copy(order = index)
                    ?: PortablePhase(id = phase, label = phase.replaceFirstChar { it.uppercase() }, order = index)
            } else baseline.phases,
            cameraView = baseline.cameraView,
            supportedViews = baseline.supportedViews.ifEmpty { listOf(baseline.cameraView) }.distinct(),
            comparisonMode = baseline.comparisonMode,
            extensions = baseline.extensions + mapOf(
                "sourceType" to record.sourceType,
                "status" to record.status,
                "cueConfig" to record.cueConfigJson,
                "version" to record.version.toString(),
            ),
        )
    }

    fun toDrillDefinitionRecord(
        portable: PortableDrill,
        nowMs: Long,
        existing: DrillDefinitionRecord? = null,
    ): DrillDefinitionRecord {
        val phaseSchemaJson = portable.phases.sortedBy { it.order }.joinToString("|") { it.id }
        val keyJointsJson = portable.keyJoints.joinToString("|")
        val sourceType = portable.extensions["sourceType"] ?: existing?.sourceType ?: DrillSourceType.USER_CREATED
        val status = portable.extensions["status"] ?: existing?.status ?: "DRAFT"
        val version = portable.extensions["version"]?.toIntOrNull() ?: existing?.version ?: 1

        val cueConfigWithPayload = PortableDrillLegacyPayloadCodec.mergeIntoCueConfig(
            existingCueConfig = portable.extensions["cueConfig"] ?: existing?.cueConfigJson,
            portable = portable,
        )

        return DrillDefinitionRecord(
            id = portable.id,
            name = portable.title,
            description = portable.description,
            movementMode = portable.movementType,
            cameraView = portable.cameraView.toLegacyCameraView(),
            phaseSchemaJson = phaseSchemaJson,
            keyJointsJson = keyJointsJson,
            normalizationBasisJson = portable.normalizationBasis,
            cueConfigJson = cueConfigWithPayload,
            sourceType = sourceType,
            status = status,
            version = version,
            createdAtMs = existing?.createdAtMs ?: nowMs,
            updatedAtMs = nowMs,
        )
    }

    private fun String.toPortableView(): PortableViewType = when (this) {
        DrillCameraView.FRONT -> PortableViewType.FRONT
        DrillCameraView.BACK -> PortableViewType.BACK
        DrillCameraView.LEFT,
        DrillCameraView.RIGHT,
        DrillCameraView.SIDE,
        -> PortableViewType.SIDE
        DrillCameraView.FREESTYLE -> PortableViewType.SIDE
        else -> PortableViewType.SIDE
    }

    private fun PortableViewType.toLegacyCameraView(): String = when (this) {
        PortableViewType.FRONT -> DrillCameraView.FRONT
        PortableViewType.BACK -> DrillCameraView.BACK
        PortableViewType.SIDE -> DrillCameraView.SIDE
    }
}
