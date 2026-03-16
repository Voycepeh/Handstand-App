package com.inversioncoach.app.recording

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.OverlayDrawingFrame
import com.inversioncoach.app.overlay.OverlayFrameRenderer
import com.inversioncoach.app.overlay.OverlayGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val TAG = "AnnotatedVideoCompositor"

class AnnotatedVideoCompositor(
    private val context: Context,
) {
    suspend fun export(
        rawVideoUri: String,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
        overlayFrames: List<AnnotatedOverlayFrame>,
        debugValidation: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        if (overlayFrames.isEmpty()) return@withContext null
        val retriever = MediaMetadataRetriever()
        val output = File(context.cacheDir, "recordings/annotated_${System.currentTimeMillis()}.mp4").apply {
            parentFile?.mkdirs()
        }

        try {
            val rawUri = Uri.parse(rawVideoUri)
            retriever.setDataSource(context, rawUri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.roundToInt()
                ?.coerceIn(15, 60) ?: 30
            if (durationMs <= 0L) {
                Log.w(TAG, "Cannot export annotated video because duration metadata is unavailable")
                return@withContext null
            }

            Log.d(TAG, "export_start uri=$rawVideoUri width=$width height=$height durationMs=$durationMs fps=$frameRate")
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 6).coerceAtLeast(1_500_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()

            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            var muxerTrack = -1
            var muxerStarted = false
            fun drain(endOfStream: Boolean) {
                if (endOfStream) codec.signalEndOfInputStream()
                while (true) {
                    val index = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) error("Output format changed twice")
                            muxerTrack = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        index >= 0 -> {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && muxerStarted) {
                                val data = codec.getOutputBuffer(index) ?: error("Missing codec output buffer")
                                data.position(bufferInfo.offset)
                                data.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerTrack, data, bufferInfo)
                            }
                            codec.releaseOutputBuffer(index, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            val totalFrames = ((durationMs / 1000f) * frameRate).roundToInt().coerceAtLeast(1)
            val timestampStart = overlayFrames.first().timestampMs
            repeat(totalFrames) { idx ->
                val frameTimeUs = (idx * 1_000_000L) / frameRate
                val frameTimeMs = frameTimeUs / 1000L
                val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return@repeat
                val canvas = inputSurface.lockCanvas(null)
                drawFrame(
                    canvas = canvas,
                    bitmap = bitmap,
                    width = width,
                    height = height,
                    overlay = overlayFor(overlayFrames, timestampStart + frameTimeMs),
                    drillType = drillType,
                    drillCameraSide = drillCameraSide,
                )
                inputSurface.unlockCanvasAndPost(canvas)
                bitmap.recycle()
                if (idx % frameRate == 0) {
                    Log.d(TAG, "frame_render_progress frame=${idx + 1}/$totalFrames timeMs=$frameTimeMs")
                }
                drain(endOfStream = false)
            }

            drain(endOfStream = true)
            codec.stop()
            codec.release()
            inputSurface.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
            val outputUri = Uri.fromFile(output).toString()
            if (debugValidation) {
                val verified = verifyAnnotatedDifference(rawVideoUri, outputUri)
                Log.d(TAG, "debug_validation overlay_present=$verified")
                if (!verified) {
                    Log.w(TAG, "debug_validation_failed overlay not detected in exported clip")
                }
            }
            Log.d(TAG, "export_complete output=${output.absolutePath}")
            outputUri
        } catch (t: Throwable) {
            Log.e(TAG, "export_failure", t)
            output.delete()
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun drawFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        overlay: AnnotatedOverlayFrame?,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
    ) {
        val dest = android.graphics.Rect(0, 0, width, height)
        canvas.drawBitmap(bitmap, null, dest, null)
        if (overlay == null) return
        val joints = overlay.smoothedLandmarks.ifEmpty { overlay.landmarks }
        val renderModel = OverlayGeometry.build(
            drillType = drillType,
            sessionMode = overlay.orientation,
            joints = joints,
            drillCameraSide = drillCameraSide,
        )
        OverlayFrameRenderer.drawAndroid(
            canvas = canvas,
            width = width,
            height = height,
            model = renderModel,
            frame = OverlayDrawingFrame(
                drawSkeleton = overlay.drawSkeleton,
                drawIdealLine = overlay.drawIdealLine,
            ),
        )
    }



    private fun verifyAnnotatedDifference(rawVideoUri: String, annotatedVideoUri: String): Boolean {
        val rawRetriever = MediaMetadataRetriever()
        val outRetriever = MediaMetadataRetriever()
        return try {
            rawRetriever.setDataSource(context, Uri.parse(rawVideoUri))
            outRetriever.setDataSource(context, Uri.parse(annotatedVideoUri))
            val rawFrame = rawRetriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST) ?: return false
            val outFrame = outRetriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST) ?: return false
            val width = minOf(rawFrame.width, outFrame.width)
            val height = minOf(rawFrame.height, outFrame.height)
            var changed = 0
            var checked = 0
            val xStep = (width / 30).coerceAtLeast(1)
            val yStep = (height / 30).coerceAtLeast(1)
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    checked++
                    if (rawFrame.getPixel(x, y) != outFrame.getPixel(x, y)) changed++
                    x += xStep
                }
                y += yStep
            }
            rawFrame.recycle()
            outFrame.recycle()
            checked > 0 && (changed.toDouble() / checked) > 0.005
        } catch (t: Throwable) {
            Log.w(TAG, "debug_validation_error", t)
            false
        } finally {
            runCatching { rawRetriever.release() }
            runCatching { outRetriever.release() }
        }
    }

    private fun overlayFor(frames: List<AnnotatedOverlayFrame>, targetTimestampMs: Long): AnnotatedOverlayFrame? {
        val nearest = frames.minByOrNull { kotlin.math.abs(it.timestampMs - targetTimestampMs) } ?: return null
        val delta = kotlin.math.abs(nearest.timestampMs - targetTimestampMs)
        if (delta > 200L) {
            Log.d(TAG, "missing_pose_frame timestampMs=$targetTimestampMs nearestDeltaMs=$delta")
            return null
        }
        return nearest
    }
}
