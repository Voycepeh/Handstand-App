package com.inversioncoach.app.drills.studio

import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import org.json.JSONArray
import org.json.JSONObject

internal data class ReferenceTemplateDraftPayload(
    val phasePosesJson: String,
    val keyframesJson: String,
    val fpsHint: Int,
    val durationMs: Long,
)

internal object ReferenceTemplateDraftSerializer {
    fun toPayload(draft: DrillTemplate): ReferenceTemplateDraftPayload {
        val orderedPhases = draft.phases.sortedBy { it.order }
        val phasePoses = draft.skeletonTemplate.phasePoses
        val normalizedWindows = orderedPhases.associate { phase ->
            phase.id to (draft.calibration.phaseWindows[phase.id] ?: phase.progressWindow ?: PhaseWindow(0f, 1f))
        }

        val phaseArray = JSONArray()
        orderedPhases.forEachIndexed { index, phase ->
            val pose = phasePoses.firstOrNull { it.phaseId == phase.id }
            val duration = ((pose?.holdDurationMs ?: 0) + (pose?.transitionDurationMs ?: 700)).coerceAtLeast(100)
            phaseArray.put(
                JSONObject()
                    .put("phaseId", phase.id)
                    .put("sequenceIndex", index)
                    .put("durationMs", duration),
            )
        }

        val fallbackPhases = if (orderedPhases.isEmpty()) {
            phasePoses.mapIndexed { index, pose ->
                DrillPhaseTemplate(
                    id = pose.phaseId,
                    label = pose.name,
                    order = index + 1,
                    progressWindow = null,
                )
            }
        } else {
            orderedPhases
        }

        val keyframeArray = JSONArray()
        draft.skeletonTemplate.keyframes
            .sortedBy { it.progress }
            .forEachIndexed { index, keyframe ->
                keyframeArray.put(
                    JSONObject()
                        .put("phaseId", inferPhaseIdForProgress(keyframe.progress, fallbackPhases, normalizedWindows))
                        .put("progress", keyframe.progress.coerceIn(0f, 1f)),
                )
            }

        val durationMs = phasePoses.sumOf { ((it.holdDurationMs ?: 0) + it.transitionDurationMs).toLong() }.coerceAtLeast(1L)
        return ReferenceTemplateDraftPayload(
            phasePosesJson = JSONObject().put("phases", phaseArray).toString(),
            keyframesJson = JSONObject().put("keyframes", keyframeArray).toString(),
            fpsHint = draft.skeletonTemplate.framesPerSecond.coerceAtLeast(12),
            durationMs = durationMs,
        )
    }

    private fun inferPhaseIdForProgress(
        progress: Float,
        phases: List<DrillPhaseTemplate>,
        windows: Map<String, PhaseWindow>,
    ): String {
        val clamped = progress.coerceIn(0f, 1f)
        val hit = phases.firstOrNull { phase ->
            val window = windows[phase.id] ?: phase.progressWindow ?: return@firstOrNull false
            val start = window.start.coerceIn(0f, 1f)
            val end = window.end.coerceIn(start, 1f)
            clamped >= start && (clamped < end || (phase == phases.lastOrNull() && clamped == end))
        }
        return hit?.id ?: phases.lastOrNull()?.id ?: "phase_1"
    }
}
