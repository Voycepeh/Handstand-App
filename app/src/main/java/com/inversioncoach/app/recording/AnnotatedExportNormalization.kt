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
    val sourceMetadataRotationDegrees: Int,
    val renderRotationDegrees: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val finalRotationMetadataDegrees: Int = 0,
) {
    val requiresAxisSwap: Boolean = sourceMetadataRotationDegrees == 90 || sourceMetadataRotationDegrees == 270
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

internal fun normalizedRotationDegrees(rawRotationDegrees: Int): Int = ((rawRotationDegrees % 360) + 360) % 360

internal fun sourceToUprightRotationDegrees(rawRotationDegrees: Int): Int = normalizedRotationDegrees(rawRotationDegrees)

internal fun finalOutputRotationMetadataDegrees(): Int = 0

internal fun buildExportTransform(
    source: SourceVideoMetadata,
    preset: ExportPreset,
): ExportTransform {
    val sourceRotation = normalizedRotationDegrees(source.rotationDegrees)
    val orientedWidth = if (sourceRotation == 90 || sourceRotation == 270) source.height else source.width
    val orientedHeight = if (sourceRotation == 90 || sourceRotation == 270) source.width else source.height
    val scale = (preset.targetHeight.toFloat() / orientedHeight.toFloat()).coerceAtMost(1f)
    val outputWidth = ((orientedWidth * scale) / 2f).roundToInt().coerceAtLeast(2) * 2
    val outputHeight = ((orientedHeight * scale) / 2f).roundToInt().coerceAtLeast(2) * 2
    return ExportTransform(
        sourceMetadataRotationDegrees = sourceRotation,
        renderRotationDegrees = sourceToUprightRotationDegrees(source.rotationDegrees),
        outputWidth = outputWidth,
        outputHeight = outputHeight,
        finalRotationMetadataDegrees = finalOutputRotationMetadataDegrees(),
    )
}

internal fun mapOverlayPointToExportSpace(point: JointPoint, transform: ExportTransform): JointPoint {
    // Overlay timeline points are captured from the same sampled texture space as decoded video frames.
    // Route through texture-space mapping so overlays and video share one rotation/flip convention.
    val (mappedX, mappedY) = mapTextureCoordinateToExportSpace(point.x, point.y, transform.renderRotationDegrees)
    return point.copy(
        x = mappedX.coerceIn(0f, 1f),
        y = mappedY.coerceIn(0f, 1f),
    )
}

internal fun mapNormalizedPointToExportSpace(
    x: Float,
    y: Float,
    rotationDegrees: Int,
): Pair<Float, Float> = when (normalizedRotationDegrees(rotationDegrees)) {
    90 -> 1f - y to x
    180 -> 1f - x to 1f - y
    270 -> y to 1f - x
    else -> x to y
}

/**
 * Maps GL texture coordinates for decoder OES sampling into export orientation space.
 *
 * OES texture space uses a bottom-left origin while export/overlay normalized space is top-left.
 * With identity SurfaceTexture matrix, we rotate in top-left space, then convert back.
 */
internal fun mapTextureCoordinateToExportSpace(
    x: Float,
    y: Float,
    rotationDegrees: Int,
): Pair<Float, Float> {
    val topLeftY = 1f - y
    val (rotatedX, rotatedTopLeftY) = mapNormalizedPointToExportSpace(
        x = x,
        y = topLeftY,
        rotationDegrees = rotationDegrees,
    )
    return rotatedX to (1f - rotatedTopLeftY)
}

internal fun verifyExportedVideo(
    sourceDurationMs: Long,
    output: OutputVideoMetadata?,
    toleranceMs: Long = 500L,
    expectedWidth: Int? = null,
    expectedHeight: Int? = null,
    expectedRotationDegrees: Int = 0,
): OutputVerificationResult {
    if (output == null) return OutputVerificationResult(false, "output_metadata_unreadable")
    if (output.durationMs <= 0L) return OutputVerificationResult(false, "output_duration_non_positive")
    if (output.width <= 0 || output.height <= 0) return OutputVerificationResult(false, "output_dimensions_invalid")
    if (expectedWidth != null && output.width != expectedWidth) {
        return OutputVerificationResult(false, "output_width_mismatch expected=$expectedWidth actual=${output.width}")
    }
    if (expectedHeight != null && output.height != expectedHeight) {
        return OutputVerificationResult(false, "output_height_mismatch expected=$expectedHeight actual=${output.height}")
    }
    if (output.rotationDegrees != expectedRotationDegrees) {
        return OutputVerificationResult(false, "output_rotation_mismatch expected=$expectedRotationDegrees actual=${output.rotationDegrees}")
    }
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
