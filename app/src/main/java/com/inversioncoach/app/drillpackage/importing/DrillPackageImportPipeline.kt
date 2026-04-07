package com.inversioncoach.app.drillpackage.importing

import com.inversioncoach.app.drillpackage.io.DrillPackageJsonCodec
import com.inversioncoach.app.drillpackage.mapping.DrillCatalogPortableMapper
import com.inversioncoach.app.drillpackage.model.DrillPackage
import com.inversioncoach.app.drillpackage.validation.DrillPackageValidator
import com.inversioncoach.app.drills.catalog.DrillCatalog

/**
 * Import seam for Studio-authored portable packages into Android runtime catalog format.
 *
 * Keeps JSON decoding, contract validation, and catalog mapping in one place so UI surfaces
 * can consume a single outcome type.
 */
object DrillPackageImportPipeline {
    fun parseAndValidate(raw: String): DrillPackageImportResult {
        val decoded = runCatching { DrillPackageJsonCodec.decode(raw) }
            .getOrElse { error -> return DrillPackageImportResult.DecodeFailure(error.message ?: "Failed to decode drill package JSON") }

        val report = DrillPackageValidator.validateDetailed(decoded)
        if (!report.isValid) {
            return DrillPackageImportResult.ValidationFailure(
                errors = report.errors,
                warnings = report.warnings,
                decoded = decoded,
            )
        }

        val catalog = runCatching { DrillCatalogPortableMapper.toCatalog(decoded) }
            .getOrElse { error ->
                return DrillPackageImportResult.MappingFailure(
                    message = error.message ?: "Failed to map portable package into runtime catalog",
                    warnings = report.warnings,
                    decoded = decoded,
                )
            }

        return DrillPackageImportResult.Success(
            decoded = decoded,
            runtimeCatalog = catalog,
            warnings = report.warnings,
        )
    }
}

sealed interface DrillPackageImportResult {
    data class Success(
        val decoded: DrillPackage,
        val runtimeCatalog: DrillCatalog,
        val warnings: List<String>,
    ) : DrillPackageImportResult

    data class DecodeFailure(val message: String) : DrillPackageImportResult

    data class ValidationFailure(
        val errors: List<String>,
        val warnings: List<String>,
        val decoded: DrillPackage,
    ) : DrillPackageImportResult

    data class MappingFailure(
        val message: String,
        val warnings: List<String>,
        val decoded: DrillPackage,
    ) : DrillPackageImportResult
}
