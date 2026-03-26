package com.inversioncoach.app.recording

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import com.inversioncoach.app.model.AnnotatedExportFailureReason
import com.inversioncoach.app.model.DrillType
import com.inversioncoach.app.overlay.DrillCameraSide
import com.inversioncoach.app.overlay.OverlayDrawingFrame
import com.inversioncoach.app.overlay.OverlayFrameRenderer
import com.inversioncoach.app.overlay.OverlayGeometry
import com.inversioncoach.app.overlay.OverlayRenderModel
import com.inversioncoach.app.overlay.OverlayRenderTarget
import com.inversioncoach.app.pose.PoseScaleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

private const val TAG = "AnnotatedVideoCompositor"
private const val FIRST_FRAME_DECODE_TIMEOUT_MS = 2_500L
private const val FRAME_SYNC_TIMEOUT_MS = 500L
private const val EXPORT_DURATION_TOLERANCE_MS = 750L

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
        onTelemetry: (AnnotatedExportTelemetry) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        val telemetry = AnnotatedExportTelemetry(
            exportStartedAtMs = System.currentTimeMillis(),
            overlayFramesAvailable = overlayFrames.size,
        )
        onTelemetry(telemetry)

        val output = File(context.cacheDir, "recordings/annotated_${System.currentTimeMillis()}.mp4").apply {
            parentFile?.mkdirs()
        }

        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var glCompositor: GlSurfaceCompositor? = null
        var muxer: MediaMuxer? = null
        var muxStarted = false
        var muxStopped = false
        var exportStarted = false
        var firstDecodeObserved = false

        fun fail(reason: AnnotatedExportFailureReason): String? {
            telemetry.failureReason = reason.name
            telemetry.exportCompletedAtMs = System.currentTimeMillis()
            onTelemetry(telemetry)
            output.delete()
            return null
        }

        try {
            val rawUri = Uri.parse(rawVideoUri)
            extractor = try {
                MediaExtractor().apply { setDataSource(context, rawUri, null) }
            } catch (_: Throwable) {
                return@withContext fail(AnnotatedExportFailureReason.EXTRACTOR_INIT_FAILED)
            }
            val extractorInstance = extractor ?: return@withContext fail(AnnotatedExportFailureReason.EXTRACTOR_INIT_FAILED)

            val videoTrack = (0 until extractorInstance.trackCount).firstOrNull {
                extractorInstance.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return@withContext fail(AnnotatedExportFailureReason.VIDEO_TRACK_NOT_FOUND)

            extractorInstance.selectTrack(videoTrack)
            val inputFormat = extractorInstance.getTrackFormat(videoTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: return@withContext fail(AnnotatedExportFailureReason.DECODER_INIT_FAILED)
            val sourceMetadata = readSourceVideoMetadata(inputFormat, rawUri)
            val transform = buildExportTransform(sourceMetadata, preset)
            val estimatedTotalFrames = if (sourceMetadata.durationUs > 0L) {
                ((sourceMetadata.durationUs / 1_000_000f) * preset.outputFps).roundToInt().coerceAtLeast(1)
            } else {
                1
            }
            val firstOverlayTs = overlayFrames.firstOrNull()?.timestampMs
            val lastOverlayTs = overlayFrames.lastOrNull()?.timestampMs
            val overlaySpanMs = if (firstOverlayTs != null && lastOverlayTs != null) {
                (lastOverlayTs - firstOverlayTs).coerceAtLeast(0L)
            } else {
                0L
            }
            Log.i(
                TAG,
                "export_diagnostics_start rawSourceUri=$rawVideoUri rawDurationMs=${sourceMetadata.durationUs / 1_000L} " +
                    "rawWidth=${sourceMetadata.width} rawHeight=${sourceMetadata.height} sourceRotationDegrees=${sourceMetadata.rotationDegrees} " +
                    "renderRotationDegrees=${transform.renderRotationDegrees} finalMuxerOrientationHintDegrees=${transform.finalRotationMetadataDegrees} " +
                    "overlayFrameCount=${overlayFrames.size} firstOverlayTimestampMs=${firstOverlayTs ?: -1L} " +
                    "lastOverlayTimestampMs=${lastOverlayTs ?: -1L} overlayTimelineSpanMs=$overlaySpanMs " +
                    "outputWidth=${transform.outputWidth} outputHeight=${transform.outputHeight}",
            )

            encoder = try {
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, transform.outputWidth, transform.outputHeight).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                        setInteger(MediaFormat.KEY_BIT_RATE, computeBitrate(transform.outputWidth, transform.outputHeight, preset))
                        setInteger(MediaFormat.KEY_FRAME_RATE, preset.outputFps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    }
                    configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
            } catch (_: Throwable) {
                return@withContext fail(AnnotatedExportFailureReason.ENCODER_INIT_FAILED)
            }
            val encoderInstance = encoder ?: return@withContext fail(AnnotatedExportFailureReason.ENCODER_INIT_FAILED)
            val encoderInputSurface = try {
                encoderInstance.createInputSurface()
            } catch (_: Throwable) {
                return@withContext fail(AnnotatedExportFailureReason.INPUT_SURFACE_INIT_FAILED)
            }
            encoderInstance.start()

            glCompositor = try {
                GlSurfaceCompositor(sourceMetadata.width, sourceMetadata.height, transform, encoderInputSurface)
            } catch (e: CompositorInitException) {
                Log.e(TAG, "egl_gl_init_failure stage=${e.reason}", e)
                return@withContext fail(e.reason)
            } catch (_: Throwable) {
                return@withContext fail(AnnotatedExportFailureReason.EGL_INIT_FAILED)
            }
            val glCompositorInstance = glCompositor ?: return@withContext fail(AnnotatedExportFailureReason.EGL_INIT_FAILED)
            telemetry.compositorInitializedAtMs = System.currentTimeMillis()
            onTelemetry(telemetry)

            decoder = try {
                MediaCodec.createDecoderByType(mime).apply {
                    configure(inputFormat, glCompositorInstance.decoderSurface, null, 0)
                    start()
                }
            } catch (_: Throwable) {
                return@withContext fail(AnnotatedExportFailureReason.DECODER_INIT_FAILED)
            }
            val decoderInstance = decoder ?: return@withContext fail(AnnotatedExportFailureReason.DECODER_INIT_FAILED)
            telemetry.decoderInitializedAtMs = System.currentTimeMillis()
            exportStarted = true
            onTelemetry(telemetry)

            muxer = try {
                MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } catch (_: Throwable) {
                return@withContext fail(AnnotatedExportFailureReason.MUXER_INIT_FAILED)
            }
            val muxerInstance = muxer ?: return@withContext fail(AnnotatedExportFailureReason.MUXER_INIT_FAILED)
            runCatching { muxerInstance.setOrientationHint(transform.finalRotationMetadataDegrees) }

            val resolver = OverlayTimelineResolver(overlayFrames)
            val decoderInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            var muxTrack = -1
            var inputDone = false
            var decodeDone = false
            var encodedDone = false
            val firstFrameDeadline = System.currentTimeMillis() + FIRST_FRAME_DECODE_TIMEOUT_MS

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) encoderInstance.signalEndOfInputStream()
                while (true) {
                    val outIndex = encoderInstance.dequeueOutputBuffer(encoderInfo, 10_000)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxStarted) return
                            muxTrack = muxerInstance.addTrack(encoderInstance.outputFormat)
                            muxerInstance.start()
                            muxStarted = true
                        }
                        outIndex >= 0 -> {
                            if (encoderInfo.size > 0 && muxStarted) {
                                val data = encoderInstance.getOutputBuffer(outIndex)
                                if (data != null) {
                                    data.position(encoderInfo.offset)
                                    data.limit(encoderInfo.offset + encoderInfo.size)
                                    muxerInstance.writeSampleData(muxTrack, data, encoderInfo)
                                    telemetry.muxedFrameCount += 1
                                    telemetry.outputBytesWritten = output.length()
                                    telemetry.encodedFrameCount += 1
                                    if (telemetry.firstFrameEncodedAtMs == null) telemetry.firstFrameEncodedAtMs = System.currentTimeMillis()
                                }
                            }
                            if ((encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encodedDone = true
                            }
                            encoderInstance.releaseOutputBuffer(outIndex, false)
                            onTelemetry(telemetry)
                        }
                    }
                    if (encodedDone) return
                }
            }

            while (!encodedDone) {
                if (!inputDone) {
                    val inputIndex = decoderInstance.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoderInstance.getInputBuffer(inputIndex) ?: continue
                        val sampleSize = extractorInstance.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoderInstance.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoderInstance.queueInputBuffer(inputIndex, 0, sampleSize, extractorInstance.sampleTime, 0)
                            extractorInstance.advance()
                        }
                    }
                }

                if (!decodeDone) {
                    val outputIndex = decoderInstance.dequeueOutputBuffer(decoderInfo, 10_000)
                    when {
                        outputIndex >= 0 -> {
                            val render = decoderInfo.size > 0
                            decoderInstance.releaseOutputBuffer(outputIndex, render)
                            if (render) {
                                firstDecodeObserved = true
                                telemetry.decodedFrameCount += 1
                                if (telemetry.firstFrameDecodedAtMs == null) telemetry.firstFrameDecodedAtMs = System.currentTimeMillis()
                                val presentationTimeMs = decoderInfo.presentationTimeUs / 1000L
                                val overlay = resolver.overlayAt(presentationTimeMs)
                                if (overlay != null) telemetry.overlayFramesConsumed += 1
                                val instruction = buildRenderInstruction(overlay, drillType, drillCameraSide)
                                val result = glCompositorInstance.renderFrame(decoderInfo.presentationTimeUs, instruction, FRAME_SYNC_TIMEOUT_MS)
                                telemetry.frameAvailableWaitMs += result.frameWaitMs
                                telemetry.compositorRenderMs += result.renderMs
                                if (!result.rendered) {
                                    telemetry.droppedFrameCount += 1
                                } else {
                                    if (telemetry.firstFrameSubmittedToEncoderAtMs == null) {
                                        telemetry.firstFrameSubmittedToEncoderAtMs = System.currentTimeMillis()
                                    }
                                    telemetry.renderedFrameCount += 1
                                    if (telemetry.firstFrameRenderedAtMs == null) telemetry.firstFrameRenderedAtMs = System.currentTimeMillis()
                                    onProgress(telemetry.renderedFrameCount, estimatedTotalFrames)
                                }
                                onTelemetry(telemetry)
                                drainEncoder(endOfStream = false)
                            }
                            if ((decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                decodeDone = true
                                drainEncoder(endOfStream = true)
                            }
                        }
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    }
                }

                if (!firstDecodeObserved && System.currentTimeMillis() > firstFrameDeadline) {
                    return@withContext fail(AnnotatedExportFailureReason.FIRST_FRAME_DECODE_TIMEOUT)
                }
            }

            try {
                val muxStart = System.currentTimeMillis()
                if (muxStarted && !muxStopped) {
                    muxerInstance.stop()
                    muxStopped = true
                }
                telemetry.muxElapsedMs = System.currentTimeMillis() - muxStart
                telemetry.muxFinalizeCompleted = true
            } catch (_: Throwable) {
                return@withContext fail(AnnotatedExportFailureReason.MUX_FINALIZE_FAILED)
            }

            telemetry.exportCompletedAtMs = System.currentTimeMillis()
            onTelemetry(telemetry)

            val outputUri = Uri.fromFile(output).toString()
            val outputMetadata = readOutputVideoMetadata(outputUri)
            val verification = verifyExportedVideo(
                sourceDurationMs = sourceMetadata.durationUs / 1_000L,
                output = outputMetadata,
                toleranceMs = EXPORT_DURATION_TOLERANCE_MS,
                expectedWidth = transform.outputWidth,
                expectedHeight = transform.outputHeight,
                expectedRotationDegrees = 0,
            )
            Log.i(
                TAG,
                "export_diagnostics_finish rawSourceUri=$rawVideoUri exportStartTimestampMs=${telemetry.exportStartedAtMs} " +
                    "exportEndTimestampMs=${telemetry.exportCompletedAtMs ?: -1L} verifiedOutputDurationMs=${outputMetadata?.durationMs ?: -1L} " +
                    "verifiedOutputWidth=${outputMetadata?.width ?: -1} verifiedOutputHeight=${outputMetadata?.height ?: -1} " +
                    "verifiedOutputRotationDegrees=${outputMetadata?.rotationDegrees ?: -1} verificationPassed=${verification.passed} " +
                    "verificationDetail=${verification.failureDetail.orEmpty()}",
            )
            if (!verification.passed || output.length() <= 0L) {
                Log.w(TAG, "export_verification_failed detail=${verification.failureDetail.orEmpty()}")
                return@withContext fail(AnnotatedExportFailureReason.VERIFICATION_FAILED)
            }
            Log.i(
                TAG,
                "export_diagnostics_complete outputWidth=${outputMetadata?.width ?: -1} " +
                    "outputHeight=${outputMetadata?.height ?: -1} outputRotationDegrees=${outputMetadata?.rotationDegrees ?: -1}",
            )
            if (debugValidation) {
                Log.d(TAG, "debug_validation overlay_present=${verifyAnnotatedDifference(rawVideoUri, outputUri)}")
            }
            outputUri
        } catch (t: Throwable) {
            Log.e(TAG, "export_failure", t)
            fail(if (exportStarted) AnnotatedExportFailureReason.EXPORT_FAILED_AFTER_START else AnnotatedExportFailureReason.UNKNOWN)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { glCompositor?.release() }
            if (muxStarted && !muxStopped) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor?.release() }
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
            mirrored = overlay.mirrorMode,
            scaleMode = overlay.scaleMode,
            unreliableJointNames = overlay.unreliableJointNames,
        )
    }

    private fun readSourceVideoMetadata(
        inputFormat: MediaFormat,
        rawUri: Uri,
    ): SourceVideoMetadata {
        val width = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val formatDurationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L
        val formatRotation = if (inputFormat.containsKey(MediaFormat.KEY_ROTATION)) inputFormat.getInteger(MediaFormat.KEY_ROTATION) else 0
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, rawUri)
            val retrieverDurationUs =
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1_000L
            val retrieverRotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val resolvedDurationUs = maxOf(formatDurationUs, retrieverDurationUs)
            val resolvedRotation = if (retrieverRotation != 0) retrieverRotation else formatRotation
            SourceVideoMetadata(
                durationUs = resolvedDurationUs,
                width = width,
                height = height,
                rotationDegrees = normalizedRotationDegrees(resolvedRotation),
            )
        } catch (_: Throwable) {
            SourceVideoMetadata(
                durationUs = formatDurationUs,
                width = width,
                height = height,
                rotationDegrees = normalizedRotationDegrees(formatRotation),
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readOutputVideoMetadata(outputUri: String): OutputVideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(outputUri))
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            OutputVideoMetadata(
                durationMs = durationMs,
                width = width,
                height = height,
                rotationDegrees = rotationDegrees,
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
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
        } catch (_: Throwable) {
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
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { rawRetriever.release() }
            runCatching { outRetriever.release() }
        }
    }

    private fun computeBitrate(width: Int, height: Int, preset: ExportPreset): Int = when (preset) {
        ExportPreset.FAST -> (width * height * 4).coerceAtLeast(1_000_000)
        ExportPreset.BALANCED -> (width * height * 6).coerceAtLeast(1_500_000)
        ExportPreset.HIGH -> (width * height * 8).coerceAtLeast(2_500_000)
    }

    private data class RenderInstruction(
        val model: OverlayRenderModel? = null,
        val drawSkeleton: Boolean = false,
        val drawIdealLine: Boolean = false,
        val mirrored: Boolean = false,
        val scaleMode: PoseScaleMode = PoseScaleMode.FIT,
        val unreliableJointNames: Set<String> = emptySet(),
    )

    private class CompositorInitException(val reason: AnnotatedExportFailureReason, message: String) : IllegalStateException(message)

    private data class RenderSubmissionResult(
        val rendered: Boolean,
        val frameWaitMs: Long,
        val renderMs: Long,
    )

    private class GlSurfaceCompositor(
        sourceWidth: Int,
        sourceHeight: Int,
        private val transform: ExportTransform,
        encoderSurface: Surface,
    ) {
        private val width: Int = transform.outputWidth
        private val height: Int = transform.outputHeight
        private val eglDisplay: EGLDisplay
        private val eglContext: EGLContext
        private val eglSurface: EGLSurface
        private val frameSync = Object()
        @Volatile private var frameAvailable: Boolean = false
        private val overlayQuad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
        }
        private val videoQuad: FloatBuffer
        private val videoTex: FloatBuffer = createBaseTextureCoordinateBuffer()
        private val overlayTex: FloatBuffer = createOverlayTextureCoordinateBuffer()

        val decoderSurface: Surface
        private val decoderTextureId: Int
        private val surfaceTexture: SurfaceTexture
        private val videoProgram: Int
        private val overlayProgram: Int
        private val overlayTextureId: Int
        private val overlayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private val overlayCanvas = Canvas(overlayBitmap)
        private lateinit var overlayProjectionFrame: OverlayDrawingFrame
        private val texMatrix = FloatArray(16)
        private var loggedTextureTransformDiagnostics = false

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw CompositorInitException(AnnotatedExportFailureReason.EGL_INIT_FAILED, "eglGetDisplay failed")
            }
            val versions = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) {
                throw CompositorInitException(AnnotatedExportFailureReason.EGL_INIT_FAILED, "eglInitialize failed")
            }
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0 || configs[0] == null) {
                throw CompositorInitException(AnnotatedExportFailureReason.EGL_INIT_FAILED, "eglChooseConfig failed")
            }
            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw CompositorInitException(AnnotatedExportFailureReason.EGL_INIT_FAILED, "eglCreateContext failed")
            }
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], encoderSurface, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw CompositorInitException(AnnotatedExportFailureReason.EGL_INIT_FAILED, "eglCreateWindowSurface failed")
            }
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw CompositorInitException(AnnotatedExportFailureReason.EGL_INIT_FAILED, "eglMakeCurrent failed")
            }

            decoderTextureId = createExternalTexture()
            surfaceTexture = SurfaceTexture(decoderTextureId)
            surfaceTexture.setDefaultBufferSize(sourceWidth, sourceHeight)
            surfaceTexture.setOnFrameAvailableListener {
                synchronized(frameSync) {
                    frameAvailable = true
                    frameSync.notifyAll()
                }
            }
            decoderSurface = Surface(surfaceTexture)
            videoProgram = createProgram(VIDEO_VERTEX_SHADER, VIDEO_FRAGMENT_SHADER)
            overlayProgram = createProgram(OVERLAY_VERTEX_SHADER, OVERLAY_FRAGMENT_SHADER)
            overlayTextureId = create2dTexture()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlayBitmap, 0)
            val displaySize = sourceDisplaySize(sourceWidth, sourceHeight, transform.renderRotationDegrees)
            val contentRect = calculateAspectFitRect(
                contentWidth = displaySize.first,
                contentHeight = displaySize.second,
                containerWidth = width,
                containerHeight = height,
            )
            videoQuad = createQuadForRect(contentRect, width, height)
            overlayProjectionFrame = OverlayDrawingFrame(
                drawSkeleton = false,
                drawIdealLine = false,
                sourceWidth = displaySize.first,
                sourceHeight = displaySize.second,
                sourceRotationDegrees = 0,
                mirrored = false,
                previewContentRect = androidx.compose.ui.geometry.Rect(
                    left = contentRect.left,
                    top = contentRect.top,
                    right = contentRect.right,
                    bottom = contentRect.bottom,
                ),
                scaleMode = PoseScaleMode.FIT,
                renderTarget = OverlayRenderTarget.ANNOTATED_EXPORT,
                styleScaleMultiplier = 0.9f,
            )
        }

        fun renderFrame(presentationTimeUs: Long, instruction: RenderInstruction, frameTimeoutMs: Long): RenderSubmissionResult {
            val waitStart = System.currentTimeMillis()
            val available = waitForFrame(frameTimeoutMs)
            val waitedMs = System.currentTimeMillis() - waitStart
            if (!available) return RenderSubmissionResult(false, waitedMs, 0L)

            val renderStart = System.currentTimeMillis()
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(texMatrix)
            if (!loggedTextureTransformDiagnostics) {
                val texMatrixRotation = inferTextureMatrixRotationDegrees(texMatrix)
                Log.i(
                    TAG,
                    "export_diagnostics_texture_transform sourceRotationDegrees=${transform.sourceMetadataRotationDegrees} " +
                        "renderRotationDegrees=${transform.renderRotationDegrees} texMatrixRotationDegrees=$texMatrixRotation",
                )
                loggedTextureTransformDiagnostics = true
            }

            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            drawVideo()

            overlayCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            drawOverlay(instruction)
            GLES20.glUseProgram(overlayProgram)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, overlayBitmap)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            drawQuad(overlayProgram, GLES20.GL_TEXTURE_2D, overlayTextureId, overlayTex)
            GLES20.glDisable(GLES20.GL_BLEND)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeUs * 1000L)
            val swapped = EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            val renderMs = System.currentTimeMillis() - renderStart
            return RenderSubmissionResult(swapped, waitedMs, renderMs)
        }

        private fun waitForFrame(timeoutMs: Long): Boolean {
            synchronized(frameSync) {
                if (!frameAvailable) {
                    try {
                        frameSync.wait(timeoutMs)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
                val available = frameAvailable
                frameAvailable = false
                return available
            }
        }

        private fun drawVideo() {
            GLES20.glUseProgram(videoProgram)
            val matrixLoc = GLES20.glGetUniformLocation(videoProgram, "uTexMatrix")
            GLES20.glUniformMatrix4fv(matrixLoc, 1, false, texMatrix, 0)
            drawQuad(videoProgram, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, decoderTextureId, videoTex, videoQuad)
        }

        private fun inferTextureMatrixRotationDegrees(matrix: FloatArray): Int {
            val xAxisX = matrix[0]
            val xAxisY = matrix[1]
            val angleDegrees = Math.toDegrees(kotlin.math.atan2(xAxisY.toDouble(), xAxisX.toDouble())).toInt()
            val normalized = ((angleDegrees % 360) + 360) % 360
            val snapped = (normalized / 90f).roundToInt() * 90
            return normalizedRotationDegrees(snapped)
        }

        private fun createBaseTextureCoordinateBuffer(): FloatBuffer {
            val coordinates = floatArrayOf(
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 1f,
            )
            return ByteBuffer.allocateDirect(coordinates.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(coordinates)
                    position(0)
                }
        }

        private fun createOverlayTextureCoordinateBuffer(): FloatBuffer {
            val coordinates = floatArrayOf(
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f,
            )
            return ByteBuffer.allocateDirect(coordinates.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(coordinates)
                    position(0)
                }
        }

        private fun drawOverlay(instruction: RenderInstruction) {
            val model = instruction.model ?: return
            OverlayFrameRenderer.drawAndroid(
                canvas = overlayCanvas,
                width = width,
                height = height,
                model = model,
                frame = overlayProjectionFrame.copy(
                    drawSkeleton = instruction.drawSkeleton,
                    drawIdealLine = instruction.drawIdealLine,
                    // Export video frames are already normalized into upright output space.
                    // Keep overlay projection in this same export-upright coordinate system.
                    sourceRotationDegrees = 0,
                    mirrored = instruction.mirrored,
                    scaleMode = instruction.scaleMode,
                    renderTarget = OverlayRenderTarget.ANNOTATED_EXPORT,
                    unreliableJointNames = instruction.unreliableJointNames,
                ),
            )
        }

        private fun drawQuad(
            program: Int,
            textureTarget: Int,
            textureId: Int,
            textureCoordinates: FloatBuffer,
            vertexCoordinates: FloatBuffer = overlayQuad,
        ) {
            val aPos = GLES20.glGetAttribLocation(program, "aPosition")
            val aTex = GLES20.glGetAttribLocation(program, "aTexCoord")
            val uTex = GLES20.glGetUniformLocation(program, "uTexture")
            vertexCoordinates.position(0)
            textureCoordinates.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexCoordinates)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, textureCoordinates)
            GLES20.glEnableVertexAttribArray(aTex)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(textureTarget, textureId)
            GLES20.glUniform1i(uTex, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aTex)
        }

        fun release() {
            runCatching { decoderSurface.release() }
            runCatching { surfaceTexture.release() }
            runCatching { overlayBitmap.recycle() }
            runCatching {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            }
            runCatching { EGL14.eglDestroySurface(eglDisplay, eglSurface) }
            runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
            runCatching { EGL14.eglReleaseThread() }
            runCatching { EGL14.eglTerminate(eglDisplay) }
        }

        private fun createExternalTexture(): Int {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return textures[0]
        }

        private fun create2dTexture(): Int {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return textures[0]
        }

        private fun createProgram(vertex: String, fragment: String): Int {
            val v = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
            val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, v)
            GLES20.glAttachShader(program, f)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                throw CompositorInitException(AnnotatedExportFailureReason.GL_PROGRAM_LINK_FAILED, GLES20.glGetProgramInfoLog(program))
            }
            return program
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                throw CompositorInitException(AnnotatedExportFailureReason.GL_SHADER_COMPILE_FAILED, GLES20.glGetShaderInfoLog(shader))
            }
            return shader
        }

        private fun sourceDisplaySize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> {
            val normalized = normalizedRotationDegrees(rotationDegrees)
            return if (normalized == 90 || normalized == 270) height to width else width to height
        }

        private fun calculateAspectFitRect(
            contentWidth: Int,
            contentHeight: Int,
            containerWidth: Int,
            containerHeight: Int,
        ): RectF {
            if (contentWidth <= 0 || contentHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
                return RectF(0f, 0f, containerWidth.toFloat(), containerHeight.toFloat())
            }
            val scale = minOf(
                containerWidth.toFloat() / contentWidth.toFloat(),
                containerHeight.toFloat() / contentHeight.toFloat(),
            )
            val fittedWidth = contentWidth * scale
            val fittedHeight = contentHeight * scale
            val left = (containerWidth - fittedWidth) / 2f
            val top = (containerHeight - fittedHeight) / 2f
            return RectF(left, top, left + fittedWidth, top + fittedHeight)
        }

        private fun createQuadForRect(rect: RectF, containerWidth: Int, containerHeight: Int): FloatBuffer {
            fun toGlX(px: Float): Float = (px / containerWidth.toFloat()) * 2f - 1f
            fun toGlY(py: Float): Float = 1f - (py / containerHeight.toFloat()) * 2f
            val left = toGlX(rect.left)
            val right = toGlX(rect.right)
            val top = toGlY(rect.top)
            val bottom = toGlY(rect.bottom)
            val vertices = floatArrayOf(
                left, bottom,
                right, bottom,
                left, top,
                right, top,
            )
            return ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(vertices)
                    position(0)
                }
        }

        companion object {
            private const val VIDEO_VERTEX_SHADER =
                "attribute vec4 aPosition;\n" +
                    "attribute vec2 aTexCoord;\n" +
                    "uniform mat4 uTexMatrix;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = aPosition;\n" +
                    "  vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;\n" +
                    "}"

            private const val VIDEO_FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform samplerExternalOES uTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
                    "}"

            private const val OVERLAY_VERTEX_SHADER =
                "attribute vec4 aPosition;\n" +
                    "attribute vec2 aTexCoord;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = aPosition;\n" +
                    "  vTexCoord = aTexCoord;\n" +
                    "}"

            private const val OVERLAY_FRAGMENT_SHADER =
                "precision mediump float;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
                    "}"
        }
    }
}
