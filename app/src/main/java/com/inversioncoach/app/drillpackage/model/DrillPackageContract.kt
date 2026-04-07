package com.inversioncoach.app.drillpackage.model

/**
 * Canonical portable package contract constants shared across Android import/export boundaries.
 *
 * This contract is intentionally scoped to the Studio↔Android seam and should remain decoupled
 * from runtime-only drill internals.
 */
object DrillPackageContract {
    const val CURRENT_SCHEMA_MAJOR = 1
    const val CURRENT_SCHEMA_MINOR = 0

    const val COORDINATE_MIN = 0f
    const val COORDINATE_MAX = 1f

    fun isSchemaSupported(version: SchemaVersion): Boolean =
        version.major == CURRENT_SCHEMA_MAJOR && version.minor >= 0
}
