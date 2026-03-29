package com.inversioncoach.app.drills.studio

import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow

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
        metricThresholds = drill.metricThresholds,
        animationSpec = drill.animationSpec,
    )

    fun toCatalog(document: DrillStudioDocument): DrillTemplate = DrillTemplate(
        id = document.id,
        title = document.displayName,
        family = document.family,
        movementType = document.movementType,
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
        metricThresholds = document.metricThresholds,
        animationSpec = document.animationSpec,
    )

    private fun nearestKeyframeName(frames: List<Pair<String, Float>>, midpoint: Float): String? =
        frames.minByOrNull { (_, progress) -> kotlin.math.abs(progress - midpoint) }?.first
}
