package com.inversioncoach.app.drillpackage.mapping

import com.inversioncoach.app.drillpackage.model.DrillPackageContract

/**
 * Shared pose/joint semantics for the portable package boundary.
 */
object PortablePoseSemantics {
    fun canonicalJointName(rawName: String): String = PortableJointNames.canonicalize(rawName)

    fun <T> canonicalizeJointMap(joints: Map<String, T>): Map<String, T> = joints
        .mapKeys { (joint, _) -> canonicalJointName(joint) }
        .toSortedMap()

    fun isNormalizedCoordinate(value: Float): Boolean =
        value in DrillPackageContract.COORDINATE_MIN..DrillPackageContract.COORDINATE_MAX
}
