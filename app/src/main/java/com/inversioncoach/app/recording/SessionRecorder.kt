package com.inversioncoach.app.recording

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture

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
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, title)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/InversionCoach")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            collection,
        ).setContentValues(values).build()

        var pending: PendingRecording = capture.output.prepareRecording(context, outputOptions)
        if (withAudio) pending = pending.withAudioEnabled()
        recording = pending.start(context.mainExecutor, onEvent)
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }
}
