package com.inversioncoach.app.drillpackage.validation

import com.inversioncoach.app.drillpackage.mapping.PortablePoseSemantics
import com.inversioncoach.app.drillpackage.model.DrillPackage
import com.inversioncoach.app.drillpackage.model.DrillPackageContract

object DrillPackageValidator {
    fun validate(pkg: DrillPackage): List<String> = validateDetailed(pkg).errors

    fun validateDetailed(pkg: DrillPackage): DrillPackageValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (pkg.manifest.packageId.isBlank()) errors += "Manifest packageId is required."
        if (pkg.manifest.schemaVersion.major <= 0) errors += "Schema version presence is required (major > 0)."
        if (!DrillPackageContract.isSchemaSupported(pkg.manifest.schemaVersion)) {
            warnings += "Schema ${pkg.manifest.schemaVersion.token} is outside Android's current tested major version ${DrillPackageContract.CURRENT_SCHEMA_MAJOR}."
        }

        pkg.manifest.assets.forEach { asset ->
            if (asset.id.isBlank()) errors += "Asset id is required."
            if (asset.type.isBlank()) errors += "Asset type is required for ${asset.id}."
            if (asset.uri.isBlank()) errors += "Asset uri is required for ${asset.id}."
        }

        pkg.drills.forEach { drill ->
            if (drill.id.isBlank()) errors += "Drill id is required."
            if (drill.title.isBlank()) errors += "Drill title is required for ${drill.id}."

            if (drill.supportedViews.isEmpty()) {
                errors += "Supported views are required for ${drill.id}."
            }
            if (drill.supportedViews.distinct().size != drill.supportedViews.size) {
                errors += "Supported views must be unique for ${drill.id}."
            }
            if (drill.cameraView !in drill.supportedViews) {
                errors += "Primary camera view must be included in supported views for ${drill.id}."
            }

            val phaseIds = drill.phases.map { it.id }.toSet()
            val orders = drill.phases.map { it.order }
            if (orders.distinct().size != orders.size) {
                errors += "Phase order must be unique for ${drill.id}."
            }

            drill.phases.forEach { phase ->
                if (phase.id.isBlank()) errors += "Phase id is required for ${drill.id}."
                if (phase.windowStart !in 0f..1f || phase.windowEnd !in 0f..1f || phase.windowStart > phase.windowEnd) {
                    errors += "Phase windows must be normalized and ordered for ${drill.id}/${phase.id}."
                }
            }

            drill.poses.forEach { pose ->
                if (pose.phaseId.isBlank()) errors += "Pose phaseId is required for ${drill.id}."
                if (pose.phaseId !in phaseIds) {
                    errors += "Pose phaseId ${pose.phaseId} must reference a declared phase for ${drill.id}."
                }
                pose.joints.forEach { (jointName, point) ->
                    if (jointName.isBlank()) errors += "Pose joint names must be non-empty for ${drill.id}."
                    if (!PortablePoseSemantics.isNormalizedCoordinate(point.x) || !PortablePoseSemantics.isNormalizedCoordinate(point.y)) {
                        errors += "Joint coordinates must be normalized for ${drill.id}:${pose.phaseId}:$jointName."
                    }
                    if (point.visibility != null && !PortablePoseSemantics.isNormalizedCoordinate(point.visibility)) {
                        errors += "Joint visibility must be between 0 and 1 for ${drill.id}:${pose.phaseId}:$jointName."
                    }
                    if (point.confidence != null && !PortablePoseSemantics.isNormalizedCoordinate(point.confidence)) {
                        errors += "Joint confidence must be between 0 and 1 for ${drill.id}:${pose.phaseId}:$jointName."
                    }
                }
            }
        }
        return DrillPackageValidationReport(errors = errors, warnings = warnings)
    }
}

data class DrillPackageValidationReport(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}
