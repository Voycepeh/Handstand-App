package com.inversioncoach.app.recording

enum class ExportPreset(
    val targetHeight: Int,
    val outputFps: Int,
    val analysisFps: Int,
) {
    FAST(targetHeight = 720, outputFps = 24, analysisFps = 10),
    BALANCED(targetHeight = 720, outputFps = 30, analysisFps = 15),
    HIGH(targetHeight = 1080, outputFps = 30, analysisFps = 18),
}
