package com.inversioncoach.app.ui.drillstudio

import com.inversioncoach.app.drills.catalog.CatalogNormalizationBasis
import com.inversioncoach.app.drills.catalog.CameraView
import com.inversioncoach.app.drills.catalog.ComparisonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class DrillStudioPayloadParsingTest {
    @Test
    fun decodeStudioPayload_readsStudioPayloadTokenFromCueConfigCodec() {
        val json = """
            {
              "cameraView":"LEFT_PROFILE",
              "supportedViews":["LEFT_PROFILE"],
              "comparisonMode":"POSE_TIMELINE",
              "keyJoints":["shoulder_left"],
              "normalizationBasis":"HIPS"
            }
        """.trimIndent()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

        val payload = decodeStudioPayload("legacyDrillType:FREE_HANDSTAND|studioPayload:$encoded")

        requireNotNull(payload)
        assertEquals(CameraView.LEFT_PROFILE, payload.cameraView)
        assertEquals(ComparisonMode.POSE_TIMELINE, payload.comparisonMode)
        assertEquals(CatalogNormalizationBasis.HIPS, payload.normalizationBasis)
    }

    @Test
    fun decodeStudioPayload_returnsNullWhenStudioPayloadMissingOrBad() {
        assertNull(decodeStudioPayload("legacyDrillType:FREE_HANDSTAND|comparisonMode:POSE_TIMELINE"))
        assertNull(decodeStudioPayload("studioPayload:not-base64"))
    }
}
