package com.inversioncoach.app.ui.navigation

import android.os.Bundle
import com.inversioncoach.app.ui.startdrill.StartDrillDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteArgumentsTest {
    @Test
    fun parseStartDestinationFallsBackToLive() {
        assertEquals(StartDrillDestination.LIVE, RouteArguments.parseStartDestination(null))
        assertEquals(StartDrillDestination.LIVE, RouteArguments.parseStartDestination(Bundle().apply { putString("destination", "invalid") }))
        assertEquals(StartDrillDestination.WORKSPACE, RouteArguments.parseStartDestination(Bundle().apply { putString("destination", "workspace") }))
    }

    @Test
    fun parseDrillStudioNormalizesBlankIds() {
        val args = RouteArguments.parseDrillStudio(
            Bundle().apply {
                putString("mode", "create")
                putString("drillId", "")
                putString("templateId", "template-a")
            },
        )

        assertEquals("create", args.mode)
        assertNull(args.drillId)
        assertEquals("template-a", args.templateId)
    }

    @Test
    fun parseUploadVideoNormalizesDefaults() {
        val defaults = RouteArguments.parseUploadVideo(Bundle())
        assertNull(defaults.drillId)
        assertNull(defaults.templateId)
        assertFalse(defaults.isReferenceUpload)
        assertFalse(defaults.createNewDrillFromReference)

        val args = RouteArguments.parseUploadVideo(
            Bundle().apply {
                putString("drillId", "drill-1")
                putString("referenceTemplateId", "template-1")
                putBoolean("isReference", true)
                putBoolean("createNewDrillFromReference", true)
            },
        )
        assertEquals("drill-1", args.drillId)
        assertEquals("template-1", args.templateId)
        assertTrue(args.isReferenceUpload)
        assertTrue(args.createNewDrillFromReference)
    }
}
