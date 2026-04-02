package com.inversioncoach.app.recording

enum class ExportPreset(
    val targetHeight: Int,
    val outputFps: Int,
) {
    FAST(targetHeight = 720, outputFps = 24),
    BALANCED(targetHeight = 720, outputFps = 30),
    HIGH_QUALITY(targetHeight = 1080, outputFps = 30),
}
