package com.inversioncoach.app.ui.calibration

import com.inversioncoach.app.calibration.CalibrationStep

data class CalibrationStepCopy(
    val title: String,
    val instruction: String,
    val cameraPlacement: String,
)

fun copyFor(step: CalibrationStep): CalibrationStepCopy = when (step) {
    CalibrationStep.FRONT_NEUTRAL -> CalibrationStepCopy(
        title = "Front neutral",
        instruction = "Stand facing the camera with arms relaxed by your sides.",
        cameraPlacement = "Place the back camera 2.5–4 m away and keep your full body in frame.",
    )

    CalibrationStep.SIDE_NEUTRAL -> CalibrationStepCopy(
        title = "Side neutral",
        instruction = "Turn sideways and stand tall with your whole body visible.",
        cameraPlacement = "Keep the camera side-on to your body and make sure head to feet are visible.",
    )

    CalibrationStep.ARMS_OVERHEAD -> CalibrationStepCopy(
        title = "Arms overhead",
        instruction = "Raise both arms straight overhead and hold still.",
        cameraPlacement = "Keep both hands visible and stay centered in frame.",
    )

    CalibrationStep.CONTROLLED_HOLD -> CalibrationStepCopy(
        title = "Wall handstand hold",
        instruction = "Hold a wall-supported inversion for a few seconds.",
        cameraPlacement = "Use the back camera side-on and keep hands, shoulders, hips, and feet visible.",
    )
}
