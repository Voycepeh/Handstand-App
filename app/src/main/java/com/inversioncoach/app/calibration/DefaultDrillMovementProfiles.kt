package com.inversioncoach.app.calibration

import com.inversioncoach.app.model.DrillType

object DefaultDrillMovementProfiles {
    fun forDrill(drillType: DrillType, nowMs: Long = System.currentTimeMillis()): DrillMovementProfile {
        return when (drillType) {
            DrillType.FREE_HANDSTAND -> DrillMovementProfile(
                drillType = drillType,
                profileVersion = 1,
                userBodyProfile = null,
                holdTemplate = HoldTemplate(
                    drillType = drillType,
                    profileVersion = 1,
                    targetDurationMs = 3000L,
                    alignmentTarget = 85,
                    stabilityTarget = 80,
                ),
                repTemplate = null,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            )
            else -> DrillMovementProfile(
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
}
