package com.inversioncoach.app.recording

data class AnnotatedExportTelemetry(
    val exportStartedAtMs: Long,
    var decoderInitializedAtMs: Long? = null,
    var firstFrameDecodedAtMs: Long? = null,
    var decodedFrameCount: Int = 0,
    var renderedFrameCount: Int = 0,
    var encodedFrameCount: Int = 0,
    var overlayFramesAvailable: Int = 0,
    var overlayFramesConsumed: Int = 0,
    var outputBytesWritten: Long = 0L,
    var exportCompletedAtMs: Long? = null,
    var failureReason: String? = null,
    var decodeElapsedMs: Long = 0L,
    var overlayResolveElapsedMs: Long = 0L,
    var renderElapsedMs: Long = 0L,
    var verifyElapsedMs: Long = 0L,
) {
    val totalElapsedMs: Long
        get() = (exportCompletedAtMs ?: System.currentTimeMillis()) - exportStartedAtMs

    fun structuredLogLine(event: String): String =
        "event=$event exportStarted=$exportStartedAtMs decoderInitialized=$decoderInitializedAtMs firstFrameDecodedAt=$firstFrameDecodedAtMs " +
            "decodedFrameCount=$decodedFrameCount renderedFrameCount=$renderedFrameCount encodedFrameCount=$encodedFrameCount " +
            "overlayFramesAvailable=$overlayFramesAvailable overlayFramesConsumed=$overlayFramesConsumed outputBytesWritten=$outputBytesWritten " +
            "exportCompleted=$exportCompletedAtMs failureReason=${failureReason.orEmpty()} " +
            "elapsedMs.decode=$decodeElapsedMs elapsedMs.overlayResolve=$overlayResolveElapsedMs elapsedMs.render=$renderElapsedMs elapsedMs.verify=$verifyElapsedMs elapsedMs.total=$totalElapsedMs"
}

