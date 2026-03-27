package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

object DefaultDrillMovementProfiles {
    fun forDrill(drillType: DrillType, nowMs: Long = System.currentTimeMillis()): DrillMovementProfile {
        return if (drillType in holdDrills) {
            DrillMovementProfile(
                drillType = drillType,
                profileVersion = 1,
                userBodyProfile = null,
                holdTemplate = defaultHoldTemplate(drillType),
                repTemplate = null,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            )
        } else {
            DrillMovementProfile(
                drillType = drillType,
                profileVersion = 1,
                userBodyProfile = null,
                holdTemplate = null,
                repTemplate = null,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            )
        }
    }

    private val holdDrills = setOf(
        DrillType.FREE_HANDSTAND,
        DrillType.WALL_HANDSTAND,
        DrillType.BACK_TO_WALL_HANDSTAND,
        DrillType.WALL_FACING_HANDSTAND_HOLD,
    )

    private fun defaultHoldTemplate(drillType: DrillType): HoldTemplate = HoldTemplate(
        drillType = drillType,
        profileVersion = 1,
        metrics = listOf(
            HoldMetricTemplate("wrist_shoulder_offset", 0.05f, 0.04f, 0.08f, 1.0f),
            HoldMetricTemplate("shoulder_hip_offset", 0.05f, 0.04f, 0.08f, 1.0f),
            HoldMetricTemplate("hip_ankle_offset", 0.06f, 0.05f, 0.10f, 0.9f),
            HoldMetricTemplate("torso_line_deviation", 0.05f, 0.05f, 0.10f, 0.8f),
            HoldMetricTemplate("left_right_symmetry", 0.95f, 0.08f, 0.15f, 0.6f),
            HoldMetricTemplate("stability_score", 0.90f, 0.10f, 0.20f, 0.5f),
        ),
        minStableDurationMs = 2000L,
        source = HoldTemplateSource.DEFAULT_BASELINE,
    )
}
