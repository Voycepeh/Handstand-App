package com.inversioncoach.app.recording

import android.content.Context
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

    fun startRecording(
        capture: VideoCapture<Recorder>,
        title: String,
        withAudio: Boolean = false,
        onEvent: (VideoRecordEvent) -> Unit,
    ) {
        val outputFile = createOutputFile(title)
        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        var pending: PendingRecording = capture.output.prepareRecording(context, outputOptions)
        if (withAudio) pending = pending.withAudioEnabled()
        recording = pending.start(context.mainExecutor, onEvent)
    }

    fun stopRecording() {
        runCatching {
            recording?.stop()
        }.onFailure { throwable ->
            Log.w(TAG, "Ignoring recorder stop failure", throwable)
        }
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
