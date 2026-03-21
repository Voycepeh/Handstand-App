package com.inversioncoach.app.recording

import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.inversioncoach.app.model.AnnotatedExportFailureReason
import java.io.File

data class MediaVerificationResult(
    val isValid: Boolean,
    val failureReason: AnnotatedExportFailureReason? = null,
    val sizeBytes: Long = 0L,
    val durationMs: Long? = null,
)

data class ReplayInspectionResult(
    val uri: String?,
    val fileExists: Boolean,
    val fileSizeBytes: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val hasVideoTrack: Boolean,
    val firstFrameDecoded: Boolean,
    val errorDetail: String? = null,
) {
    val isDecodable: Boolean =
        fileExists &&
            fileSizeBytes > 0L &&
            (durationMs ?: 0L) > 0L &&
            hasVideoTrack &&
            firstFrameDecoded
}

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

    fun inspectReplay(uri: String?): ReplayInspectionResult {
        if (uri.isNullOrBlank()) {
            return ReplayInspectionResult(
                uri = uri,
                fileExists = false,
                fileSizeBytes = 0L,
                durationMs = null,
                width = null,
                height = null,
                hasVideoTrack = false,
                firstFrameDecoded = false,
                errorDetail = "URI_EMPTY",
            )
        }
        val file = runCatching { Uri.parse(uri).path }.getOrNull()?.let(::File)
        if (file == null || !file.exists()) {
            return ReplayInspectionResult(
                uri = uri,
                fileExists = false,
                fileSizeBytes = 0L,
                durationMs = null,
                width = null,
                height = null,
                hasVideoTrack = false,
                firstFrameDecoded = false,
                errorDetail = "FILE_MISSING",
            )
        }

        val fileSize = file.length().coerceAtLeast(0L)
        var durationMs: Long? = null
        var width: Int? = null
        var height: Int? = null
        var firstFrameDecoded = false
        var metadataError: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val firstFrame = retriever.getFrameAtTime(250_000L, MediaMetadataRetriever.OPTION_CLOSEST)
            firstFrameDecoded = firstFrame != null
            firstFrame?.recycle()
        } catch (error: Throwable) {
            metadataError = "${error::class.java.simpleName}:${error.message.orEmpty()}"
        } finally {
            runCatching { retriever.release() }
        }

        var hasVideoTrack = false
        var trackError: String? = null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    hasVideoTrack = true
                    break
                }
            }
        } catch (error: Throwable) {
            trackError = "${error::class.java.simpleName}:${error.message.orEmpty()}"
        } finally {
            runCatching { extractor.release() }
        }

        return ReplayInspectionResult(
            uri = uri,
            fileExists = true,
            fileSizeBytes = fileSize,
            durationMs = durationMs,
            width = width,
            height = height,
            hasVideoTrack = hasVideoTrack,
            firstFrameDecoded = firstFrameDecoded,
            errorDetail = listOfNotNull(metadataError, trackError).joinToString(";").ifBlank { null },
        )
    }
}
