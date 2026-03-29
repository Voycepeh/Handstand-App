package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate

object DrillStudioPhaseEditor {
    private fun normalizeOrders(phases: List<DrillPhaseTemplate>): List<DrillPhaseTemplate> =
        phases.mapIndexed { idx, phase -> phase.copy(order = idx + 1) }

    fun addPhase(phases: List<DrillPhaseTemplate>, poses: List<PhasePoseTemplate>, defaultJoints: Map<String, JointPoint>): Pair<List<DrillPhaseTemplate>, List<PhasePoseTemplate>> {
        val existingIds = phases.map { it.id }.toSet()
        var nextOrder = phases.size + 1
        var phaseId = "phase_$nextOrder"
        while (phaseId in existingIds) {
            nextOrder += 1
            phaseId = "phase_$nextOrder"
        }
        val phase = DrillPhaseTemplate(id = phaseId, label = "Phase $nextOrder", order = nextOrder)
        val baseJoints = poses.lastOrNull()?.joints ?: defaultJoints
        return normalizeOrders(phases + phase) to (poses + PhasePoseTemplate(phaseId = phaseId, name = phase.label, joints = baseJoints))
    }

    fun duplicatePhase(phases: List<DrillPhaseTemplate>, poses: List<PhasePoseTemplate>, phaseId: String): Pair<List<DrillPhaseTemplate>, List<PhasePoseTemplate>> {
        val phaseIndex = phases.indexOfFirst { it.id == phaseId }
        if (phaseIndex < 0) return phases to poses
        val phase = phases[phaseIndex]
        val existingIds = phases.map { it.id }.toSet()
        var suffix = 1
        var duplicateId = "${phase.id}_copy"
        while (duplicateId in existingIds) {
            suffix += 1
            duplicateId = "${phase.id}_copy_$suffix"
        }
        val duplicated = phase.copy(id = duplicateId, label = "${phase.label} Copy")
        val pose = poses.firstOrNull { it.phaseId == phaseId }
        val dupPose = pose?.copy(phaseId = duplicateId, name = duplicated.label)
        val nextPhases = phases.toMutableList().apply { add(phaseIndex + 1, duplicated) }
        val nextPoses = poses.toMutableList().apply {
            if (dupPose != null) add((phaseIndex + 1).coerceAtMost(size), dupPose)
        }
        return normalizeOrders(nextPhases) to nextPoses
    }

    fun deletePhase(phases: List<DrillPhaseTemplate>, poses: List<PhasePoseTemplate>, phaseId: String): Pair<List<DrillPhaseTemplate>, List<PhasePoseTemplate>> {
        if (phases.size <= 1) return phases to poses
        return normalizeOrders(phases.filterNot { it.id == phaseId }) to poses.filterNot { it.phaseId == phaseId }
    }

    fun updatePoseJoint(poses: List<PhasePoseTemplate>, phaseId: String, joint: String, point: JointPoint): List<PhasePoseTemplate> =
        poses.map { pose -> if (pose.phaseId == phaseId) pose.copy(joints = pose.joints + (joint to point)) else pose }

    fun recoverSelectionAfterDelete(
        remainingPhaseIds: List<String>,
        availablePosePhaseIds: List<String>,
        previousSelectedPhaseId: String?,
    ): String? {
        val recoverable = remainingPhaseIds.filter { it in availablePosePhaseIds }
        if (recoverable.isEmpty()) return null
        if (previousSelectedPhaseId != null && previousSelectedPhaseId in recoverable) return previousSelectedPhaseId
        return recoverable.first()
    }
}
