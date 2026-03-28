package com.inversioncoach.app.drills

import com.inversioncoach.app.model.DrillDefinitionRecord
import com.inversioncoach.app.model.DrillType

object DrillDefinitionResolver {
    fun resolveLegacyDrillType(drill: DrillDefinitionRecord?): DrillType {
        val cue = drill?.cueConfigJson.orEmpty()
        val token = cue.split('|').firstOrNull { it.startsWith("legacyDrillType:") }
            ?.substringAfter(':')
            ?.trim()
        return token?.let { DrillType.fromStoredName(it) } ?: DrillType.FREE_HANDSTAND
    }
}
