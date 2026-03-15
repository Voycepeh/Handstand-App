package com.inversioncoach.app.recording

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File

class AnnotatedSessionRecorder(
    private val context: Context,
) {
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var activeOutputFile: File? = null
    private var isRecording: Boolean = false

    fun startRecording(
        resultCode: Int,
        resultData: Intent,
        title: String,
        onError: (String) -> Unit,
    ): Boolean {
        if (isRecording) return true
        return runCatching {
            val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("Unable to create screen capture session")

            val (width, height, densityDpi) = screenMetrics()
            val outputFile = createOutputFile(title)

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile.absolutePath)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(8_000_000)
                setVideoFrameRate(30)
                setVideoSize(width, height)
                prepare()
            }

            val display = projection.createVirtualDisplay(
                "AnnotatedSessionCapture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null,
            )

            recorder.start()
            mediaProjection = projection
            mediaRecorder = recorder
            virtualDisplay = display
            activeOutputFile = outputFile
            isRecording = true
            true
        }.onFailure {
            releaseResources()
            onError("Unable to start annotated screen recording")
        }.getOrElse { false }
    }

    fun stopRecording(): String? {
        if (!isRecording) return null
        runCatching { mediaRecorder?.stop() }
        val outputUri = activeOutputFile
            ?.takeIf { it.exists() }
            ?.let { Uri.fromFile(it).toString() }
        releaseResources()
        return outputUri
    }

    private fun releaseResources() {
        runCatching { virtualDisplay?.release() }
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        runCatching { mediaProjection?.stop() }
        virtualDisplay = null
        mediaRecorder = null
        mediaProjection = null
        isRecording = false
    }

    private fun screenMetrics(): Triple<Int, Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    private fun createOutputFile(title: String): File {
        val recordingsDir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val safeTitle = title
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "session" }
        return File(recordingsDir, "${safeTitle}_annotated_${System.currentTimeMillis()}.mp4")
    }
}
