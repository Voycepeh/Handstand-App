package com.inversioncoach.app.recording

data class AnnotatedExportTelemetry(
    val exportStartedAtMs: Long,
    var decoderInitializedAtMs: Long? = null,
    var compositorInitializedAtMs: Long? = null,
    var firstFrameDecodedAtMs: Long? = null,
    var firstFrameRenderedAtMs: Long? = null,
    var firstFrameEncodedAtMs: Long? = null,
    var firstFrameSubmittedToEncoderAtMs: Long? = null,
    var decodedFrameCount: Int = 0,
    var renderedFrameCount: Int = 0,
    var encodedFrameCount: Int = 0,
    var muxedFrameCount: Int = 0,
    var droppedFrameCount: Int = 0,
    var overlayFramesAvailable: Int = 0,
    var overlayFramesConsumed: Int = 0,
    var usableOverlayFrameCount: Int = 0,
    var rawOverlayFrameCount: Int = 0,
    var outputBytesWritten: Long = 0L,
    var dynamicTimeoutMs: Long = 0L,
    var stallWindowMs: Long = 0L,
    var lastProgressAtMs: Long? = null,
    var timeSinceLastProgressMs: Long = 0L,
    var muxFinalizeCompleted: Boolean = false,
    var exportCompletedAtMs: Long? = null,
    var failureReason: String? = null,
    var decodeElapsedMs: Long = 0L,
    var overlayResolveElapsedMs: Long = 0L,
    var renderElapsedMs: Long = 0L,
    var verifyElapsedMs: Long = 0L,
    var muxElapsedMs: Long = 0L,
    var frameAvailableWaitMs: Long = 0L,
    var compositorRenderMs: Long = 0L,
) {
    val totalElapsedMs: Long
        get() = (exportCompletedAtMs ?: System.currentTimeMillis()) - exportStartedAtMs

    fun structuredLogLine(event: String): String =
        "event=$event exportStarted=$exportStartedAtMs decoderInitialized=$decoderInitializedAtMs compositorInitialized=$compositorInitializedAtMs firstFrameDecodedAt=$firstFrameDecodedAtMs " +
            "firstFrameRenderedAt=$firstFrameRenderedAtMs firstFrameEncodedAt=$firstFrameEncodedAtMs firstFrameSubmittedToEncoderAt=$firstFrameSubmittedToEncoderAtMs " +
            "decodedFrameCount=$decodedFrameCount renderedFrameCount=$renderedFrameCount encodedFrameCount=$encodedFrameCount muxedFrameCount=$muxedFrameCount droppedFrameCount=$droppedFrameCount " +
            "overlayFramesAvailable=$overlayFramesAvailable overlayFramesConsumed=$overlayFramesConsumed usableOverlayFrameCount=$usableOverlayFrameCount rawOverlayFrameCount=$rawOverlayFrameCount outputBytesWritten=$outputBytesWritten " +
            "dynamicTimeoutMs=$dynamicTimeoutMs stallWindowMs=$stallWindowMs lastProgressAtMs=${lastProgressAtMs ?: -1L} timeSinceLastProgressMs=$timeSinceLastProgressMs " +
            "muxFinalizeCompleted=$muxFinalizeCompleted exportCompleted=$exportCompletedAtMs failureReason=${failureReason.orEmpty()} " +
            "elapsedMs.decode=$decodeElapsedMs elapsedMs.overlayResolve=$overlayResolveElapsedMs elapsedMs.render=$renderElapsedMs elapsedMs.compositorWait=$frameAvailableWaitMs elapsedMs.compositorRender=$compositorRenderMs elapsedMs.mux=$muxElapsedMs elapsedMs.verify=$verifyElapsedMs elapsedMs.total=$totalElapsedMs"
}
