package com.inversioncoach.app.overlay

import com.inversioncoach.app.drills.DrillCameraView

enum class EffectiveView {
    FRONT,
    SIDE,
    FREESTYLE,
}

object EffectiveViewResolver {
    fun resolve(
        explicit: EffectiveView?,
        drillDefaultCameraView: String?,
        freestyleFallback: EffectiveView = EffectiveView.FREESTYLE,
    ): EffectiveView {
        explicit?.let { return it }
        val mappedDefault = drillDefaultCameraView?.toEffectiveViewOrNull()
        return mappedDefault ?: freestyleFallback
    }
}

fun String.toEffectiveViewOrNull(): EffectiveView? = when (this) {
    DrillCameraView.FRONT,
    DrillCameraView.BACK,
    -> EffectiveView.FRONT

    DrillCameraView.LEFT,
    DrillCameraView.RIGHT,
    "SIDE",
    -> EffectiveView.SIDE

    DrillCameraView.FREESTYLE -> EffectiveView.FREESTYLE
    else -> null
}
