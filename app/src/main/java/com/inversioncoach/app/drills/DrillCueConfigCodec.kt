package com.inversioncoach.app.drills

import com.inversioncoach.app.drills.catalog.ComparisonMode
import com.inversioncoach.app.model.DrillType

internal data class DrillCueConfigTokens(
    val legacyDrillType: DrillType,
    val comparisonMode: String,
    val studioPayload: String?,
)

internal object DrillCueConfigCodec {
    private const val LEGACY_KEY = "legacyDrillType"
    private const val COMPARISON_KEY = "comparisonMode"
    private const val STUDIO_PAYLOAD_KEY = "studioPayload"

    fun parse(cueConfigJson: String?): DrillCueConfigTokens {
        val tokens = cueConfigJson
            .orEmpty()
            .split('|')
            .asSequence()
            .mapNotNull(::parseToken)
            .toMap()

        val legacy = tokens[LEGACY_KEY]
            ?.let { DrillType.fromStoredName(it) }
            ?: DrillType.FREE_HANDSTAND

        val comparison = tokens[COMPARISON_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: ComparisonMode.POSE_TIMELINE.name

        return DrillCueConfigTokens(
            legacyDrillType = legacy,
            comparisonMode = comparison,
            studioPayload = tokens[STUDIO_PAYLOAD_KEY]?.takeIf { it.isNotBlank() },
        )
    }

    fun merge(
        existingCueConfig: String?,
        comparisonMode: String,
        studioPayload: String,
        legacyDrillType: DrillType = DrillType.FREE_HANDSTAND,
    ): String {
        val existingEntries = existingCueConfig
            .orEmpty()
            .split('|')
            .mapNotNull(::parseToken)
            .filterNot { (key, _) ->
                key == COMPARISON_KEY || key == STUDIO_PAYLOAD_KEY || key == LEGACY_KEY
            }
            .toMutableList()

        existingEntries += LEGACY_KEY to legacyDrillType.name
        existingEntries += COMPARISON_KEY to comparisonMode
        existingEntries += STUDIO_PAYLOAD_KEY to studioPayload

        return existingEntries.joinToString("|") { (key, value) -> "$key:$value" }
    }

    private fun parseToken(token: String): Pair<String, String>? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null
        val separator = trimmed.indexOf(':')
        if (separator <= 0 || separator == trimmed.lastIndex) return null
        val key = trimmed.substring(0, separator)
        val value = trimmed.substring(separator + 1)
        return key to value
    }
}
