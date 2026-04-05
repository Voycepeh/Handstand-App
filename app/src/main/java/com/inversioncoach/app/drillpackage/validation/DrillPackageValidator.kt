package com.inversioncoach.app.drillpackage.validation

import com.inversioncoach.app.drillpackage.model.DrillPackage

object DrillPackageValidator {
    fun validate(pkg: DrillPackage): List<String> {
        val errors = mutableListOf<String>()
        if (pkg.manifest.packageId.isBlank()) errors += "Manifest packageId is required."
        if (pkg.manifest.schemaVersion.major <= 0) errors += "Schema version presence is required (major > 0)."

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
                pose.joints.forEach { (jointName, point) ->
                    if (jointName.isBlank()) errors += "Pose joint names must be non-empty for ${drill.id}."
                    if (point.x !in 0f..1f || point.y !in 0f..1f) {
                        errors += "Joint coordinates must be normalized for ${drill.id}:${pose.phaseId}:$jointName."
                    }
                    if (point.visibility != null && point.visibility !in 0f..1f) {
                        errors += "Joint visibility must be between 0 and 1 for ${drill.id}:${pose.phaseId}:$jointName."
                    }
                    if (point.confidence != null && point.confidence !in 0f..1f) {
                        errors += "Joint confidence must be between 0 and 1 for ${drill.id}:${pose.phaseId}:$jointName."
                    }
                }
            }
        }
        return errors
    }
}
