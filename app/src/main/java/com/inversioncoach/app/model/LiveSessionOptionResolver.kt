package com.inversioncoach.app.model

import com.inversioncoach.app.overlay.EffectiveView

fun LiveSessionOptions.canonicalizeFor(drillType: DrillType): LiveSessionOptions {
    val mode = drillType.sessionMode()
    val resolvedView = when {
        mode == SessionMode.FREESTYLE -> EffectiveView.FREESTYLE
        effectiveView == EffectiveView.FREESTYLE -> EffectiveView.SIDE
        else -> effectiveView
    }
    return copy(effectiveView = resolvedView)
}
