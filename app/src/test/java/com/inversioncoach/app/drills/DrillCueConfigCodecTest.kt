package com.inversioncoach.app.drills

import com.inversioncoach.app.model.DrillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrillCueConfigCodecTest {
    @Test
    fun parse_extractsKnownTokensWithFallbacks() {
        val parsed = DrillCueConfigCodec.parse(
            "seedSource:legacy|legacyDrillType:WALL_HANDSTAND|comparisonMode:POSE_TIMELINE|studioPayload:abc123",
        )

        assertEquals(DrillType.WALL_HANDSTAND, parsed.legacyDrillType)
        assertEquals("POSE_TIMELINE", parsed.comparisonMode)
        assertEquals("abc123", parsed.studioPayload)
    }

    @Test
    fun parse_defaultsWhenTokensMissingOrMalformed() {
        val parsed = DrillCueConfigCodec.parse("comparisonMode:|broken_token|legacyDrillType:")

        assertEquals(DrillType.FREE_HANDSTAND, parsed.legacyDrillType)
        assertEquals("POSE_TIMELINE", parsed.comparisonMode)
        assertNull(parsed.studioPayload)
    }

    @Test
    fun merge_replacesManagedTokensAndPreservesUnknownValues() {
        val merged = DrillCueConfigCodec.merge(
            existingCueConfig = "seedSource:legacy|comparisonMode:OVERLAY|legacyDrillType:WALL_HANDSTAND|custom:keep",
            comparisonMode = "POSE_TIMELINE",
            studioPayload = "payloadX",
            legacyDrillType = DrillType.WALL_HANDSTAND,
        )

        assertEquals(
            "seedSource:legacy|custom:keep|legacyDrillType:WALL_HANDSTAND|comparisonMode:POSE_TIMELINE|studioPayload:payloadX",
            merged,
        )
    }
}
