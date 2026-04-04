package com.inversioncoach.app.ui.drillstudio

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DrillStudioImageStore(private val context: Context) {
    private val imageDir = context.filesDir.resolve("drill_studio/phase_images").apply { mkdirs() }

    suspend fun persistPickedImage(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val extension = extensionForUri(uri)
        val target = File(imageDir, "phase_image_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected image")
        Uri.fromFile(target).toString()
    }

    suspend fun persistCapturedImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val target = File(imageDir, "phase_image_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read captured image")
        Uri.fromFile(target).toString()
    }

    private fun extensionForUri(uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
    }
}
