package com.inversioncoach.app.model

import com.inversioncoach.app.overlay.EffectiveView
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSessionOptionResolverTest {

    @Test
    fun canonicalizeFor_mapsDrillFreestyleViewToSide() {
        val canonical = LiveSessionOptions(effectiveView = EffectiveView.FREESTYLE)
            .canonicalizeFor(DrillType.FREE_HANDSTAND)

        assertEquals(EffectiveView.SIDE, canonical.effectiveView)
    }

    @Test
    fun canonicalizeFor_forcesFreestyleDrillToFreestyleView() {
        val canonical = LiveSessionOptions(effectiveView = EffectiveView.FRONT)
            .canonicalizeFor(DrillType.FREESTYLE)

        assertEquals(EffectiveView.FREESTYLE, canonical.effectiveView)
    }
}
