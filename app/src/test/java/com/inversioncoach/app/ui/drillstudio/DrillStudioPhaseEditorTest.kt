package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillStudioPhaseEditorTest {
    private val baseJoints = mapOf("head" to JointPoint(0.5f, 0.2f))

    @Test
    fun addDuplicateDeletePhase_updatesPhaseAndPoseCollections() {
        val phases = listOf(DrillPhaseTemplate("phase_1", "Setup", 1))
        val poses = listOf(PhasePoseTemplate("phase_1", "Setup", baseJoints))

        val (afterAddPhases, afterAddPoses) = DrillStudioPhaseEditor.addPhase(phases, poses, baseJoints)
        assertEquals(2, afterAddPhases.size)
        assertEquals("phase_2", afterAddPhases.last().id)
        assertEquals(2, afterAddPoses.size)

        val (afterDupPhases, afterDupPoses) = DrillStudioPhaseEditor.duplicatePhase(afterAddPhases, afterAddPoses, "phase_1")
        assertTrue(afterDupPhases.any { it.id == "phase_1_copy" })
        assertTrue(afterDupPoses.any { it.phaseId == "phase_1_copy" })
        assertEquals(listOf(1, 2, 3), afterDupPhases.map { it.order })

        val (afterDeletePhases, afterDeletePoses) = DrillStudioPhaseEditor.deletePhase(afterDupPhases, afterDupPoses, "phase_1_copy")
        assertEquals(2, afterDeletePhases.size)
        assertTrue(afterDeletePoses.none { it.phaseId == "phase_1_copy" })
    }

    @Test
    fun updatePoseJoint_changesOnlyTargetPhaseJoint() {
        val poses = listOf(
            PhasePoseTemplate("phase_1", "Setup", mapOf("head" to JointPoint(0.5f, 0.2f))),
            PhasePoseTemplate("phase_2", "Finish", mapOf("head" to JointPoint(0.6f, 0.3f))),
        )

        val updated = DrillStudioPhaseEditor.updatePoseJoint(poses, "phase_2", "head", JointPoint(0.7f, 0.4f))

        assertEquals(0.5f, updated.first { it.phaseId == "phase_1" }.joints.getValue("head").x, 0.0001f)
        assertEquals(0.7f, updated.first { it.phaseId == "phase_2" }.joints.getValue("head").x, 0.0001f)
    }

    @Test
    fun duplicatePhase_generatesUniqueIdsAcrossMultipleDuplicates() {
        val phases = listOf(DrillPhaseTemplate("phase_1", "Setup", 1))
        val poses = listOf(PhasePoseTemplate("phase_1", "Setup", baseJoints))
        val (firstPhases, firstPoses) = DrillStudioPhaseEditor.duplicatePhase(phases, poses, "phase_1")
        val (secondPhases, secondPoses) = DrillStudioPhaseEditor.duplicatePhase(firstPhases, firstPoses, "phase_1")

        assertTrue(secondPhases.any { it.id == "phase_1_copy" })
        assertTrue(secondPhases.any { it.id == "phase_1_copy_2" })
        assertEquals(secondPhases.size, secondPoses.size)
    }

    @Test
    fun recoverSelectionAfterDelete_returnsExistingOrFirstPhase() {
        val ids = listOf("phase_1", "phase_2")
        assertEquals("phase_2", DrillStudioPhaseEditor.recoverSelectionAfterDelete(ids, ids, "phase_2"))
        assertEquals("phase_1", DrillStudioPhaseEditor.recoverSelectionAfterDelete(ids, ids, "phase_deleted"))
        assertEquals("phase_2", DrillStudioPhaseEditor.recoverSelectionAfterDelete(ids, listOf("phase_2"), "phase_deleted"))
    }

    @Test
    fun addPhase_generatesUniqueIdsAgainstExistingIds() {
        val phases = listOf(
            DrillPhaseTemplate("phase_1", "Setup", 1),
            DrillPhaseTemplate("phase_2", "Middle", 2),
            DrillPhaseTemplate("phase_3", "End", 3),
        )
        val poses = phases.map { PhasePoseTemplate(it.id, it.label, baseJoints) }
        val (nextPhases, _) = DrillStudioPhaseEditor.addPhase(phases, poses, baseJoints)
        assertTrue(nextPhases.any { it.id == "phase_4" })
    }
}
