package com.inversioncoach.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsPolicyTest {

    @Test
    fun `normalized resolves legacy export quality and invalid numeric settings`() {
        val legacy = UserSettings(
            annotatedExportQuality = "UNKNOWN_PRESET",
            startupCountdownSeconds = -4,
            maxStorageMb = 0,
        )

        val normalized = legacy.normalized()

        assertEquals(AppSettingsPolicy.defaultExportQuality.name, normalized.annotatedExportQuality)
        assertEquals(AppSettingsPolicy.defaultCountdownSeconds, normalized.startupCountdownSeconds)
        assertEquals(AppSettingsPolicy.minStorageGb * 1024, normalized.maxStorageMb)
    }

    @Test
    fun `storage conversion keeps ui and persisted values aligned`() {
        val storageGb = 12
        val persisted = AppSettingsPolicy.storageGbToMb(storageGb)

        assertEquals(storageGb, AppSettingsPolicy.storageMbToGb(persisted))
        assertEquals(12 * 1024, persisted)
    }

    @Test
    fun `effective export quality falls back to stable`() {
        val settings = UserSettings(annotatedExportQuality = "does_not_exist")

        assertEquals(AnnotatedExportQuality.STABLE, settings.effectiveExportQuality())
    }
}
