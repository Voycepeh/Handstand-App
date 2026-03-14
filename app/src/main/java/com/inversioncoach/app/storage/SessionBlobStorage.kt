package com.inversioncoach.app.storage

import android.content.Context
import android.net.Uri
import java.io.File

class SessionBlobStorage(
    private val context: Context,
) {
    fun persistRawVideo(sessionId: Long, sourceUri: String): String? =
        persistVideo(sessionId, sourceUri, RAW_VIDEO_FILE_NAME)

    fun persistAnnotatedVideo(sessionId: Long, sourceUri: String): String? =
        persistVideo(sessionId, sourceUri, ANNOTATED_VIDEO_FILE_NAME)

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

    fun deleteSessionBlob(sessionId: Long) {
        sessionDir(sessionId).deleteRecursively()
    }

    fun deleteAllBlobs() {
        rootDir().deleteRecursively()
    }

    private fun persistVideo(sessionId: Long, sourceUri: String, targetFileName: String): String? {
        val targetFile = sessionDir(sessionId).resolve(targetFileName)
        targetFile.parentFile?.mkdirs()
        val uri = Uri.parse(sourceUri)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { inStream ->
            targetFile.outputStream().use { outStream ->
                inStream.copyTo(outStream)
            }
        }
        return targetFile.toURI().toString()
    }

    private fun rootDir(): File = File(context.filesDir, ROOT_DIRECTORY)

    private fun sessionDir(sessionId: Long): File = File(rootDir(), "session_$sessionId")

    companion object {
        private const val ROOT_DIRECTORY = "session_blobs"
        private const val RAW_VIDEO_FILE_NAME = "raw.mp4"
        private const val ANNOTATED_VIDEO_FILE_NAME = "annotated.mp4"
        private const val NOTES_FILE_NAME = "notes.txt"
    }
}
