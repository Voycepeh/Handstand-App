package com.inversioncoach.app.drills

import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.model.DrillType

object DrillDefinitionResolver {
    fun resolveLegacyDrillType(drill: DrillDefinitionRecord?): DrillType {
        return DrillCueConfigCodec.parse(drill?.cueConfigJson).legacyDrillType
    }
}
