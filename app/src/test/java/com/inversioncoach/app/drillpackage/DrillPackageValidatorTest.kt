package com.inversioncoach.app.drillpackage

import com.inversioncoach.app.drillpackage.model.DrillManifest
import com.inversioncoach.app.drillpackage.model.DrillPackage
import com.inversioncoach.app.drillpackage.model.PortableDrill
import com.inversioncoach.app.drillpackage.model.PortableJoint2D
import com.inversioncoach.app.drillpackage.model.PortablePhase
import com.inversioncoach.app.drillpackage.model.PortablePose
import com.inversioncoach.app.drillpackage.model.PortableViewType
import com.inversioncoach.app.drillpackage.model.SchemaVersion
import com.inversioncoach.app.drillpackage.validation.DrillPackageValidator
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillPackageValidatorTest {
    @Test
    fun validatesPortablePackageConstraints() {
        val pkg = DrillPackage(
            manifest = DrillManifest(
                packageId = "test",
                schemaVersion = SchemaVersion(1, 0),
                source = "test",
                exportedAtMs = 1,
            ),
            drills = listOf(
                PortableDrill(
                    id = "drill",
                    title = "Drill",
                    description = "",
                    family = "core",
                    movementType = "HOLD",
                    cameraView = PortableViewType.FRONT,
                    supportedViews = listOf(PortableViewType.FRONT),
                    comparisonMode = "POSE_TIMELINE",
                    normalizationBasis = "HIPS",
                    keyJoints = listOf("left_shoulder"),
                    tags = emptyList(),
                    phases = listOf(
                        PortablePhase(id = "setup", label = "Setup", order = 0),
                        PortablePhase(id = "hold", label = "Hold", order = 1),
                    ),
                    poses = listOf(
                        PortablePose(
                            phaseId = "setup",
                            name = "Setup",
                            viewType = PortableViewType.FRONT,
                            joints = mapOf("left_shoulder" to PortableJoint2D(0.2f, 0.4f, visibility = 0.9f, confidence = 0.8f)),
                        ),
                    ),
                    metricThresholds = emptyMap(),
                ),
            ),
        )

        assertTrue(DrillPackageValidator.validate(pkg).isEmpty())
    }

    @Test
    fun rejectsPrimaryViewMissingFromSupportedViews() {
        val pkg = DrillPackage(
            manifest = DrillManifest("test", SchemaVersion(1, 0), "test", 1),
            drills = listOf(
                PortableDrill(
                    id = "drill",
                    title = "Drill",
                    description = "",
                    family = "core",
                    movementType = "HOLD",
                    cameraView = PortableViewType.BACK,
                    supportedViews = listOf(PortableViewType.SIDE),
                    comparisonMode = "POSE_TIMELINE",
                    normalizationBasis = "HIPS",
                    keyJoints = listOf("left_shoulder"),
                    tags = emptyList(),
                    phases = listOf(PortablePhase(id = "setup", label = "Setup", order = 0)),
                    poses = emptyList(),
                    metricThresholds = emptyMap(),
                ),
            ),
        )

        val errors = DrillPackageValidator.validate(pkg)
        assertTrue(errors.any { it.contains("Primary camera view") })
    }

}
