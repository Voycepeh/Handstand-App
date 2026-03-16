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
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.OverlayDrawingFrame
import com.inversioncoach.app.overlay.OverlayFrameRenderer
import com.inversioncoach.app.overlay.OverlayGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "AnnotatedVideoCompositor"
private const val EXPORT_FPS = 30
private const val MAX_SAMPLE_DELTA_MS = 200L

class AnnotatedVideoCompositor(
    private val context: Context,
) {
    suspend fun export(
        rawVideoUri: String,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
        overlayFrames: List<AnnotatedOverlayFrame>,
        debugValidation: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
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
            val sourceFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.roundToInt()
                ?.coerceIn(15, 60) ?: EXPORT_FPS
            if (durationMs <= 0L) {
                Log.w(TAG, "Cannot export annotated video because duration metadata is unavailable")
                return@withContext null
            }

            Log.d(TAG, "export_start uri=$rawVideoUri width=$width height=$height durationMs=$durationMs sourceFps=$sourceFps exportFps=$EXPORT_FPS")
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 6).coerceAtLeast(1_500_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, EXPORT_FPS)
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

            val totalFrames = ((durationMs / 1000f) * EXPORT_FPS).roundToInt().coerceAtLeast(1)
            val timeline = OverlayTimeline(overlayFrames.sortedBy { it.timestampMs })
            val timestampStart = overlayFrames.first().timestampMs
            repeat(totalFrames) { idx ->
                val frameTimeUs = (idx * 1_000_000L) / EXPORT_FPS
                val frameTimeMs = frameTimeUs / 1000L
                val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return@repeat
                val canvas = inputSurface.lockCanvas(null)
                drawFrame(
                    canvas = canvas,
                    bitmap = bitmap,
                    width = width,
                    height = height,
                    overlay = timeline.overlayAt(timestampStart + frameTimeMs),
                    drillType = drillType,
                    drillCameraSide = drillCameraSide,
                )
                inputSurface.unlockCanvasAndPost(canvas)
                bitmap.recycle()
                if (idx % EXPORT_FPS == 0) {
                    Log.d(TAG, "frame_render_progress frame=${idx + 1}/$totalFrames timeMs=$frameTimeMs")
                }
                if (idx % 3 == 0 || idx == totalFrames - 1) {
                    onProgress(idx + 1, totalFrames)
                }
                drain(endOfStream = false)
            }

            drain(endOfStream = true)
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
            val outputUri = Uri.fromFile(output).toString()
            if (!output.exists() || output.length() <= 0L) {
                Log.w(TAG, "export_failure reason=output_missing_or_empty path=${output.absolutePath}")
                output.delete()
                return@withContext null
            }
            if (!verifyReadableOutput(outputUri)) {
                Log.w(TAG, "export_failure reason=output_not_readable uri=$outputUri")
                output.delete()
                return@withContext null
            }
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
            sessionMode = overlay.sessionMode,
            joints = joints,
            drillCameraSide = overlay.drillCameraSide ?: drillCameraSide,
        )
        OverlayFrameRenderer.drawAndroid(
            canvas = canvas,
            width = width,
            height = height,
            model = renderModel,
            frame = OverlayDrawingFrame(
                drawSkeleton = overlay.showSkeleton,
                drawIdealLine = overlay.showIdealLine,
            ),
        )
    }

    private fun verifyReadableOutput(annotatedVideoUri: String): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(annotatedVideoUri))
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(250_000L, MediaMetadataRetriever.OPTION_CLOSEST)
            val readable = durationMs > 0L && frame != null
            frame?.recycle()
            readable
        } catch (t: Throwable) {
            Log.w(TAG, "output_verification_error", t)
            false
        } finally {
            runCatching { retriever.release() }
        }
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

    private class OverlayTimeline(frames: List<AnnotatedOverlayFrame>) {
        private val samples = frames.sortedBy { it.timestampMs }
        private var lowerIndex = 0

        fun overlayAt(targetTimestampMs: Long): AnnotatedOverlayFrame? {
            if (samples.isEmpty()) return null
            while (lowerIndex < samples.lastIndex && samples[lowerIndex + 1].timestampMs <= targetTimestampMs) {
                lowerIndex++
            }
            val previous = samples[lowerIndex]
            val next = samples.getOrNull(lowerIndex + 1)
            val nearest = when {
                next == null -> previous
                abs(previous.timestampMs - targetTimestampMs) <= abs(next.timestampMs - targetTimestampMs) -> previous
                else -> next
            }
            if (abs(nearest.timestampMs - targetTimestampMs) > MAX_SAMPLE_DELTA_MS) {
                Log.d(TAG, "missing_pose_frame timestampMs=$targetTimestampMs nearestDeltaMs=${abs(nearest.timestampMs - targetTimestampMs)}")
                return null
            }
            if (next == null || previous.timestampMs == next.timestampMs) return nearest
            if (targetTimestampMs <= previous.timestampMs) return previous
            if (targetTimestampMs >= next.timestampMs) return next
            val span = (next.timestampMs - previous.timestampMs).toFloat().coerceAtLeast(1f)
            val t = ((targetTimestampMs - previous.timestampMs).toFloat() / span).coerceIn(0f, 1f)
            return interpolate(previous, next, t)
        }

        private fun interpolate(previous: AnnotatedOverlayFrame, next: AnnotatedOverlayFrame, t: Float): AnnotatedOverlayFrame {
            val prevByName = previous.smoothedLandmarks.associateBy { it.name }
            val nextByName = next.smoothedLandmarks.associateBy { it.name }
            val names = (prevByName.keys + nextByName.keys).sorted()
            val interpolated = names.mapNotNull { name ->
                val a = prevByName[name]
                val b = nextByName[name]
                when {
                    a != null && b != null -> JointPoint(
                        name = name,
                        x = lerp(a.x, b.x, t),
                        y = lerp(a.y, b.y, t),
                        z = lerp(a.z, b.z, t),
                        visibility = lerp(a.visibility, b.visibility, t),
                    )
                    a != null -> a
                    else -> b
                }
            }
            return previous.copy(
                timestampMs = lerp(previous.timestampMs.toFloat(), next.timestampMs.toFloat(), t).toLong(),
                landmarks = interpolated,
                smoothedLandmarks = interpolated,
                confidence = lerp(previous.confidence, next.confidence, t),
                bodyVisible = previous.bodyVisible || next.bodyVisible,
                showSkeleton = previous.showSkeleton || next.showSkeleton,
                showIdealLine = previous.showIdealLine || next.showIdealLine,
            )
        }

        private fun lerp(a: Float, b: Float, t: Float): Float = a + ((b - a) * t)
    }
}
