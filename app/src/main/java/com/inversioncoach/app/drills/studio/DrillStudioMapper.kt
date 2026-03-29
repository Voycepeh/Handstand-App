package com.inversioncoach.app.drills.studio

import com.inversioncoach.app.drills.catalog.CalibrationTemplate
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhaseWindow
import com.inversioncoach.app.drills.catalog.SkeletonKeyframeTemplate
import com.inversioncoach.app.drills.catalog.SkeletonTemplate

object DrillStudioMapper {
    fun fromCatalog(drill: DrillTemplate): DrillStudioDocument = DrillStudioDocument(
        id = drill.id,
        seededCatalogDrillId = drill.id,
        displayName = drill.title,
        family = drill.family,
        movementType = drill.movementType,
        cameraView = drill.cameraView,
        supportedViews = drill.supportedViews,
        analysisPlane = drill.analysisPlane,
        comparisonMode = drill.comparisonMode,
        phases = drill.phases.map { phase ->
            DrillStudioPhase(
                id = phase.id,
                label = phase.label,
                order = phase.order,
                progressWindow = DrillStudioPhaseWindow(phase.progressWindow.start, phase.progressWindow.end),
                anchorKeyframeName = nearestKeyframeName(
                    drill.animationSpec.keyframes.map { it.name to it.progress },
                    midpoint = (phase.progressWindow.start + phase.progressWindow.end) / 2f,
                ),
            )
        },
        metricThresholds = drill.calibration.metricThresholds,
        animationSpec = drill.animationSpec,
    )

    fun toCatalog(document: DrillStudioDocument): DrillTemplate = DrillTemplate(
        id = document.id,
        title = document.displayName,
        family = document.family,
        movementType = document.movementType,
        tags = emptyList(),
        cameraView = document.cameraView,
        supportedViews = document.supportedViews,
        analysisPlane = document.analysisPlane,
        comparisonMode = document.comparisonMode,
        phases = document.phases.map { phase ->
            DrillPhaseTemplate(
                id = phase.id,
                label = phase.label,
                order = phase.order,
                progressWindow = PhaseWindow(phase.progressWindow.start, phase.progressWindow.end),
            )
        },
        skeletonTemplate = SkeletonTemplate(
            id = document.animationSpec.id,
            loop = document.animationSpec.loop,
            mirroredSupported = document.animationSpec.mirroredSupported,
            framesPerSecond = document.animationSpec.fpsHint,
            keyframes = document.animationSpec.keyframes.map { frame ->
                SkeletonKeyframeTemplate(
                    progress = frame.progress,
                    joints = frame.joints.mapKeys { (joint, _) -> joint.name }
                        .mapValues { (_, pt) -> JointPoint(pt.x, pt.y) },
                )
            },
        ),
        calibration = CalibrationTemplate(
            metricThresholds = document.metricThresholds,
            phaseWindows = document.phases.associate { it.id to PhaseWindow(it.progressWindow.start, it.progressWindow.end) },
        ),
    )

    private fun nearestKeyframeName(frames: List<Pair<String, Float>>, midpoint: Float): String? =
        frames.minByOrNull { (_, progress) -> kotlin.math.abs(progress - midpoint) }?.first
}
