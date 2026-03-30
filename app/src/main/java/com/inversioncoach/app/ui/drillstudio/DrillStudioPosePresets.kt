package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.drills.catalog.JointPoint

data class DrillStudioPosePreset(
    val id: String,
    val label: String,
    val joints: Map<String, JointPoint>,
)

object DrillStudioPosePresets {
    val neutralUpright = DrillStudioPosePreset(
        id = "neutral_upright",
        label = "Neutral upright",
        joints = mapOf(
            "nose" to JointPoint(0.5f, 0.16f),
            "left_shoulder" to JointPoint(0.44f, 0.3f),
            "right_shoulder" to JointPoint(0.56f, 0.3f),
            "left_elbow" to JointPoint(0.41f, 0.42f),
            "right_elbow" to JointPoint(0.59f, 0.42f),
            "left_wrist" to JointPoint(0.39f, 0.52f),
            "right_wrist" to JointPoint(0.61f, 0.52f),
            "left_hip" to JointPoint(0.46f, 0.54f),
            "right_hip" to JointPoint(0.54f, 0.54f),
            "left_knee" to JointPoint(0.46f, 0.7f),
            "right_knee" to JointPoint(0.54f, 0.7f),
            "left_ankle" to JointPoint(0.46f, 0.88f),
            "right_ankle" to JointPoint(0.54f, 0.88f),
        ),
    )

    val handstandFront = DrillStudioPosePreset(
        id = "handstand_front",
        label = "Handstand front view",
        joints = mapOf(
            "nose" to JointPoint(0.5f, 0.86f),
            "left_shoulder" to JointPoint(0.45f, 0.72f),
            "right_shoulder" to JointPoint(0.55f, 0.72f),
            "left_elbow" to JointPoint(0.43f, 0.58f),
            "right_elbow" to JointPoint(0.57f, 0.58f),
            "left_wrist" to JointPoint(0.42f, 0.44f),
            "right_wrist" to JointPoint(0.58f, 0.44f),
            "left_hip" to JointPoint(0.47f, 0.54f),
            "right_hip" to JointPoint(0.53f, 0.54f),
            "left_knee" to JointPoint(0.47f, 0.34f),
            "right_knee" to JointPoint(0.53f, 0.34f),
            "left_ankle" to JointPoint(0.47f, 0.14f),
            "right_ankle" to JointPoint(0.53f, 0.14f),
        ),
    )

    val handstandSide = DrillStudioPosePreset(
        id = "handstand_side",
        label = "Handstand side view",
        joints = mapOf(
            "nose" to JointPoint(0.5f, 0.88f),
            "left_shoulder" to JointPoint(0.5f, 0.72f),
            "left_elbow" to JointPoint(0.5f, 0.58f),
            "left_wrist" to JointPoint(0.5f, 0.42f),
            "left_hip" to JointPoint(0.5f, 0.54f),
            "left_knee" to JointPoint(0.5f, 0.34f),
            "left_ankle" to JointPoint(0.5f, 0.14f),
        ),
    )

    val pikePushUpSide = DrillStudioPosePreset(
        id = "pike_push_up_side",
        label = "Pike push-up side view",
        joints = mapOf(
            "nose" to JointPoint(0.56f, 0.56f),
            "left_shoulder" to JointPoint(0.48f, 0.52f),
            "left_elbow" to JointPoint(0.42f, 0.54f),
            "left_wrist" to JointPoint(0.34f, 0.56f),
            "left_hip" to JointPoint(0.56f, 0.42f),
            "left_knee" to JointPoint(0.64f, 0.56f),
            "left_ankle" to JointPoint(0.74f, 0.72f),
        ),
    )

    val all = listOf(neutralUpright, handstandFront, handstandSide, pikePushUpSide)
}
