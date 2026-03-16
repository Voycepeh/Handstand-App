package com.inversioncoach.app.recording

import android.content.Context
import android.net.Uri
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import android.util.Log
import java.io.File

private const val TAG = "SessionRecorder"

class SessionRecorder(
    private val context: Context,
) {
    private var recording: Recording? = null
    private var activeOutputFile: File? = null

    fun startRecording(
        capture: VideoCapture<Recorder>,
        title: String,
        withAudio: Boolean = false,
        onEvent: (VideoRecordEvent) -> Unit,
    ) {
        stopRecording()
        val outputFile = createOutputFile(title)
        activeOutputFile = outputFile
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        Log.d(TAG, "recording_started title=$title output=${outputFile.absolutePath} withAudio=$withAudio")

        var pending: PendingRecording = capture.output.prepareRecording(context, outputOptions)
        if (withAudio) pending = pending.withAudioEnabled()
        recording = pending.start(context.mainExecutor, onEvent)
    }

    fun fallbackOutputUri(): String? =
        activeOutputFile
            ?.takeIf { it.exists() }
            ?.let { Uri.fromFile(it).toString() }

    fun stopRecording() {
        runCatching {
            recording?.stop()
        }.onFailure { throwable ->
            Log.w(TAG, "Ignoring recorder stop failure", throwable)
        }
        Log.d(TAG, "recording_stop_requested")
        recording = null
    }

    private fun createOutputFile(title: String): File {
        val recordingsDir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val safeTitle = title
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "session" }
        return File(recordingsDir, "${safeTitle}_${System.currentTimeMillis()}.mp4")
    }
}
