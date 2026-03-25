package com.inversioncoach.app.pose.model

data class JointLandmark(
    val jointType: JointType,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
)
