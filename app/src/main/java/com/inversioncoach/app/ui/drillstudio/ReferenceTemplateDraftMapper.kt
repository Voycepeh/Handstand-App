package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.drills.catalog.AnalysisPlane
import com.inversioncoach.app.drills.catalog.CalibrationTemplate
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.drills.catalog.SkeletonTemplate
import com.inversioncoach.app.model.ReferenceTemplateRecord
import org.json.JSONObject

internal data class TemplateDraftMappingResult(
    val draft: DrillTemplate,
    val warnings: List<String> = emptyList(),
)

internal object ReferenceTemplateDraftMapper {
    fun toDraft(
        record: ReferenceTemplateRecord,
        seed: DrillTemplate?,
    ): TemplateDraftMappingResult {
        val phasePoses = parsePhasePoses(record.phasePosesJson)
        val keyframes = parseKeyframes(record.keyframesJson)

        val phases = derivePhases(phasePoses, keyframes, seed)
        val phaseWindows = derivePhaseWindows(phases, phasePoses, keyframes)
        val syntheticPoses = phases.mapIndexed { index, phase ->
            val sourceDuration = phasePoses.firstOrNull { it.phaseId == phase.id }?.durationMs
            PhasePoseTemplate(
                phaseId = phase.id,
                name = phase.label,
                joints = synthesizeJoints(record.id, phase.id, index),
                holdDurationMs = sourceDuration?.toInt()?.coerceAtLeast(0),
                transitionDurationMs = inferTransitionDurationMs(phasePoses, phase.id),
            )
        }

        val mappedKeyframes = buildKeyframes(phases, keyframes, syntheticPoses)
        val fallbackWarnings = mutableListOf<String>()
        if (phasePoses.isEmpty()) fallbackWarnings += "Template phase pose data was incomplete; reconstructed editable phases."
        if (keyframes.isEmpty()) fallbackWarnings += "Template keyframe data was incomplete; generated synthetic keyframes."

        val base = seed ?: blankBaseTemplate(record)
        val draft = base.copy(
            id = "draft_template_${record.id}_${System.currentTimeMillis()}",
            title = "${record.displayName} (Draft)",
            description = "Template-derived draft from ${record.displayName}",
            family = base.family.ifBlank { "Reference Template" },
            movementType = base.movementType,
            tags = (base.tags + "template" + "reference").distinct(),
            phases = phases,
            skeletonTemplate = SkeletonTemplate(
                id = "skel_template_${record.id}",
                loop = base.skeletonTemplate.loop,
                mirroredSupported = base.skeletonTemplate.mirroredSupported,
                framesPerSecond = (record.fpsHint ?: base.skeletonTemplate.framesPerSecond).coerceAtLeast(12),
                phasePoses = syntheticPoses,
                keyframes = mappedKeyframes,
            ),
            calibration = CalibrationTemplate(
                metricThresholds = base.calibration.metricThresholds,
                phaseWindows = phaseWindows,
            ),
        )
        return TemplateDraftMappingResult(draft = draft, warnings = fallbackWarnings)
    }

    private fun derivePhases(
        phasePoses: List<ParsedPhasePose>,
        keyframes: List<ParsedKeyframe>,
        seed: DrillTemplate?,
    ): List<DrillPhaseTemplate> {
        val phaseIds = linkedSetOf<String>()
        phasePoses.forEach { phaseIds += it.phaseId }
        keyframes.forEach { phaseIds += it.phaseId }
        if (phaseIds.isEmpty()) {
            seed?.phases?.forEach { phaseIds += it.id }
        }
        if (phaseIds.isEmpty()) {
            phaseIds += "setup"
        }
        val orderedIds = phasePoses.sortedBy { it.sequenceIndex }.map { it.phaseId }
            .ifEmpty { phaseIds.toList() }
            .let { ids -> (ids + phaseIds).distinct() }

        return orderedIds.mapIndexed { index, phaseId ->
            val label = phaseId
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
            val progressWindow = seed?.phases?.firstOrNull { it.id == phaseId }?.progressWindow
                ?: PhaseWindow(
                    start = index.toFloat() / orderedIds.size.coerceAtLeast(1),
                    end = ((index + 1).toFloat() / orderedIds.size.coerceAtLeast(1)).coerceAtMost(1f),
                )
            DrillPhaseTemplate(
                id = phaseId,
                label = label,
                order = index + 1,
                progressWindow = progressWindow,
            )
        }
    }

    private fun derivePhaseWindows(
        phases: List<DrillPhaseTemplate>,
        phasePoses: List<ParsedPhasePose>,
        keyframes: List<ParsedKeyframe>,
    ): Map<String, PhaseWindow> {
        if (phases.isEmpty()) return emptyMap()
        if (keyframes.isNotEmpty()) {
            val progressByPhase = keyframes
                .groupBy { it.phaseId }
                .mapValues { (_, frames) -> frames.minOf { it.progress } }
            return phases.mapIndexed { index, phase ->
                val start = progressByPhase[phase.id] ?: (index.toFloat() / phases.size)
                val end = progressByPhase[phases.getOrNull(index + 1)?.id] ?: if (index == phases.lastIndex) 1f else ((index + 1).toFloat() / phases.size)
                phase.id to PhaseWindow(start.coerceIn(0f, 1f), end.coerceIn(start.coerceIn(0f, 1f), 1f))
            }.toMap()
        }

        val totalMs = phasePoses.sumOf { it.durationMs }.coerceAtLeast(1L)
        var elapsed = 0L
        return phases.associate { phase ->
            val duration = phasePoses.firstOrNull { it.phaseId == phase.id }?.durationMs ?: (totalMs / phases.size.coerceAtLeast(1))
            val start = elapsed.toFloat() / totalMs.toFloat()
            elapsed += duration
            val end = elapsed.toFloat() / totalMs.toFloat()
            phase.id to PhaseWindow(start.coerceIn(0f, 1f), end.coerceIn(start.coerceIn(0f, 1f), 1f))
        }
    }

