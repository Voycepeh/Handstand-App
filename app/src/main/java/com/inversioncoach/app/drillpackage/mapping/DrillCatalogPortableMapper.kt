package com.inversioncoach.app.drillpackage.mapping

import com.inversioncoach.app.drillpackage.model.DrillManifest
import com.inversioncoach.app.drillpackage.model.DrillPackage
import com.inversioncoach.app.drillpackage.model.PortableDrill
import com.inversioncoach.app.drillpackage.model.PortableJoint2D
import com.inversioncoach.app.drillpackage.model.PortablePhase
import com.inversioncoach.app.drillpackage.model.PortablePose
import com.inversioncoach.app.drillpackage.model.PortableViewType
import com.inversioncoach.app.drillpackage.model.SchemaVersion
import com.inversioncoach.app.drills.catalog.CatalogCameraView
import com.inversioncoach.app.drills.catalog.DrillCatalog
import com.inversioncoach.app.drills.catalog.DrillPhaseTemplate
import com.inversioncoach.app.drills.catalog.DrillTemplate
import com.inversioncoach.app.drills.catalog.JointPoint
import com.inversioncoach.app.drills.catalog.PhasePoseTemplate
import com.inversioncoach.app.drills.catalog.PhaseWindow

object DrillCatalogPortableMapper {
    fun toPortablePackage(catalog: DrillCatalog, source: String = "android_catalog"): DrillPackage = DrillPackage(
        manifest = DrillManifest(
            packageId = catalog.catalogId,
            schemaVersion = SchemaVersion(major = catalog.schemaVersion),
            source = source,
            exportedAtMs = System.currentTimeMillis(),
        ),
        drills = catalog.drills.map(::toPortableDrill),
    )

    fun toPortableDrill(drill: DrillTemplate): PortableDrill = PortableDrill(
        id = drill.id,
        title = drill.title,
        description = drill.description,
        family = drill.family,
        movementType = drill.movementType.name,
        cameraView = drill.cameraView.toPortableView(),
        supportedViews = drill.supportedViews.map { it.toPortableView() }.ifEmpty { listOf(drill.cameraView.toPortableView()) }.distinct(),
        comparisonMode = drill.comparisonMode.name,
        normalizationBasis = drill.normalizationBasis.name,
        keyJoints = drill.keyJoints.map(PortableJointNames::canonicalize),
        tags = drill.tags,
        phases = drill.phases.map(::toPortablePhase),
        poses = drill.skeletonTemplate.phasePoses.map { it.toPortablePose(drill.cameraView.toPortableView()) },
        metricThresholds = drill.calibration.metricThresholds,
        extensions = mapOf(
            "legacyCatalogCameraView" to drill.cameraView.name,
            "legacyCatalogSupportedViews" to drill.supportedViews.joinToString(",") { it.name },
        ),
    )

    fun toCatalog(packageModel: DrillPackage): DrillCatalog = DrillCatalog(
        schemaVersion = packageModel.manifest.schemaVersion.major,
        catalogId = packageModel.manifest.packageId,
        drills = packageModel.drills.map(::toCatalogDrill),
    )

