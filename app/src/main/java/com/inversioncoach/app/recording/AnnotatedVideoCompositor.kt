package com.inversioncoach.app.recording

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
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
import com.inversioncoach.app.overlay.OverlayRenderModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
        preset: ExportPreset = ExportPreset.BALANCED,
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
            val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
            val scale = (preset.targetHeight.toFloat() / sourceHeight.toFloat()).coerceAtMost(1f)
            val width = ((sourceWidth * scale) / 2f).roundToInt().coerceAtLeast(2) * 2
            val height = ((sourceHeight * scale) / 2f).roundToInt().coerceAtLeast(2) * 2
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) {
                Log.w(TAG, "export_failure reason=duration_unavailable")
                return@withContext null
            }

            val workerCount = computeWorkerCount()
            val queueCapacity = (workerCount * 3).coerceAtLeast(6)
            val totalFrames = ((durationMs / 1000f) * preset.outputFps).roundToInt().coerceAtLeast(1)
            val resolver = OverlayTimelineResolver(overlayFrames)

            Log.d(
                TAG,
                "export_start preset=${preset.name} workers=$workerCount queueCapacity=$queueCapacity totalFrames=$totalFrames " +
                    "width=$width height=$height durationMs=$durationMs overlaySamples=${overlayFrames.size}",
            )

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, computeBitrate(width, height, preset))
                setInteger(MediaFormat.KEY_FRAME_RATE, preset.outputFps)
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

            val decodeChannel = Channel<DecodedFrame>(capacity = queueCapacity)
            val preparedChannel = Channel<PreparedFrame>(capacity = queueCapacity)
            val decodeElapsedMs = AtomicLong(0L)
            val resolveElapsedMs = AtomicLong(0L)
            val instructionElapsedMs = AtomicLong(0L)
            val renderElapsedMs = AtomicLong(0L)
            val maxPendingFrames = AtomicInteger(0)
            val decodedCount = AtomicInteger(0)

            coroutineScope {
                val decodeJob = launch {
                    val decodeStart = System.currentTimeMillis()
                    for (idx in 0 until totalFrames) {
                        val frameTimeUs = (idx * 1_000_000L) / preset.outputFps
                        val bitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val scaled = if (bitmap.width != width || bitmap.height != height) {
                            Bitmap.createScaledBitmap(bitmap, width, height, true).also { bitmap.recycle() }
                        } else {
                            bitmap
                        }
                        decodeChannel.send(DecodedFrame(index = idx, frameTimeUs = frameTimeUs, bitmap = scaled))
                        decodedCount.incrementAndGet()
                        if (idx % 30 == 0) {
                            Log.d(TAG, "stage=decode progress=${idx + 1}/$totalFrames")
                        }
                    }
                    decodeElapsedMs.set(System.currentTimeMillis() - decodeStart)
                    decodeChannel.close()
                }

                val workers = (0 until workerCount).map {
                    async {
                        for (decoded in decodeChannel) {
                            val resolveStart = System.nanoTime()
                            val overlay = resolver.overlayAt(decoded.frameTimeUs / 1000L)
                            resolveElapsedMs.addAndGet(((System.nanoTime() - resolveStart) / 1_000_000L))
                            val instructionStart = System.nanoTime()
                            val instruction = buildRenderInstruction(overlay, drillType, drillCameraSide)
                            instructionElapsedMs.addAndGet(((System.nanoTime() - instructionStart) / 1_000_000L))
                            preparedChannel.send(
                                PreparedFrame(
                                    index = decoded.index,
                                    bitmap = decoded.bitmap,
                                    instruction = instruction,
                                ),
                            )
                        }
                    }
                }

                val encoderJob = launch {
                    val ordered = OrderedFrameBuffer<PreparedFrame>()
                    var next = 0
                    var rendered = 0
                    val canvasDest = Rect(0, 0, width, height)
                    for (prepared in preparedChannel) {
                        ordered.put(prepared.index, prepared)
                        if (ordered.size() > maxPendingFrames.get()) {
                            maxPendingFrames.set(ordered.size())
                        }
                        while (true) {
                            val frame = ordered.pop(next) ?: break
                            val renderStart = System.nanoTime()
                            val canvas = inputSurface.lockCanvas(null)
                            canvas.drawBitmap(frame.bitmap, null, canvasDest, null)
                            drawInstruction(canvas, width, height, frame.instruction)
                            inputSurface.unlockCanvasAndPost(canvas)
                            frame.bitmap.recycle()
                            renderElapsedMs.addAndGet((System.nanoTime() - renderStart) / 1_000_000L)
                            drain(endOfStream = false)
                            rendered++
                            if (rendered % 30 == 0 || rendered == totalFrames) {
                                Log.d(TAG, "stage=encode progress=$rendered/$totalFrames")
                            }
                            onProgress(rendered, totalFrames)
                            next++
                        }
                    }
                }

                decodeJob.join()
                workers.awaitAll()
                preparedChannel.close()
                encoderJob.join()
            }

            drain(endOfStream = true)
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }

            val outputUri = Uri.fromFile(output).toString()
            val verifyStart = System.currentTimeMillis()
            val readable = verifyReadableOutput(outputUri)
            val verifyElapsedMs = System.currentTimeMillis() - verifyStart
            val totalElapsedMs = decodeElapsedMs.get() + resolveElapsedMs.get() + instructionElapsedMs.get() + renderElapsedMs.get() + verifyElapsedMs
            Log.d(
                TAG,
                "export_telemetry preset=${preset.name} workers=$workerCount decoded=${decodedCount.get()} rendered=$totalFrames " +
                    "overlaySamples=${overlayFrames.size} decodeMs=${decodeElapsedMs.get()} resolveMs=${resolveElapsedMs.get()} " +
                    "instructionMs=${instructionElapsedMs.get()} renderEncodeMs=${renderElapsedMs.get()} verifyMs=$verifyElapsedMs " +
                    "totalMs=$totalElapsedMs maxPendingFrames=${maxPendingFrames.get()}",
            )

            if (!output.exists() || output.length() <= 0L || !readable) {
                Log.w(TAG, "export_failure reason=verification_failed uri=$outputUri")
                output.delete()
                return@withContext null
            }
            if (debugValidation) {
                val verified = verifyAnnotatedDifference(rawVideoUri, outputUri)
                Log.d(TAG, "debug_validation overlay_present=$verified")
            }
            outputUri
        } catch (t: Throwable) {
            Log.e(TAG, "export_failure", t)
            output.delete()
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun buildRenderInstruction(
        overlay: AnnotatedOverlayFrame?,
        drillType: DrillType,
        drillCameraSide: DrillCameraSide,
    ): RenderInstruction {
        if (overlay == null) return RenderInstruction()
        val joints = overlay.smoothedLandmarks.ifEmpty { overlay.landmarks }
        val model = OverlayGeometry.build(
            drillType = drillType,
            sessionMode = overlay.sessionMode,
            joints = joints,
            drillCameraSide = overlay.drillCameraSide ?: drillCameraSide,
            freestyleViewMode = overlay.freestyleViewMode,
        )
        return RenderInstruction(
            model = model,
            drawSkeleton = overlay.showSkeleton,
            drawIdealLine = overlay.showIdealLine,
        )
    }

    private fun drawInstruction(canvas: Canvas, width: Int, height: Int, instruction: RenderInstruction) {
        val model = instruction.model ?: return
        OverlayFrameRenderer.drawAndroid(
            canvas = canvas,
            width = width,
            height = height,
            model = model,
            frame = OverlayDrawingFrame(
                drawSkeleton = instruction.drawSkeleton,
                drawIdealLine = instruction.drawIdealLine,
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

    private fun computeWorkerCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores - 2).coerceAtLeast(1).coerceAtMost(4)
    }

    private fun computeBitrate(width: Int, height: Int, preset: ExportPreset): Int = when (preset) {
        ExportPreset.FAST -> (width * height * 4).coerceAtLeast(1_000_000)
        ExportPreset.BALANCED -> (width * height * 6).coerceAtLeast(1_500_000)
        ExportPreset.HIGH -> (width * height * 8).coerceAtLeast(2_500_000)
    }

    private data class DecodedFrame(
        val index: Int,
        val frameTimeUs: Long,
        val bitmap: Bitmap,
    )

    private data class PreparedFrame(
        val index: Int,
        val bitmap: Bitmap,
        val instruction: RenderInstruction,
    )

    private data class RenderInstruction(
        val model: OverlayRenderModel? = null,
        val drawSkeleton: Boolean = false,
        val drawIdealLine: Boolean = false,
    )
}
