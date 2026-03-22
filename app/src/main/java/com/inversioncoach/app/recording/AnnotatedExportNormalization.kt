package com.inversioncoach.app.recording

import com.inversioncoach.app.model.JointPoint
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class SourceVideoMetadata(
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)

internal data class ExportTransform(
    val rotationDegrees: Int,
    val outputWidth: Int,
    val outputHeight: Int,
) {
    val requiresAxisSwap: Boolean = rotationDegrees == 90 || rotationDegrees == 270
}

internal data class OutputVideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)

internal data class OutputVerificationResult(
    val passed: Boolean,
    val failureDetail: String? = null,
)

internal fun buildExportTransform(
    source: SourceVideoMetadata,
    preset: ExportPreset,
): ExportTransform {
    val normalizedRotation = ((source.rotationDegrees % 360) + 360) % 360
    val orientedWidth = if (normalizedRotation == 90 || normalizedRotation == 270) source.height else source.width
    val orientedHeight = if (normalizedRotation == 90 || normalizedRotation == 270) source.width else source.height
    val scale = (preset.targetHeight.toFloat() / orientedHeight.toFloat()).coerceAtMost(1f)
    val outputWidth = ((orientedWidth * scale) / 2f).roundToInt().coerceAtLeast(2) * 2
    val outputHeight = ((orientedHeight * scale) / 2f).roundToInt().coerceAtLeast(2) * 2
    return ExportTransform(
        rotationDegrees = normalizedRotation,
        outputWidth = outputWidth,
        outputHeight = outputHeight,
    )
}

internal fun mapOverlayPointToExportSpace(point: JointPoint, transform: ExportTransform): JointPoint {
    val (mappedX, mappedY) = when (transform.rotationDegrees) {
        90 -> 1f - point.y to point.x
        180 -> 1f - point.x to 1f - point.y
        270 -> point.y to 1f - point.x
        else -> point.x to point.y
    }
    return point.copy(
        x = mappedX.coerceIn(0f, 1f),
        y = mappedY.coerceIn(0f, 1f),
    )
}

internal fun verifyExportedVideo(
    sourceDurationMs: Long,
    output: OutputVideoMetadata?,
    toleranceMs: Long = 500L,
): OutputVerificationResult {
    if (output == null) return OutputVerificationResult(false, "output_metadata_unreadable")
    if (output.durationMs <= 0L) return OutputVerificationResult(false, "output_duration_non_positive")
    if (output.width <= 0 || output.height <= 0) return OutputVerificationResult(false, "output_dimensions_invalid")
    if (sourceDurationMs > 0L) {
        val deltaMs = abs(sourceDurationMs - output.durationMs)
        if (output.durationMs + toleranceMs < sourceDurationMs) {
            return OutputVerificationResult(
                passed = false,
                failureDetail = "output_duration_too_short sourceMs=$sourceDurationMs outputMs=${output.durationMs} deltaMs=$deltaMs toleranceMs=$toleranceMs",
            )
        }
    }
    return OutputVerificationResult(passed = true)
}
