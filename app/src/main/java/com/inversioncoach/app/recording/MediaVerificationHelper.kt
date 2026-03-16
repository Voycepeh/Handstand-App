package com.inversioncoach.app.recording

import android.media.MediaMetadataRetriever
import android.net.Uri
import com.inversioncoach.app.model.AnnotatedExportFailureReason
import java.io.File

data class MediaVerificationResult(
    val isValid: Boolean,
    val failureReason: AnnotatedExportFailureReason? = null,
    val sizeBytes: Long = 0L,
    val durationMs: Long? = null,
)

object MediaVerificationHelper {
    fun verify(uri: String?, minDurationMs: Long = 1L): MediaVerificationResult {
        if (uri.isNullOrBlank()) {
            return MediaVerificationResult(false, AnnotatedExportFailureReason.OUTPUT_URI_NULL)
        }
        val file = runCatching { Uri.parse(uri).path }.getOrNull()?.let(::File)
            ?: return MediaVerificationResult(false, AnnotatedExportFailureReason.OUTPUT_FILE_MISSING)
        if (!file.exists()) return MediaVerificationResult(false, AnnotatedExportFailureReason.OUTPUT_FILE_MISSING)
        if (file.length() <= 0L) return MediaVerificationResult(false, AnnotatedExportFailureReason.OUTPUT_FILE_ZERO_BYTES)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return MediaVerificationResult(false, AnnotatedExportFailureReason.METADATA_UNREADABLE)
            val firstFrame = retriever.getFrameAtTime(250_000L, MediaMetadataRetriever.OPTION_CLOSEST)
            val playable = firstFrame != null
            firstFrame?.recycle()
            if (duration < minDurationMs || !playable) {
                MediaVerificationResult(false, AnnotatedExportFailureReason.METADATA_UNREADABLE, file.length(), duration)
            } else {
                MediaVerificationResult(true, null, file.length(), duration)
            }
        } catch (_: Throwable) {
            MediaVerificationResult(false, AnnotatedExportFailureReason.METADATA_UNREADABLE, file.length(), null)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
