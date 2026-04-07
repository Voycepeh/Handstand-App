package com.inversioncoach.app.drillpackage

import com.inversioncoach.app.drillpackage.importing.DrillPackageImportPipeline
import com.inversioncoach.app.drillpackage.importing.DrillPackageImportResult
import com.inversioncoach.app.drillpackage.io.DrillPackageJsonCodec
import com.inversioncoach.app.drillpackage.model.DrillManifest
import com.inversioncoach.app.drillpackage.model.DrillPackage
import com.inversioncoach.app.drillpackage.model.PortableDrill
import com.inversioncoach.app.drillpackage.model.PortableJoint2D
import com.inversioncoach.app.drillpackage.model.PortablePhase
import com.inversioncoach.app.drillpackage.model.PortablePose
import com.inversioncoach.app.drillpackage.model.PortableViewType
import com.inversioncoach.app.drillpackage.model.SchemaVersion
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillPackageImportPipelineTest {
    @Test
    fun parseAndValidateReturnsSuccessForValidPackage() {
        val result = DrillPackageImportPipeline.parseAndValidate(DrillPackageJsonCodec.encode(validPackage()))
        assertTrue(result is DrillPackageImportResult.Success)
    }

    @Test
    fun parseAndValidateReturnsValidationFailureForUnknownPhaseReference() {
        val invalid = validPackage().copy(
            drills = validPackage().drills.map { drill ->
                drill.copy(
                    poses = listOf(
                        PortablePose(
                            phaseId = "missing_phase",
                            name = "Missing",
                            viewType = PortableViewType.SIDE,
                            joints = mapOf("left_shoulder" to PortableJoint2D(0.4f, 0.3f)),
                        ),
                    ),
                )
            },
        )

        val result = DrillPackageImportPipeline.parseAndValidate(DrillPackageJsonCodec.encode(invalid))
        assertTrue(result is DrillPackageImportResult.ValidationFailure)
        assertTrue((result as DrillPackageImportResult.ValidationFailure).errors.any { it.contains("must reference a declared phase") })
    }

    private fun validPackage(): DrillPackage = DrillPackage(
        manifest = DrillManifest("catalog-v1", SchemaVersion(1, 0), "studio", 42L),
        drills = listOf(
            PortableDrill(
                id = "push_up",
                title = "Push-up",
                description = "",
                family = "push",
                movementType = "REP",
                cameraView = PortableViewType.SIDE,
                supportedViews = listOf(PortableViewType.SIDE),
                comparisonMode = "POSE_TIMELINE",
                normalizationBasis = "HIPS",
                keyJoints = listOf("left_shoulder"),
                tags = listOf("seeded"),
                phases = listOf(PortablePhase("setup", "Setup", 0)),
                poses = listOf(
                    PortablePose(
                        phaseId = "setup",
                        name = "setup",
                        viewType = PortableViewType.SIDE,
                        joints = mapOf("left_shoulder" to PortableJoint2D(0.4f, 0.3f)),
                    ),
                ),
                metricThresholds = emptyMap(),
            ),
        ),
    )
}