    fun toCatalogDrill(drill: PortableDrill): DrillTemplate {
        val sortedPhases = drill.phases.sortedBy { it.order }
        val restoredLegacyView = runCatching {
            drill.extensions["legacyCatalogCameraView"]?.let(CatalogCameraView::valueOf)
        }.getOrNull()

        if (drill.cameraView == PortableViewType.BACK && restoredLegacyView == null) {
            error("Portable BACK camera view cannot be mapped to current catalog schema without explicit legacyCatalogCameraView extension.")
        }

        val cameraView = restoredLegacyView ?: drill.cameraView.toCatalogView()
        val restoredSupportedViews = drill.extensions["legacyCatalogSupportedViews"]
            ?.split(',')
            ?.mapNotNull { runCatching { CatalogCameraView.valueOf(it) }.getOrNull() }
            .orEmpty()

        val supportedViews = if (restoredSupportedViews.isNotEmpty()) restoredSupportedViews else {
            drill.supportedViews.map { it.toCatalogView() }.ifEmpty { listOf(cameraView) }
        }

        return DrillTemplate(
            id = drill.id,
            title = drill.title,
            description = drill.description,
            family = drill.family,
            movementType = runCatching { com.inversioncoach.app.drills.catalog.CatalogMovementType.valueOf(drill.movementType) }
                .getOrDefault(com.inversioncoach.app.drills.catalog.CatalogMovementType.HOLD),
            tags = drill.tags,
            cameraView = cameraView,
            supportedViews = supportedViews,
            analysisPlane = com.inversioncoach.app.drills.catalog.CatalogAnalysisPlane.SAGITTAL,
            comparisonMode = runCatching { com.inversioncoach.app.drills.catalog.CatalogComparisonMode.valueOf(drill.comparisonMode) }
                .getOrDefault(com.inversioncoach.app.drills.catalog.CatalogComparisonMode.POSE_TIMELINE),
            keyJoints = drill.keyJoints,
            normalizationBasis = runCatching { com.inversioncoach.app.drills.catalog.CatalogNormalizationBasis.valueOf(drill.normalizationBasis) }
                .getOrDefault(com.inversioncoach.app.drills.catalog.CatalogNormalizationBasis.HIPS),
            phases = sortedPhases.map {
                DrillPhaseTemplate(
                    id = it.id,
                    label = it.label,
                    order = it.order,
                    progressWindow = PhaseWindow(start = it.windowStart, end = it.windowEnd),
                )
            },
            skeletonTemplate = com.inversioncoach.app.drills.catalog.SkeletonTemplate(
                id = "${drill.id}_skeleton",
                loop = drill.movementType == "HOLD",
                framesPerSecond = 15,
                phasePoses = drill.poses.map {
                    PhasePoseTemplate(
                        phaseId = it.phaseId,
                        name = it.name,
                        joints = it.joints.mapValues { (_, p) -> JointPoint(p.x, p.y) },
                        holdDurationMs = it.holdDurationMs,
                        transitionDurationMs = it.transitionDurationMs,
                    )
                },
                keyframes = emptyList(),
            ),
            calibration = com.inversioncoach.app.drills.catalog.CalibrationTemplate(
                metricThresholds = drill.metricThresholds,
                phaseWindows = sortedPhases.associate { phase ->
                    phase.id to PhaseWindow(phase.windowStart, phase.windowEnd)
                },
            ),
        )
    }

    private fun toPortablePhase(phase: DrillPhaseTemplate): PortablePhase = PortablePhase(
        id = phase.id,
        label = phase.label,
        order = phase.order,
        windowStart = phase.progressWindow.start,
        windowEnd = phase.progressWindow.end,
    )

    private fun PhasePoseTemplate.toPortablePose(defaultView: PortableViewType): PortablePose {
        val canonicalJoints = joints.mapKeys { (joint, _) -> PortableJointNames.canonicalize(joint) }
        return PortablePose(
            phaseId = phaseId,
            name = name,
            viewType = defaultView,
            joints = canonicalJoints
                .toSortedMap()
                .mapValues { (joint, p) ->
                    PortableJoint2D(
                        x = p.x,
                        y = p.y,
                        visibility = authoring?.jointConfidence?.get(joint),
                        confidence = authoring?.jointConfidence?.get(joint),
                    )
                },
            holdDurationMs = holdDurationMs,
            transitionDurationMs = transitionDurationMs,
        )
    }

    private fun CatalogCameraView.toPortableView(): PortableViewType = when (this) {
        CatalogCameraView.FRONT -> PortableViewType.FRONT
        CatalogCameraView.SIDE,
        CatalogCameraView.LEFT_PROFILE,
        CatalogCameraView.RIGHT_PROFILE,
        -> PortableViewType.SIDE
    }

    private fun PortableViewType.toCatalogView(): CatalogCameraView = when (this) {
        PortableViewType.FRONT -> CatalogCameraView.FRONT
        PortableViewType.SIDE -> CatalogCameraView.SIDE
        PortableViewType.BACK -> error("Portable BACK camera view requires explicit legacyCatalogCameraView extension for catalog mapping.")
    }
}
