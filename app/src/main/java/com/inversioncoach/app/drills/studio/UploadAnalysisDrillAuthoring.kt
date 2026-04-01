package com.inversioncoach.app.drills.studio

import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.CatalogMovementType
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.movementprofile.CameraViewConstraint
import com.inversioncoach.app.movementprofile.UploadedVideoAnalysisResult
import com.inversioncoach.app.ui.drillstudio.analysisPlaneForPrimaryView
import kotlin.math.max

/**
 * Source-of-truth authored content extracted from uploaded analysis for Drill Studio hydration.
 */
data class ExtractedDrillAuthoringPayload(
    val movementType: CatalogMovementType,
    val cameraView: CameraView,
    val supportedViews: List<CameraView>,
    val phases: List<DrillPhaseTemplate>,
    val phasePoses: List<PhasePoseTemplate>,
    val keyframes: List<SkeletonKeyframeTemplate>,
    val inferredMetadata: Map<String, String>,
    val cameraInferenceExplicit: Boolean,
)

object UploadAnalysisDrillAuthoringMapper {
    fun fromAnalysis(
        analysis: UploadedVideoAnalysisResult,
        movementType: CatalogMovementType,
    ): ExtractedDrillAuthoringPayload {
        val timeline = analysis.overlayTimeline
        val cameraView = toCameraView(analysis.inferredView)
        val phaseOrder = linkedSetOf<String>()
        analysis.phaseTimeline.forEach { (_, phase) -> phase.takeIf { it.isNotBlank() }?.let(phaseOrder::add) }
        timeline.forEach { point -> point.phaseId?.takeIf { it.isNotBlank() }?.let(phaseOrder::add) }
        if (phaseOrder.isEmpty()) {
            phaseOrder += if (movementType == CatalogMovementType.REP) "movement" else "hold"
        }

        val phaseIds = phaseOrder.toList()
        val totalDurationMs = ((timeline.lastOrNull()?.timestampMs ?: 0L) - (timeline.firstOrNull()?.timestampMs ?: 0L)).coerceAtLeast(1L)
        val phases = phaseIds.mapIndexed { index, rawId ->
            val cleanId = rawId.trim().lowercase().replace(' ', '_')
            val defaultStart = index.toFloat() / phaseIds.size.toFloat().coerceAtLeast(1f)
            val defaultEnd = ((index + 1).toFloat() / phaseIds.size.toFloat().coerceAtLeast(1f)).coerceAtMost(1f)
            val window = phaseWindowFor(rawId, analysis, defaultStart, defaultEnd)
            DrillPhaseTemplate(
                id = cleanId.ifBlank { "phase_${index + 1}" },
                label = rawId.replaceFirstChar { it.titlecase() },
                order = index + 1,
                progressWindow = window,
            )
        }

        val defaultJoints = timeline.firstOrNull()?.landmarks.orEmpty()
            .associate { (name, point) -> name to JointPoint(point.first, point.second) }
            .ifEmpty {
                mapOf(
                    "left_shoulder" to JointPoint(0.44f, 0.3f),
                    "right_shoulder" to JointPoint(0.56f, 0.3f),
                    "left_hip" to JointPoint(0.46f, 0.54f),
                    "right_hip" to JointPoint(0.54f, 0.54f),
                )
            }

        val phasePoses = phases.map { phase ->
            val sampled = timeline.firstOrNull { it.phaseId.equals(phase.label, ignoreCase = true) }
                ?: timeline.firstOrNull { it.phaseId.equals(phase.id, ignoreCase = true) }
            val joints = sampled?.landmarks?.associate { (name, point) -> name to JointPoint(point.first, point.second) } ?: defaultJoints
            val holdDurationMs = ((phase.progressWindow.end - phase.progressWindow.start).coerceAtLeast(0f) * totalDurationMs).toInt().takeIf { it > 0 }
            PhasePoseTemplate(
                phaseId = phase.id,
                name = phase.label,
                joints = joints,
                holdDurationMs = holdDurationMs,
                transitionDurationMs = max(200, ((totalDurationMs / phases.size.coerceAtLeast(1))).toInt()),
            )
        }

        val keyframes = buildList {
            phases.forEachIndexed { index, phase ->
                val poseJoints = phasePoses.firstOrNull { it.phaseId == phase.id }?.joints ?: defaultJoints
                add(
                    SkeletonKeyframeTemplate(
                        progress = (index.toFloat() / phases.lastIndex.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                        joints = poseJoints,
                    ),
                )
            }
            if (isNotEmpty() && last().progress < 1f) {
                add(last().copy(progress = 1f))
            }
        }

        return ExtractedDrillAuthoringPayload(
            movementType = movementType,
            cameraView = cameraView,
            supportedViews = listOf(cameraView),
            phases = phases,
            phasePoses = phasePoses,
            keyframes = keyframes,
            inferredMetadata = mapOf(
                "inferredView" to analysis.inferredView.name,
                "droppedFrames" to analysis.droppedFrames.toString(),
                "phaseCount" to phases.size.toString(),
            ),
            cameraInferenceExplicit = analysis.inferredView in setOf(
                CameraViewConstraint.SIDE_LEFT,
                CameraViewConstraint.SIDE_RIGHT,
                CameraViewConstraint.FRONT,
            ),
        )
    }

    fun applyToTemplate(seed: DrillTemplate, payload: ExtractedDrillAuthoringPayload): DrillTemplate {
        val destinationLooksDefaultLeft = seed.cameraView == CameraView.LEFT_PROFILE && seed.supportedViews == listOf(CameraView.LEFT_PROFILE)
        val shouldOverwriteCameraMetadata = payload.cameraInferenceExplicit || seed.supportedViews.isEmpty() || destinationLooksDefaultLeft
        val resolvedCameraView = if (shouldOverwriteCameraMetadata) payload.cameraView else seed.cameraView
        val resolvedSupportedViews = if (shouldOverwriteCameraMetadata) payload.supportedViews else seed.supportedViews
        return seed.copy(
            movementType = payload.movementType,
            cameraView = resolvedCameraView,
            supportedViews = resolvedSupportedViews,
            analysisPlane = analysisPlaneForPrimaryView(resolvedCameraView),
            phases = payload.phases,
            skeletonTemplate = seed.skeletonTemplate.copy(
                phasePoses = payload.phasePoses,
                keyframes = payload.keyframes,
            ),
            calibration = seed.calibration.copy(
                phaseWindows = payload.phases.associate { it.id to it.progressWindow },
            ),
        )
    }

    private fun toCameraView(view: CameraViewConstraint): CameraView = when (view) {
        CameraViewConstraint.FRONT -> CameraView.FRONT
        CameraViewConstraint.SIDE_RIGHT -> CameraView.RIGHT_PROFILE
        CameraViewConstraint.SIDE_LEFT -> CameraView.LEFT_PROFILE
        CameraViewConstraint.BACK -> CameraView.FRONT
        CameraViewConstraint.ANY -> CameraView.SIDE
    }

    private fun phaseWindowFor(
        phaseId: String,
        analysis: UploadedVideoAnalysisResult,
        fallbackStart: Float,
        fallbackEnd: Float,
    ): PhaseWindow {
        val total = analysis.phaseTimeline.size.coerceAtLeast(1)
        val first = analysis.phaseTimeline.indexOfFirst { it.second.equals(phaseId, ignoreCase = true) }
        if (first < 0) return PhaseWindow(fallbackStart, fallbackEnd)
        val last = analysis.phaseTimeline.indexOfLast { it.second.equals(phaseId, ignoreCase = true) }
        val start = (first.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val end = ((last + 1).toFloat() / total.toFloat()).coerceIn(start, 1f)
        return PhaseWindow(start, end)
    }
}
