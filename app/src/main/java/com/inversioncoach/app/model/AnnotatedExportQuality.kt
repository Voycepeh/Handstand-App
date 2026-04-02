package com.inversioncoach.app.model

import com.inversioncoach.app.recording.ExportPreset

enum class AnnotatedExportQuality {
    STABLE,
    HIGH_QUALITY,
}

fun AnnotatedExportQuality.toExportPreset(): ExportPreset = when (this) {
    AnnotatedExportQuality.STABLE -> ExportPreset.BALANCED
    AnnotatedExportQuality.HIGH_QUALITY -> ExportPreset.HIGH_QUALITY
}
