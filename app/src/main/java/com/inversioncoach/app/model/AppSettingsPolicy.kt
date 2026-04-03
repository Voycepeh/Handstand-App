package com.inversioncoach.app.model

private const val MB_PER_GB = 1024

object AppSettingsPolicy {
    val countdownOptionsSeconds: List<Int> = listOf(3, 5, 10)

    const val defaultCountdownSeconds: Int = 10
    const val minStorageGb: Int = 1
    const val maxStorageGb: Int = 100
    const val defaultStorageGb: Int = 5
    const val defaultStorageMb: Int = defaultStorageGb * MB_PER_GB
    val defaultExportQuality: AnnotatedExportQuality = AnnotatedExportQuality.STABLE

    fun resolveExportQuality(raw: String): AnnotatedExportQuality =
        AnnotatedExportQuality.entries.firstOrNull { it.name == raw } ?: defaultExportQuality

    fun resolveStartupCountdownSeconds(raw: Int): Int =
        if (raw <= 0) defaultCountdownSeconds else raw

    fun resolveStorageMb(raw: Int): Int =
        raw.coerceIn(minStorageGb * MB_PER_GB, maxStorageGb * MB_PER_GB)

    fun storageGbToMb(storageGb: Int): Int = storageGb.coerceIn(minStorageGb, maxStorageGb) * MB_PER_GB

    fun storageMbToGb(storageMb: Int): Int = (resolveStorageMb(storageMb).toFloat() / MB_PER_GB).toInt()
}

fun UserSettings.normalized(): UserSettings {
    val resolvedQuality = AppSettingsPolicy.resolveExportQuality(annotatedExportQuality)
    return copy(
        annotatedExportQuality = resolvedQuality.name,
        startupCountdownSeconds = AppSettingsPolicy.resolveStartupCountdownSeconds(startupCountdownSeconds),
        maxStorageMb = AppSettingsPolicy.resolveStorageMb(maxStorageMb),
    )
}

fun UserSettings.effectiveExportQuality(): AnnotatedExportQuality =
    AppSettingsPolicy.resolveExportQuality(annotatedExportQuality)