    private fun buildKeyframes(
        phases: List<DrillPhaseTemplate>,
        parsed: List<ParsedKeyframe>,
        poses: List<PhasePoseTemplate>,
    ): List<SkeletonKeyframeTemplate> {
        if (parsed.isNotEmpty()) {
            return parsed.sortedBy { it.progress }.mapIndexed { index, keyframe ->
                val joints = poses.firstOrNull { it.phaseId == keyframe.phaseId }?.joints ?: poses.getOrNull(index)?.joints ?: defaultJoints()
                SkeletonKeyframeTemplate(progress = keyframe.progress.coerceIn(0f, 1f), joints = joints)
            }.ifEmpty {
                listOf(SkeletonKeyframeTemplate(0f, defaultJoints()), SkeletonKeyframeTemplate(1f, defaultJoints()))
            }
        }
        if (poses.size <= 1) {
            val joints = poses.firstOrNull()?.joints ?: defaultJoints()
            return listOf(SkeletonKeyframeTemplate(0f, joints), SkeletonKeyframeTemplate(1f, joints))
        }
        return poses.mapIndexed { index, pose ->
            SkeletonKeyframeTemplate(
                progress = index.toFloat() / (phases.lastIndex.coerceAtLeast(1)).toFloat(),
                joints = pose.joints,
            )
        }
    }

    private fun parsePhasePoses(raw: String): List<ParsedPhasePose> = runCatching {
        val phases = JSONObject(raw).optJSONArray("phases") ?: return@runCatching emptyList()
        (0 until phases.length()).mapNotNull { index ->
            val item = phases.optJSONObject(index) ?: return@mapNotNull null
            val phaseId = item.optString("phaseId").ifBlank { null } ?: return@mapNotNull null
            ParsedPhasePose(
                phaseId = phaseId,
                sequenceIndex = item.optInt("sequenceIndex", index),
                durationMs = item.optLong("durationMs", 0L).coerceAtLeast(0L),
            )
        }
    }.getOrDefault(emptyList())

    private fun parseKeyframes(raw: String): List<ParsedKeyframe> = runCatching {
        val keyframes = JSONObject(raw).optJSONArray("keyframes") ?: return@runCatching emptyList()
        (0 until keyframes.length()).mapNotNull { index ->
            val item = keyframes.optJSONObject(index) ?: return@mapNotNull null
            val phaseId = item.optString("phaseId").ifBlank { null } ?: return@mapNotNull null
            ParsedKeyframe(
                phaseId = phaseId,
                progress = item.optDouble("progress", if (keyframes.length() <= 1) 0.0 else index.toDouble() / keyframes.length().toDouble()).toFloat(),
            )
        }
    }.getOrDefault(emptyList())

    private fun inferTransitionDurationMs(phasePoses: List<ParsedPhasePose>, phaseId: String): Int {
        val duration = phasePoses.firstOrNull { it.phaseId == phaseId }?.durationMs ?: return 700
        return (duration / 2L).toInt().coerceAtLeast(100)
    }

    private fun synthesizeJoints(templateId: String, phaseId: String, index: Int): Map<String, JointPoint> {
        val base = defaultJoints()
        val seed = (templateId + phaseId).hashCode().toUInt().toInt()
        val offsetX = (((seed % 7) - 3) * 0.008f) + (index % 3) * 0.004f
        val offsetY = ((((seed / 11) % 7) - 3) * 0.006f) - (index % 2) * 0.003f
        return base.mapValues { (_, point) ->
            JointPoint(
                x = (point.x + offsetX).coerceIn(0.05f, 0.95f),
                y = (point.y + offsetY).coerceIn(0.05f, 0.95f),
            )
        }
    }

    private fun defaultJoints(): Map<String, JointPoint> =
        DrillStudioPoseUtils.normalizeJointNames(DrillStudioPosePresets.neutralUpright.joints)

    private fun blankBaseTemplate(record: ReferenceTemplateRecord): DrillTemplate = DrillTemplate(
        id = record.id,
        title = record.displayName,
        description = record.displayName,
        family = "Reference Template",
        movementType = CatalogMovementType.HOLD,
        tags = listOf("reference", "template"),
        cameraView = CameraView.LEFT_PROFILE,
        supportedViews = listOf(CameraView.LEFT_PROFILE),
        analysisPlane = AnalysisPlane.SAGITTAL,
        comparisonMode = ComparisonMode.POSE_TIMELINE,
        keyJoints = listOf("shoulder_left", "shoulder_right", "hip_left", "hip_right"),
        phases = emptyList(),
        skeletonTemplate = SkeletonTemplate(
            id = "skel_${record.id}",
            loop = true,
            framesPerSecond = 24,
            keyframes = emptyList(),
        ),
        calibration = CalibrationTemplate(metricThresholds = emptyMap(), phaseWindows = emptyMap()),
    )

    private data class ParsedPhasePose(
        val phaseId: String,
        val sequenceIndex: Int,
        val durationMs: Long,
    )

    private data class ParsedKeyframe(
        val phaseId: String,
        val progress: Float,
    )
}
