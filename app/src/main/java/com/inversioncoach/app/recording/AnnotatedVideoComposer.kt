package com.inversioncoach.app.recording

import android.util.Log
import com.inversioncoach.app.model.AnnotatedExportFailureReason
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.overlay.DrillCameraSide

private const val TAG = "AnnotatedVideoComposer"

class AnnotatedVideoComposer(
    private val compositor: AnnotatedVideoCompositor,
) {
    suspend fun compose(
        rawVideoUri: String,
        timeline: OverlayTimeline,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
        preset: ExportPreset,
        onProgress: (Int, Int) -> Unit,
        onTelemetry: (AnnotatedExportTelemetry) -> Unit,
    ): ComposerResult {
        if (rawVideoUri.isBlank()) {
            return ComposerResult(null, AnnotatedExportFailureReason.RAW_VIDEO_MISSING.name)
        }
        if (timeline.frames.isEmpty()) {
            return ComposerResult(null, AnnotatedExportFailureReason.OVERLAY_TIMELINE_EMPTY.name)
        }
        Log.d(TAG, "composer_start raw=$rawVideoUri frames=${timeline.frames.size}")
        val rendered = compositor.export(
            rawVideoUri = rawVideoUri,
            drillType = drillType,
            drillCameraSide = drillCameraSide,
            overlayFrames = timeline.frames.map { it.toAnnotatedOverlayFrame() },
            preset = preset,
            onProgress = onProgress,
            onTelemetry = onTelemetry,
        )
        if (rendered.isNullOrBlank()) {
            return ComposerResult(null, AnnotatedExportFailureReason.ENCODE_FAILED.name)
        }
        Log.d(TAG, "composer_complete uri=$rendered")
        return ComposerResult(rendered, null)
    }
}

data class ComposerResult(
    val uri: String?,
    val failureReason: String?,
)
