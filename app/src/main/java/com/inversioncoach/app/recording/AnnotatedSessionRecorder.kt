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
import android.util.Log
import android.view.WindowManager
import java.io.File

private const val TAG = "AnnotatedSessionRecorder"
private const val TARGET_FRAME_RATE = 24
private const val MAX_VIDEO_EDGE_PX = 1280
private const val MIN_VIDEO_EDGE_PX = 480

/**
 * Captures the annotated replay as a screen recording of the live coaching UI.
 */
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
        val projection = context
            .getSystemService(MediaProjectionManager::class.java)
            .getMediaProjection(resultCode, resultData)

        return startRecording(projection = projection, title = title, onError = onError)
    }

    private fun startRecording(
        projection: MediaProjection?,
        title: String,
        onError: (String) -> Unit,
    ): Boolean = runCatching {
        val activeProjection = projection ?: throw IllegalStateException("Unable to create screen capture session")
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
            setVideoEncodingBitRate(targetBitrate(width, height))
            setVideoFrameRate(TARGET_FRAME_RATE)
            val (videoWidth, videoHeight) = targetVideoSize(width, height)
            setVideoSize(videoWidth, videoHeight)
            prepare()
        }

        val display = activeProjection.createVirtualDisplay(
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
        mediaProjection = activeProjection
        mediaRecorder = recorder
        virtualDisplay = display
        activeOutputFile = outputFile
        isRecording = true
        true
    }.onFailure { throwable ->
        Log.e(TAG, "Unable to start annotated replay screen recording", throwable)
        releaseResources()
        onError("Unable to start annotated replay screen recording")
    }.getOrElse { false }

    fun stopRecording(): String? {
        if (!isRecording) return null
        runCatching { mediaRecorder?.stop() }
            .onFailure { Log.w(TAG, "Annotated replay stop failed", it) }
        val outputUri = activeOutputFile
            ?.takeIf { it.exists() }
            ?.let { Uri.fromFile(it).toString() }
        releaseResources()
        return outputUri
    }

    private fun releaseResources() {
        runCatching { virtualDisplay?.release() }
        runCatching { mediaProjection?.stop() }
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        virtualDisplay = null
        mediaProjection = null
        mediaRecorder = null
        isRecording = false
    }

    private fun screenMetrics(): Triple<Int, Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    private fun targetVideoSize(width: Int, height: Int): Pair<Int, Int> {
        val longEdge = maxOf(width, height)
        if (longEdge <= MAX_VIDEO_EDGE_PX) return width to height

        val scale = MAX_VIDEO_EDGE_PX.toFloat() / longEdge.toFloat()
        return (width * scale).toInt().coerceAtLeast(MIN_VIDEO_EDGE_PX) to
            (height * scale).toInt().coerceAtLeast(MIN_VIDEO_EDGE_PX)
    }

    private fun targetBitrate(width: Int, height: Int): Int {
        val pixels = width * height
        return when {
            pixels >= 1920 * 1080 -> 3_000_000
            pixels >= 1280 * 720 -> 2_000_000
            else -> 1_200_000
        }
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
