package com.inversioncoach.app.storage

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceLocatorContractTest {
    @Test
    fun serviceLocatorCachesSingletonProvidersAndRepositories() {
        val fields = ServiceLocator::class.java.declaredFields.associateBy { it.name }

        assertNotNull("Session repository singleton backing field is required", fields["sessionRepository"])
        assertNotNull("Calibration provider singleton backing field is required", fields["calibrationProvider"])
        assertNotNull("Drill movement profile repository singleton backing field is required", fields["drillMovementProfileRepository"])

        assertTrue("sessionRepository should be volatile", java.lang.reflect.Modifier.isVolatile(fields.getValue("sessionRepository").modifiers))
        assertTrue("calibrationProvider should be volatile", java.lang.reflect.Modifier.isVolatile(fields.getValue("calibrationProvider").modifiers))
        assertTrue(
            "drillMovementProfileRepository should be volatile",
            java.lang.reflect.Modifier.isVolatile(fields.getValue("drillMovementProfileRepository").modifiers),
        )
    }
}
