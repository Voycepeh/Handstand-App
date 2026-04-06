package com.inversioncoach.app.ui.live

import androidx.camera.core.CameraSelector

enum class LiveCameraFacing(val encoded: String, val cameraSelectorLensFacing: Int) {
    BACK("BACK", CameraSelector.LENS_FACING_BACK),
    FRONT("FRONT", CameraSelector.LENS_FACING_FRONT),
}

enum class LiveViewPreset(val encoded: String) {
    FRONT("FRONT"),
    SIDE("SIDE"),
    FREESTYLE("FREESTYLE"),
}

data class LiveCoachingControlState(
    val selectedCameraFacing: LiveCameraFacing = LiveCameraFacing.BACK,
    val selectedZoomRatio: Float = 1f,
    val selectedViewPreset: LiveViewPreset = LiveViewPreset.FREESTYLE,
    val mirrorPreview: Boolean = false,
)

data class LiveCameraCapabilities(
    val availableFacings: Set<LiveCameraFacing> = setOf(LiveCameraFacing.BACK),
    val supportedZoomRatios: List<Float> = listOf(1f),
)

object LiveCoachingPreferenceCodec {
    private const val CAMERA_SEPARATOR = ';'
    private const val VALUE_SEPARATOR = ':'

    fun cameraFacingFrom(raw: String?): LiveCameraFacing =
        LiveCameraFacing.entries.firstOrNull { it.encoded == raw } ?: LiveCameraFacing.BACK

    fun viewPresetFrom(raw: String?): LiveViewPreset =
        LiveViewPreset.entries.firstOrNull { it.encoded == raw } ?: LiveViewPreset.FREESTYLE

    fun zoomByCameraFrom(raw: String): Map<LiveCameraFacing, Float> =
        raw.split(CAMERA_SEPARATOR)
            .mapNotNull { token ->
                val parts = token.split(VALUE_SEPARATOR)
                if (parts.size != 2) return@mapNotNull null
                val camera = LiveCameraFacing.entries.firstOrNull { it.encoded == parts[0] } ?: return@mapNotNull null
                val zoom = parts[1].toFloatOrNull() ?: parts[1].replace(',', '.').toFloatOrNull() ?: return@mapNotNull null
                camera to zoom
            }.toMap()

    fun encodeZoomByCamera(zoomByCamera: Map<LiveCameraFacing, Float>): String =
        zoomByCamera.entries.joinToString(separator = CAMERA_SEPARATOR.toString()) { (camera, ratio) ->
            "${camera.encoded}$VALUE_SEPARATOR${ratio.toString()}"
        }
}

fun supportedZoomPresets(minZoomRatio: Float, maxZoomRatio: Float): List<Float> {
    val normalizedMin = minZoomRatio.coerceAtLeast(0.1f)
    val normalizedMax = maxZoomRatio.coerceAtLeast(normalizedMin)
    val presets = buildList {
        if (normalizedMin <= 0.5f && normalizedMax >= 0.5f) add(0.5f)
        if (normalizedMin <= 1f && normalizedMax >= 1f) add(1f)
        if (normalizedMin <= 2f && normalizedMax >= 2f) add(2f)
    }
    return if (presets.isEmpty()) listOf(1f.coerceIn(normalizedMin, normalizedMax)) else presets
}

fun resolvedZoomSelection(preferred: Float, supported: List<Float>): Float {
    val normalized = if (supported.isEmpty()) 1f else supported.minBy { kotlin.math.abs(it - preferred) }
    return normalized
}
