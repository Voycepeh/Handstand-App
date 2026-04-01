package com.inversioncoach.app.drills.studio

import com.inversioncoach.app.drills.DrillStatus
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.movementprofile.CameraViewConstraint
import com.inversioncoach.app.movementprofile.OverlayTimelinePoint
import com.inversioncoach.app.movementprofile.UploadedVideoAnalysisResult
import com.inversioncoach.app.ui.drillstudio.toDrillDefinitionRecord
import com.inversioncoach.app.ui.drillstudio.toDrillTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadAnalysisDrillAuthoringMapperTest {

    @Test
    fun `maps analysis into authored payload with phases poses and keyframes`() {
        val analysis = sampleAnalysis()

        val payload = UploadAnalysisDrillAuthoringMapper.fromAnalysis(
            analysis = analysis,
            movementType = CatalogMovementType.REP,
        )

        assertEquals(2, payload.phases.size)
        assertEquals(listOf("Setup", "Press"), payload.phases.map { it.label })
        assertEquals(2, payload.phasePoses.size)
        assertTrue(payload.keyframes.isNotEmpty())
        assertEquals("SIDE_LEFT", payload.inferredMetadata["inferredView"])
    }

    @Test
    fun `persisted authored payload hydrates drill studio template`() {
        val drillRecord = DrillDefinitionRecord(
            id = "drill_1",
            name = "Test",
            description = "",
            movementMode = "HOLD",
            cameraView = "LEFT",
            phaseSchemaJson = "setup",
            keyJointsJson = "left_shoulder|right_shoulder",
            normalizationBasisJson = "HIPS",
            cueConfigJson = "legacyDrillType:FREE_HANDSTAND",
            sourceType = "USER_CREATED",
            status = DrillStatus.READY,
            version = 1,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )

        val payload = UploadAnalysisDrillAuthoringMapper.fromAnalysis(sampleAnalysis(), CatalogMovementType.HOLD)
        val updatedTemplate = UploadAnalysisDrillAuthoringMapper.applyToTemplate(
            seed = drillRecord.toDrillTemplate(seed = null),
            payload = payload,
        )
        val updatedRecord = updatedTemplate.toDrillDefinitionRecord(
            existingId = drillRecord.id,
            existing = drillRecord,
            ready = true,
        )

        val hydrated = updatedRecord.toDrillTemplate(seed = null)
        assertEquals(payload.phases.map { it.label }, hydrated.phases.map { it.label })
        assertEquals(payload.phasePoses.map { it.phaseId }, hydrated.skeletonTemplate.phasePoses.map { it.phaseId })
        assertEquals(payload.keyframes.size, hydrated.skeletonTemplate.keyframes.size)
    }

    @Test
    fun `uncertain inferred view does not overwrite non default camera metadata`() {
        val drillRecord = DrillDefinitionRecord(
            id = "drill_camera",
            name = "Camera",
            description = "",
            movementMode = "HOLD",
            cameraView = "RIGHT",
            phaseSchemaJson = "setup",
            keyJointsJson = "left_shoulder|right_shoulder",
            normalizationBasisJson = "HIPS",
            cueConfigJson = "legacyDrillType:FREE_HANDSTAND",
            sourceType = "USER_CREATED",
            status = DrillStatus.READY,
            version = 1,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val seed = drillRecord.toDrillTemplate(seed = null).copy(
            cameraView = CameraView.RIGHT_PROFILE,
            supportedViews = listOf(CameraView.RIGHT_PROFILE),
        )
        val payload = UploadAnalysisDrillAuthoringMapper.fromAnalysis(
            sampleAnalysis(inferredView = CameraViewConstraint.ANY),
            CatalogMovementType.HOLD,
        )
        val updated = UploadAnalysisDrillAuthoringMapper.applyToTemplate(seed, payload)
        assertEquals(CameraView.RIGHT_PROFILE, updated.cameraView)
        assertEquals(listOf(CameraView.RIGHT_PROFILE), updated.supportedViews)
    }

    private fun sampleAnalysis(inferredView: CameraViewConstraint = CameraViewConstraint.SIDE_LEFT): UploadedVideoAnalysisResult = UploadedVideoAnalysisResult(
        inferredView = inferredView,
        phaseTimeline = listOf(
            0L to "setup",
            120L to "setup",
            240L to "press",
            360L to "press",
        ),
        overlayTimeline = listOf(
            OverlayTimelinePoint(
                timestampMs = 0L,
                landmarks = listOf("left_shoulder" to (0.4f to 0.3f), "right_shoulder" to (0.6f to 0.3f)),
                metrics = emptyMap(),
                phaseId = "setup",
                confidence = 0.8f,
            ),
            OverlayTimelinePoint(
                timestampMs = 320L,
                landmarks = listOf("left_shoulder" to (0.45f to 0.25f), "right_shoulder" to (0.55f to 0.25f)),
                metrics = emptyMap(),
                phaseId = "press",
                confidence = 0.82f,
            ),
        ),
        droppedFrames = 1,
        telemetry = emptyMap(),
        candidate = com.inversioncoach.app.movementprofile.MovementTemplateCandidate(
            id = "c1",
            sourceSessionId = "s1",
            tentativeName = "test",
            movementTypeGuess = com.inversioncoach.app.movementprofile.MovementType.HOLD,
            detectedView = CameraViewConstraint.SIDE_LEFT,
            keyJoints = setOf("left_shoulder"),
            candidatePhases = emptyList(),
            candidateRomMetrics = emptyMap(),
            thresholdSuggestions = emptyMap(),
            confidence = 0.7f,
            status = com.inversioncoach.app.movementprofile.TemplateCandidateStatus.READY,
            rationale = "",
        ),
    )
}
