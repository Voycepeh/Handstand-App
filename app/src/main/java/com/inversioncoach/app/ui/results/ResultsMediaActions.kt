package com.inversioncoach.app.ui.results

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.inversioncoach.app.media.ResolvedSessionMedia
import com.inversioncoach.app.media.SaveVideoError
import com.inversioncoach.app.media.SaveVideoResult
import com.inversioncoach.app.media.SessionMediaSourceOpener
import com.inversioncoach.app.media.SessionVideoSaver
import com.inversioncoach.app.ui.live.SessionDiagnostics

class ResultsMediaActions(
    private val context: Context,
    private val sourceOpener: SessionMediaSourceOpener,
    private val videoSaver: SessionVideoSaver,
) {
    fun openVideo(videoUri: String?, sessionId: Long? = null) {
        if (videoUri.isNullOrBlank()) return
        SessionDiagnostics.record(
            sessionId = sessionId,
            stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
            status = SessionDiagnostics.Status.PROGRESS,
            message = "player preparing",
            metrics = mapOf("uri" to videoUri),
        )
        val resolvedUri = sourceOpener.toSharableUri(videoUri) ?: run {
            Toast.makeText(context, "Unable to open video file.", Toast.LENGTH_SHORT).show()
            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
                status = SessionDiagnostics.Status.FAILED,
                message = "onPlayerError: unresolved sharable URI",
                errorCode = "PLAYER_URI_RESOLUTION_FAILED",
                metrics = mapOf("uri" to videoUri),
            )
            return
        }
        val mimeType = context.contentResolver.getType(resolvedUri) ?: "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(resolvedUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            val chooser = Intent.createChooser(intent, "Open video")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooser)
            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
                status = SessionDiagnostics.Status.SUCCEEDED,
                message = "player ready",
                metrics = mapOf("uri" to videoUri),
            )
        }.onFailure { error ->
            if (error is ActivityNotFoundException) {
                Toast.makeText(context, "No video player available to open this file.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Unable to open video file.", Toast.LENGTH_SHORT).show()
            }
            SessionDiagnostics.record(
                sessionId = sessionId,
                stage = SessionDiagnostics.Stage.REPLAY_SOURCE_SELECT,
                status = SessionDiagnostics.Status.FAILED,
                message = "onPlayerError: ${error::class.java.name}",
                errorCode = error.message,
                throwable = error,
                metrics = mapOf("uri" to videoUri),
            )
        }
    }

    fun saveRaw(sourceUri: String, sessionName: String, sessionTimestampMs: Long) {
        handleSaveResult(
            videoSaver.saveRawVideo(
                sourceUri = sourceUri,
                sessionName = sessionName,
                sessionTimestampMs = sessionTimestampMs,
            ),
            isAnnotated = false,
        )
    }

    fun saveAnnotated(sourceUri: String, sessionName: String, sessionTimestampMs: Long) {
        handleSaveResult(
            videoSaver.saveAnnotatedVideo(
                sourceUri = sourceUri,
                sessionName = sessionName,
                sessionTimestampMs = sessionTimestampMs,
            ),
            isAnnotated = true,
        )
    }

    fun sharePreferred(media: ResolvedSessionMedia?) {
        val source = media?.canonicalActionSource()?.uri
        if (source.isNullOrBlank() || !sourceOpener.isReadable(source)) {
            Toast.makeText(context, "No video file available to share.", Toast.LENGTH_SHORT).show()
            return
        }
        val preferredShareUri = sourceOpener.toSharableUri(source)
        if (preferredShareUri == null) {
            Toast.makeText(context, "No video file available to share.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, preferredShareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "session-video", preferredShareUri)
        }
        context.startActivity(Intent.createChooser(intent, "Share session video"))
    }

    private fun handleSaveResult(result: SaveVideoResult, isAnnotated: Boolean) {
        when (result) {
            is SaveVideoResult.Success -> {
                val message = if (isAnnotated) "Saved annotated video to device" else "Saved raw video to device"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }

            is SaveVideoResult.Failure -> {
                val message = when (result.error) {
                    SaveVideoError.MISSING_FILE -> "Source video file is missing"
                    SaveVideoError.SAVE_FAILED -> "Save failed"
                    SaveVideoError.NO_ANNOTATED_OUTPUT -> "No annotated output yet"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
