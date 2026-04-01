package com.inversioncoach.app.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SessionVideoSaver(
    private val context: Context,
    private val sourceOpener: SessionMediaSourceOpener,
) {
    fun saveRawVideo(sourceUri: String, sessionName: String, sessionTimestampMs: Long): SaveVideoResult =
        saveVideo(
            sourceUri = sourceUri,
            displayName = buildFileName(sessionName, sessionTimestampMs, SessionMediaType.RAW),
            missingSourceError = SaveVideoError.MISSING_FILE,
        )

    fun saveAnnotatedVideo(sourceUri: String?, sessionName: String, sessionTimestampMs: Long): SaveVideoResult {
        if (sourceUri.isNullOrBlank()) return SaveVideoResult.Failure(SaveVideoError.NO_ANNOTATED_OUTPUT)
        return saveVideo(
            sourceUri = sourceUri,
            displayName = buildFileName(sessionName, sessionTimestampMs, SessionMediaType.ANNOTATED),
            missingSourceError = SaveVideoError.NO_ANNOTATED_OUTPUT,
        )
    }

    private fun saveVideo(sourceUri: String, displayName: String, missingSourceError: SaveVideoError): SaveVideoResult {
        val resolver = context.contentResolver
        val inputStream = sourceOpener.openInputStream(sourceUri)
            ?: return SaveVideoResult.Failure(missingSourceError)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/InversionCoach")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val createdUri = resolver.insert(collection, contentValues)
            ?: return SaveVideoResult.Failure(SaveVideoError.SAVE_FAILED)

        return runCatching {
            inputStream.use { input ->
                resolver.openOutputStream(createdUri)?.use { output ->
                    input.copyTo(output)
                } ?: error("Unable to open MediaStore output stream")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(createdUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            SaveVideoResult.Success(createdUri)
        }.getOrElse {
            runCatching { resolver.delete(createdUri, null, null) }
            SaveVideoResult.Failure(SaveVideoError.SAVE_FAILED)
        }
    }

    companion object {
        fun buildFileName(sessionName: String, timestampMs: Long, type: SessionMediaType): String {
            val safeName = sessionName.trim().ifBlank { "session" }
                .replace(Regex("[^A-Za-z0-9_-]+"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .ifBlank { "session" }
            val dateValue = if (timestampMs > 0L) Date(timestampMs) else Date()
            val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val suffix = if (type == SessionMediaType.RAW) "raw" else "annotated"
            return "${safeName}_${formatter.format(dateValue)}_${suffix}.mp4"
        }
    }
}

sealed interface SaveVideoResult {
    data class Success(val uri: Uri) : SaveVideoResult
    data class Failure(val error: SaveVideoError) : SaveVideoResult
}

enum class SaveVideoError {
    MISSING_FILE,
    NO_ANNOTATED_OUTPUT,
    SAVE_FAILED,
}
