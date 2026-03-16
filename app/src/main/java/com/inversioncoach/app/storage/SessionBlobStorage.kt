package com.inversioncoach.app.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

class SessionBlobStorage(
    private val context: Context,
) {
    fun persistRawVideo(sessionId: Long, sourceUri: String): String? =
        persistVideo(sessionId, sourceUri, RAW_MASTER_FILE_NAME)

    fun persistAnnotatedVideo(sessionId: Long, sourceUri: String): String? =
        persistVideo(sessionId, sourceUri, ANNOTATED_MASTER_FILE_NAME)

    fun persistRawFinalVideo(sessionId: Long, sourceUri: String): String? =
        persistVideo(sessionId, sourceUri, RAW_FINAL_FILE_NAME)

    fun persistAnnotatedFinalVideo(sessionId: Long, sourceUri: String): String? =
        persistVideo(sessionId, sourceUri, ANNOTATED_FINAL_FILE_NAME)

    fun persistNotes(sessionId: Long, notes: String): String {
        val notesFile = sessionDir(sessionId).resolve(NOTES_FILE_NAME)
        notesFile.parentFile?.mkdirs()
        notesFile.writeText(notes)
        return notesFile.toURI().toString()
    }

    fun readNotes(sessionId: Long): String? {
        val notesFile = sessionDir(sessionId).resolve(NOTES_FILE_NAME)
        if (!notesFile.exists()) return null
        return notesFile.readText()
    }

    fun sessionSizeBytes(sessionId: Long): Long = directorySizeBytes(sessionDir(sessionId))

    fun totalSizeBytes(): Long = directorySizeBytes(rootDir())

    fun deleteSessionBlob(sessionId: Long) {
        sessionDir(sessionId).deleteRecursively()
    }

    fun deleteVideoFiles(sessionId: Long) {
        listOf(
            RAW_MASTER_FILE_NAME,
            ANNOTATED_MASTER_FILE_NAME,
            RAW_FINAL_FILE_NAME,
            ANNOTATED_FINAL_FILE_NAME,
        ).forEach { sessionDir(sessionId).resolve(it).delete() }
    }

    fun deleteUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return true
        val path = runCatching { Uri.parse(uri).path }.getOrNull() ?: return false
        return runCatching { File(path).delete() }.getOrDefault(false)
    }

    fun sessionWorkingFile(sessionId: Long, fileName: String): File = sessionDir(sessionId).resolve(fileName)

    fun deleteAllBlobs() {
        rootDir().deleteRecursively()
    }

    private fun persistVideo(sessionId: Long, sourceUri: String, targetFileName: String): String? = runCatching {
        val targetFile = sessionDir(sessionId).resolve(targetFileName)
        targetFile.parentFile?.mkdirs()
        val uri = Uri.parse(sourceUri)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { inStream ->
            targetFile.outputStream().use { outStream ->
                inStream.copyTo(outStream)
            }
        }
        targetFile.toURI().toString()
    }.onFailure { throwable ->
        Log.w(TAG, "Unable to persist video blob for session=$sessionId source=$sourceUri", throwable)
    }.getOrNull()

    private fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun rootDir(): File = File(context.filesDir, ROOT_DIRECTORY)

    private fun sessionDir(sessionId: Long): File = File(rootDir(), "session_$sessionId")

    companion object {
        private const val TAG = "SessionBlobStorage"
        private const val ROOT_DIRECTORY = "session_blobs"
        const val RAW_MASTER_FILE_NAME = "raw_master.mp4"
        const val ANNOTATED_MASTER_FILE_NAME = "annotated_master.mp4"
        const val RAW_FINAL_FILE_NAME = "raw_final.mp4"
        const val ANNOTATED_FINAL_FILE_NAME = "annotated_final.mp4"
        private const val NOTES_FILE_NAME = "notes.txt"
    }
}
