package com.inversioncoach.app.ui.navigation

import android.os.Bundle
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.overlay.EffectiveView
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRouteCodecTest {

    @Test
    fun parse_canonicalizesDrillSessionView() {
        val args = Bundle().apply {
            putString("drill", DrillType.FREE_HANDSTAND.name)
            putBoolean("voice", true)
            putBoolean("record", true)
            putBoolean("skeleton", true)
            putBoolean("idealLine", true)
            putBoolean("showCenterOfGravity", true)
            putBoolean("zoomOutCamera", true)
            putString("drillCameraSide", "LEFT")
            putString("effectiveView", EffectiveView.FREESTYLE.name)
            putString("selectedDrillId", "drill-1")
        }

        val parsed = LiveRouteCodec.parse(args)

        assertEquals(DrillType.FREE_HANDSTAND, parsed.drillType)
        assertEquals(EffectiveView.SIDE, parsed.options.effectiveView)
        assertEquals("drill-1", parsed.options.selectedDrillId)
    }
}
