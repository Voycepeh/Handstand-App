package com.inversioncoach.app.movementprofile

import com.inversioncoach.app.model.DrillType

class ExistingDrillToProfileAdapter {
    fun fromDrill(drillType: DrillType): MovementProfile {
        val normalizedName = drillType.name.lowercase()
        return MovementProfile(
            id = "drill_${normalizedName}",
            displayName = drillType.name.replace('_', ' '),
            drillType = drillType,
            movementType = MovementType.REP,
            allowedViews = setOf(CameraViewConstraint.ANY),
            phaseDefinitions = listOf(
                PhaseDefinition(id = "setup", displayName = "Setup", sequenceIndex = 0),
                PhaseDefinition(id = "rep", displayName = "Rep", sequenceIndex = 1),
            ),
            alignmentRules = listOf(
                AlignmentRule(
                    id = "shoulder_hip_stack",
                    metricKey = "shoulder_hip_stack",
                    target = 0f,
                    tolerance = 0.2f,
                ),
            ),
            repRule = RepRule(
                id = "rep_rule_default",
                angleKey = "elbow_avg",
                bottomThresholdDeg = 95f,
                topThresholdDeg = 165f,
                minRepDurationMs = 400L,
            ),
            readinessRule = ReadinessRule(
                minConfidence = 0.45f,
                requiredLandmarks = setOf(
                    "left_shoulder",
                    "right_shoulder",
                    "left_hip",
                    "right_hip",
                ),
                minVisibleLandmarkCount = 3,
                sideViewPrimary = false,
            ),
            keyJoints = setOf(
                "left_shoulder",
                "right_shoulder",
                "left_elbow",
                "right_elbow",
                "left_wrist",
                "right_wrist",
                "left_hip",
                "right_hip",
            ),
            defaultThresholds = mapOf("alignment_score" to 0.65f),
        )
    }
}
