package com.inversioncoach.app.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream

class SessionMediaSourceOpener(
    private val context: Context,
    private val openContentInputStream: (Uri) -> InputStream? = { uri -> context.contentResolver.openInputStream(uri) },
) {
    fun resolveUri(source: String?): Uri? {
        if (source.isNullOrBlank()) return null
        return when {
            source.startsWith("content://") -> Uri.parse(source)
            source.startsWith("file://") -> Uri.parse(source)
            source.startsWith("/") -> Uri.fromFile(File(source))
            else -> {
                val parsed = Uri.parse(source)
                if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(source)) else parsed
            }
        }
    }

    fun openInputStream(source: String?): InputStream? {
        val uri = resolveUri(source) ?: return null
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return null
                val file = File(path)
                if (!file.exists()) return null
                file.inputStream()
            }

            "content" -> openContentInputStream(uri)
            else -> null
        }
    }

    fun isReadable(source: String?): Boolean = runCatching {
        openInputStream(source)?.use { true } ?: false
    }.getOrDefault(false)

    fun toSharableUri(source: String?): Uri? {
        val uri = resolveUri(source) ?: return null
        return when (uri.scheme) {
            "content" -> uri
            "file" -> {
                val file = uri.path?.let(::File) ?: return null
                if (!file.exists()) return null
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            else -> null
        }
    }
}
