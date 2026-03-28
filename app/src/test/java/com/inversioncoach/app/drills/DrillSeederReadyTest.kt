package com.inversioncoach.app.drills

import org.junit.Assert.assertTrue
import org.junit.Test

class DrillSeederReadyTest {
    @Test
    fun seededDrillsAreReady() {
        val seeded = DrillSeeder.seedDrills(0L)
        assertTrue(seeded.isNotEmpty())
        assertTrue(seeded.all { it.status == DrillStatus.READY })
    }
}
