package com.inversioncoach.app.drills.catalog

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillCatalogJsonTest {
    @Test
    fun decode_phaseProgressWindow() {
        val raw = """
            {"drills":[{"id":"d","title":"D","movementType":"HOLD","cameraView":"SIDE","supportedViews":["SIDE"],"analysisPlane":"SAGITTAL","comparisonMode":"OVERLAY","phases":[{"id":"p1","label":"P1","order":0,"progressWindow":{"start":0.2,"end":0.7}}],"animationSpec":{"id":"a","fpsHint":15,"keyframes":[{"name":"k","progress":0.0,"joints":{}},{"name":"k2","progress":1.0,"joints":{}}]}}]}
        """.trimIndent()

        val parsed = DrillCatalogJson.decodeCatalog(raw).first()

        assertEquals(0.2f, parsed.phases.first().progressWindow.start)
        assertEquals(0.7f, parsed.phases.first().progressWindow.end)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_invalidProgressWindowFails() {
        DrillCatalogJson.decodeDrill(JSONObject("""
            {"id":"d","title":"D","movementType":"HOLD","cameraView":"SIDE","supportedViews":["SIDE"],"phases":[{"id":"p1","label":"P1","order":0,"progressWindow":{"start":0.8,"end":0.2}}],"animationSpec":{"id":"a","fpsHint":15,"keyframes":[{"name":"k","progress":0.0,"joints":{}},{"name":"k2","progress":1.0,"joints":{}}]}}
        """.trimIndent()))
    }

    @Test
    fun decode_invalidDefaultViewFails() {
        val ex = runCatching {
            DrillCatalogJson.decodeDrill(JSONObject("""
                {"id":"d","title":"D","movementType":"HOLD","cameraView":"FRONT","supportedViews":["SIDE"],"phases":[{"id":"p1","label":"P1","order":0,"progressWindow":{"start":0.0,"end":0.2}}],"animationSpec":{"id":"a","fpsHint":15,"keyframes":[{"name":"k","progress":0.0,"joints":{}},{"name":"k2","progress":1.0,"joints":{}}]}}
            """.trimIndent()))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_duplicateOrUnsortedKeyframeProgressFails() {
        DrillCatalogJson.decodeDrill(JSONObject("""
            {"id":"d","title":"D","movementType":"HOLD","cameraView":"SIDE","supportedViews":["SIDE"],"phases":[{"id":"p1","label":"P1","order":0,"progressWindow":{"start":0.0,"end":0.2}}],"animationSpec":{"id":"a","fpsHint":15,"keyframes":[{"name":"k1","progress":0.6,"joints":{}},{"name":"k2","progress":0.6,"joints":{}}]}}
        """.trimIndent()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_invalidFpsFails() {
        DrillCatalogJson.decodeDrill(JSONObject("""
            {"id":"d","title":"D","movementType":"HOLD","cameraView":"SIDE","supportedViews":["SIDE"],"phases":[{"id":"p1","label":"P1","order":0,"progressWindow":{"start":0.0,"end":0.2}}],"animationSpec":{"id":"a","fpsHint":0,"keyframes":[{"name":"k1","progress":0.0,"joints":{}},{"name":"k2","progress":1.0,"joints":{}}]}}
        """.trimIndent()))
    }
}
