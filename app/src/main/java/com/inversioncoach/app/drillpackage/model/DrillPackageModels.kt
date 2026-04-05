package com.inversioncoach.app.drillpackage.model

data class SchemaVersion(
    val major: Int,
    val minor: Int = 0,
) {
    val token: String = "$major.$minor"
}

/**
 * Portable camera perspectives are neutral and do not encode laterality.
 */
enum class PortableViewType {
    FRONT,
    SIDE,
    BACK,
}

data class PortableAssetRef(
    val id: String,
    val type: String,
    val uri: String,
)

data class PortableJoint2D(
    val x: Float,
    val y: Float,
    val visibility: Float? = null,
    val confidence: Float? = null,
)

/**
 * PortablePose stores joints as a canonical-name map for stable order-independent representation.
 */
data class PortablePose(
    val phaseId: String,
    val name: String,
    val viewType: PortableViewType,
    val joints: Map<String, PortableJoint2D>,
    val holdDurationMs: Int? = null,
    val transitionDurationMs: Int = 700,
)

data class PortablePhase(
    val id: String,
    val label: String,
    val order: Int,
    val windowStart: Float = 0f,
    val windowEnd: Float = 1f,
)

data class PortableDrill(
    val id: String,
    val title: String,
    val description: String,
    val family: String,
    val movementType: String,
    val cameraView: PortableViewType,
    val supportedViews: List<PortableViewType>,
    val comparisonMode: String,
    val normalizationBasis: String,
    val keyJoints: List<String>,
    val tags: List<String>,
    val phases: List<PortablePhase>,
    val poses: List<PortablePose>,
    val metricThresholds: Map<String, Float>,
    val extensions: Map<String, String> = emptyMap(),
)

data class DrillManifest(
    val packageId: String,
    val schemaVersion: SchemaVersion,
    val source: String,
    val exportedAtMs: Long,
    val assets: List<PortableAssetRef> = emptyList(),
)

data class DrillPackage(
    val manifest: DrillManifest,
    val drills: List<PortableDrill>,
)
