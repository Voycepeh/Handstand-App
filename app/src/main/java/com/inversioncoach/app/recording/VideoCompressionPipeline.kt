package com.inversioncoach.app.recording

import android.net.Uri
import com.inversioncoach.app.model.AnnotatedExportFailureReason
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class VideoCompressionPipeline(
    private val timeoutMs: Long = 20_000L,
) {
    data class CompressionResult(
        val outputUri: String? = null,
        val failureReason: AnnotatedExportFailureReason? = null,
    )

    suspend fun compressTo(
        sourceUri: String,
        outputFile: File,
    ): CompressionResult {
        val completed = withTimeoutOrNull(timeoutMs) {
            runCatching {
                val sourceFile = File(Uri.parse(sourceUri).path ?: return@runCatching false)
                outputFile.parentFile?.mkdirs()
                sourceFile.inputStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }.getOrDefault(false)
        } ?: return CompressionResult(failureReason = AnnotatedExportFailureReason.ANNOTATED_COMPRESSION_FAILED)

        if (!completed) {
            return CompressionResult(failureReason = AnnotatedExportFailureReason.ANNOTATED_COMPRESSION_FAILED)
        }
        return CompressionResult(outputUri = outputFile.toURI().toString())
    }
}
