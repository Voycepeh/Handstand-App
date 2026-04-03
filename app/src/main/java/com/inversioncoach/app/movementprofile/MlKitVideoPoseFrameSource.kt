package com.inversioncoach.app.movementprofile

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.pose.model.JointLandmark
import com.inversioncoach.app.pose.model.JointType
import com.inversioncoach.app.pose.model.MlKitPoseMapper
import com.inversioncoach.app.pose.model.PoseFrame as InternalPoseFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong
import kotlin.system.measureNanoTime

private const val TAG = "UploadPoseFrameSource"
private const val MAX_ANALYSIS_DIMENSION = 720

class MlKitVideoPoseFrameSource(
    private val context: Context,
    private val sampleFps: Int = 6,
    private val adaptiveConfig: AdaptiveSamplingConfig = AdaptiveSamplingConfig(legacyFixedFps = sampleFps),
    workerCount: Int = defaultWorkerCount(),
    private val queueCapacity: Int = 6,
) : VideoPoseFrameSource, UploadSamplingTelemetryProvider {
    data class DecodeTelemetry(
        val candidateFrames: Int,
        val sampledFrames: Int,
        val inferredFrames: Int,
        val workerCount: Int,
        val maxQueueBacklog: Int,
        val averageWorkerActive: Double,
        val averageDecodeMs: Double,
        val maxDecodeMs: Long,
        val averageInferenceMs: Double,
        val maxInferenceMs: Long,
        val modeCounts: Map<SamplingMode, Int>,
        val burstTriggerCounts: Map<BurstTriggerReason, Int>,
        val averageSelectedIntervalMs: Long,
        val maxSelectedIntervalMs: Long,
    )

    private val boundedWorkerCount = workerCount.coerceIn(1, 2)
    @Volatile
    var lastDecodeTelemetry: DecodeTelemetry = DecodeTelemetry(
        candidateFrames = 0,
        sampledFrames = 0,
        inferredFrames = 0,
        workerCount = boundedWorkerCount,
        maxQueueBacklog = 0,
        averageWorkerActive = 0.0,
        averageDecodeMs = 0.0,
        maxDecodeMs = 0L,
        averageInferenceMs = 0.0,
        maxInferenceMs = 0L,
        modeCounts = emptyMap(),
        burstTriggerCounts = emptyMap(),
        averageSelectedIntervalMs = 0L,
        maxSelectedIntervalMs = 0L,
    )
        private set

    private val poseMapper = MlKitPoseMapper()

    override fun samplingTelemetry(): Map<String, Long> = mapOf(
        "adaptive_candidate_frames" to lastDecodeTelemetry.candidateFrames.toLong(),
        "adaptive_sampled_frames" to lastDecodeTelemetry.sampledFrames.toLong(),
        "adaptive_inferred_frames" to lastDecodeTelemetry.inferredFrames.toLong(),
        "adaptive_mode_sparse" to (lastDecodeTelemetry.modeCounts[SamplingMode.SPARSE] ?: 0).toLong(),
        "adaptive_mode_burst" to (lastDecodeTelemetry.modeCounts[SamplingMode.BURST] ?: 0).toLong(),
        "adaptive_mode_hold_steady" to (lastDecodeTelemetry.modeCounts[SamplingMode.HOLD_STEADY] ?: 0).toLong(),
        "adaptive_mode_recovery" to (lastDecodeTelemetry.modeCounts[SamplingMode.RECOVERY] ?: 0).toLong(),
        "adaptive_mode_legacy" to (lastDecodeTelemetry.modeCounts[SamplingMode.LEGACY_FIXED] ?: 0).toLong(),
        "adaptive_trigger_visual_diff" to (lastDecodeTelemetry.burstTriggerCounts[BurstTriggerReason.VISUAL_DIFF] ?: 0).toLong(),
        "adaptive_trigger_subject_move" to (lastDecodeTelemetry.burstTriggerCounts[BurstTriggerReason.SUBJECT_MOVEMENT] ?: 0).toLong(),
        "adaptive_trigger_joint_move" to (lastDecodeTelemetry.burstTriggerCounts[BurstTriggerReason.JOINT_MOVEMENT] ?: 0).toLong(),
        "adaptive_trigger_conf_drop" to (lastDecodeTelemetry.burstTriggerCounts[BurstTriggerReason.CONFIDENCE_DROP] ?: 0).toLong(),
        "adaptive_average_interval_ms" to lastDecodeTelemetry.averageSelectedIntervalMs,
        "adaptive_max_interval_ms" to lastDecodeTelemetry.maxSelectedIntervalMs,
    )

    override fun decode(videoUri: Uri): Sequence<PoseFrame> = decode(videoUri, observer = AnalysisProgressObserver { }, request = VideoDecodeRequest(movementType = null))
    override fun decode(videoUri: Uri, request: VideoDecodeRequest): Sequence<PoseFrame> =
        decode(videoUri, observer = AnalysisProgressObserver { }, request = request)

    override fun decode(videoUri: Uri, observer: AnalysisProgressObserver): Sequence<PoseFrame> =
        decode(videoUri, observer, request = VideoDecodeRequest(movementType = null))

    override fun decode(videoUri: Uri, observer: AnalysisProgressObserver, request: VideoDecodeRequest): Sequence<PoseFrame> {
        val frames = mutableListOf<PoseFrame>()
        runBlocking(Dispatchers.Default) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (durationMs <= 0L) {
                    Log.w(TAG, "decode_failed reason=missing_duration uri=$videoUri")
                    observer.onProgress(AnalysisProgressEvent(stage = "decode_failed", detail = "missing_duration"))
                    return@runBlocking
                }
                val activeAdaptiveConfig = request.adaptiveConfig.copy(legacyFixedFps = sampleFps)
                val candidateIntervalMs = (1000f / if (activeAdaptiveConfig.enabled) {
                    activeAdaptiveConfig.candidateDecodeFps.coerceAtLeast(1)
                } else {
                    sampleFps.coerceAtLeast(1)
                }).toLong().coerceAtLeast(16L)
                val estimatedTotalFrames = (durationMs / candidateIntervalMs).toInt().coerceAtLeast(1) + 1
                val planner = UploadedVideoSamplingPlanner(
                    config = activeAdaptiveConfig,
                    movementType = request.movementType,
                )
                Log.i(TAG, "decode_loop_start uri=$videoUri durationMs=$durationMs intervalMs=$candidateIntervalMs estimatedTotalFrames=$estimatedTotalFrames adaptive=${activeAdaptiveConfig.enabled} movementType=${request.movementType}")
                observer.onProgress(
                    AnalysisProgressEvent(
                        stage = "decode_start",
                        processedFrames = 0,
                        estimatedTotalFrames = estimatedTotalFrames,
                        detail = "Decode pipeline started",
                    ),
                )
                data class FramePacket(val index: Int, val timestampMs: Long, val bitmap: Bitmap)
                val frameQueue = Channel<FramePacket>(capacity = queueCapacity.coerceAtLeast(2))
                val resultQueue = Channel<Pair<Int, PoseFrame>>(capacity = queueCapacity.coerceAtLeast(2))
                val orderedFrames = mutableMapOf<Int, PoseFrame>()
                val modeCounts = mutableMapOf<SamplingMode, Int>()
                val triggerCounts = mutableMapOf<BurstTriggerReason, Int>()
                var selectedIntervals = mutableListOf<Long>()
                var maxSelectedInterval = 0L
                var lastSelectedTs = -1L
                val backlog = AtomicInteger(0)
                var maxBacklog = 0
                val activeWorkers = AtomicInteger(0)
                var activeSamples = 0L
                var activeTicks = 0
                val totalDecodeNanos = AtomicLong(0L)
                val maxDecodeNanos = AtomicLong(0L)
                val totalInferenceNanos = AtomicLong(0L)
                val maxInferenceNanos = AtomicLong(0L)

                var previousLumaSignal: Double? = null
                var candidateFramesDecoded = 0

                coroutineScope {
                    val workers = (0 until boundedWorkerCount).map {
                        launch(Dispatchers.Default) {
                            val detector = PoseDetection.getClient(
                                PoseDetectorOptions.Builder()
                                    .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                                    .build(),
                            )
                            try {
                                for (packet in frameQueue) {
                                    activeWorkers.incrementAndGet()
                                    try {
                                        observer.onProgress(
                                            AnalysisProgressEvent(
                                                stage = "pose_detection_running",
                                                processedFrames = packet.index,
                                                estimatedTotalFrames = estimatedTotalFrames,
                                                timestampMs = packet.timestampMs,
                                                detail = "Pose detection started",
                                            ),
                                        )
                                        var poseFrame: PoseFrame
                                        val inferenceNanos = measureNanoTime {
                                            poseFrame = mapBitmapToPoseFrame(packet.bitmap, detector, packet.index, packet.timestampMs)
                                        }
                                        poseFrame = poseFrame.copy(inferenceTimeMs = (inferenceNanos / 1_000_000.0).roundToLong())
                                        totalInferenceNanos.addAndGet(inferenceNanos)
                                        maxInferenceNanos.accumulateAndGet(inferenceNanos) { current, candidate ->
                                            maxOf(current, candidate)
                                        }
                                        resultQueue.send(packet.index to poseFrame)
                                        observer.onProgress(
                                            AnalysisProgressEvent(
                                                stage = "pose_detection_complete",
                                                processedFrames = packet.index + 1,
                                                estimatedTotalFrames = estimatedTotalFrames,
                                                timestampMs = packet.timestampMs,
                                                detail = "Pose detection completed (inferenceMs=${poseFrame.inferenceTimeMs})",
                                            ),
                                        )
                                    } finally {
                                        packet.bitmap.recycle()
                                        activeWorkers.decrementAndGet()
                                        backlog.decrementAndGet()
                                    }
                                }
                            } finally {
                                runCatching { detector.close() }
                            }
                        }
                    }

                    val producer = launch(Dispatchers.IO) {
                        var timestampMs = 0L
                        var selectedIndex = 0
                        var candidateIndex = 0
                        var lastTimestampMs = -1L
                        while (timestampMs <= durationMs) {
                            try {
                                if (lastTimestampMs >= 0L && timestampMs <= lastTimestampMs) {
                                    timestampMs = lastTimestampMs + candidateIntervalMs
                                }
                                val frameTimeUs = timestampMs * 1000L
                                var bitmap: Bitmap? = null
                                val decodeNanos = measureNanoTime {
                                    bitmap = retriever.getFrameAtTime(
                                        frameTimeUs,
                                        MediaMetadataRetriever.OPTION_CLOSEST,
                                    )
                                }
                                totalDecodeNanos.addAndGet(decodeNanos)
                                maxDecodeNanos.accumulateAndGet(decodeNanos) { current, candidate ->
                                    maxOf(current, candidate)
                                }
                                val decodeMs = (decodeNanos / 1_000_000.0).roundToLong()
                                bitmap?.let { nonNullBitmap ->
                                    val lumaSignal = bitmapLumaSignal(nonNullBitmap)
                                    val signalReliable = lumaSignal != null && previousLumaSignal != null
                                    val visualDiff = if (lumaSignal != null && previousLumaSignal != null) {
                                        kotlin.math.abs(lumaSignal - previousLumaSignal!!)
                                    } else {
                                        0.0
                                    }
                                    val decision = planner.decide(
                                        AdaptiveSamplingSignal(
                                            timestampMs = timestampMs,
                                            videoDurationMs = durationMs,
                                            visualDiff = visualDiff,
                                            subjectMovement = 0.0,
                                            jointMovement = 0.0,
                                            confidenceDrop = 0.0,
                                            signalReliable = signalReliable,
                                        ),
                                    )
                                    modeCounts[decision.mode] = (modeCounts[decision.mode] ?: 0) + 1
                                    decision.reasons.forEach { reason ->
                                        triggerCounts[reason] = (triggerCounts[reason] ?: 0) + 1
                                    }
                                    if (candidateIndex % 2 == 0) {
                                        Log.i(
                                            TAG,
                                            "decode_sample frameIndex=$candidateIndex timestampMs=$timestampMs/$durationMs decodeMs=$decodeMs mode=${decision.mode} sample=${decision.sample} motionScore=${"%.3f".format(decision.motionScore)} reasons=${decision.reasons.joinToString(separator = ",")}",
                                        )
                                    }
                                    if (decision.sample) {
                                        frameQueue.send(FramePacket(index = selectedIndex, timestampMs = timestampMs, bitmap = nonNullBitmap))
                                        maxBacklog = maxOf(maxBacklog, backlog.incrementAndGet())
                                        activeSamples += activeWorkers.get().toLong()
                                        activeTicks += 1
                                        if (lastSelectedTs >= 0L) {
                                            val gap = timestampMs - lastSelectedTs
                                            selectedIntervals += gap
                                            maxSelectedInterval = maxOf(maxSelectedInterval, gap)
                                        }
                                        lastSelectedTs = timestampMs
                                        observer.onProgress(
                                            AnalysisProgressEvent(
                                                stage = "frame_sampled",
                                                processedFrames = candidateIndex + 1,
                                                estimatedTotalFrames = estimatedTotalFrames,
                                                timestampMs = timestampMs,
                                                detail = "Frame sampled for pose detection (decodeMs=$decodeMs mode=${decision.mode})",
                                            ),
                                        )
                                        selectedIndex += 1
                                    } else {
                                        nonNullBitmap.recycle()
                                        observer.onProgress(
                                            AnalysisProgressEvent(
                                                stage = "frame_skipped",
                                                processedFrames = candidateIndex + 1,
                                                estimatedTotalFrames = estimatedTotalFrames,
                                                timestampMs = timestampMs,
                                                detail = "Adaptive sampler skipped frame (mode=${decision.mode})",
                                            ),
                                        )
                                    }
                                    previousLumaSignal = lumaSignal ?: previousLumaSignal
                                } ?: run {
                                    Log.w(TAG, "decode_frame_missing timestampMs=$timestampMs index=$candidateIndex decodeMs=$decodeMs")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "decode_exception_continue frameIndex=$candidateIndex timestampMs=$timestampMs message=${e.message}", e)
                                observer.onProgress(
                                    AnalysisProgressEvent(
                                        stage = "decode_frame_error",
                                        processedFrames = candidateIndex + 1,
                                        estimatedTotalFrames = estimatedTotalFrames,
                                        timestampMs = timestampMs,
                                        detail = e.message ?: "frame_decode_failed",
                                    ),
                                )
                            }
                            candidateFramesDecoded += 1
                            candidateIndex += 1
                            lastTimestampMs = timestampMs
                            timestampMs += candidateIntervalMs
                        }
                        frameQueue.close()
                    }
                    val collector = launch(Dispatchers.Default) {
                        for ((index, frame) in resultQueue) {
                            orderedFrames[index] = frame
                        }
                    }

                    producer.join()
                    workers.forEach { it.join() }
                    resultQueue.close()
                    collector.join()
                }

                frames += orderedFrames.toSortedMap().values
                val avgDecodeMs = if (candidateFramesDecoded == 0) 0.0 else {
                    totalDecodeNanos.get().toDouble() / candidateFramesDecoded.toDouble() / 1_000_000.0
                }
                val avgInferenceMs = if (frames.isEmpty()) 0.0 else {
                    totalInferenceNanos.get().toDouble() / frames.size.toDouble() / 1_000_000.0
                }
                val averageSelectedInterval = if (selectedIntervals.isEmpty()) 0L else selectedIntervals.average().roundToLong()
                lastDecodeTelemetry = DecodeTelemetry(
                    candidateFrames = candidateFramesDecoded,
                    sampledFrames = frames.size,
                    inferredFrames = frames.size,
                    workerCount = boundedWorkerCount,
                    maxQueueBacklog = maxBacklog,
                    averageWorkerActive = if (activeTicks == 0) 0.0 else activeSamples.toDouble() / activeTicks.toDouble(),
                    averageDecodeMs = avgDecodeMs,
                    maxDecodeMs = (maxDecodeNanos.get() / 1_000_000.0).roundToLong(),
                    averageInferenceMs = avgInferenceMs,
                    maxInferenceMs = (maxInferenceNanos.get() / 1_000_000.0).roundToLong(),
                    modeCounts = modeCounts.toMap(),
                    burstTriggerCounts = triggerCounts.toMap(),
                    averageSelectedIntervalMs = averageSelectedInterval,
                    maxSelectedIntervalMs = maxSelectedInterval,
                )
                observer.onProgress(
                    AnalysisProgressEvent(
                        stage = "decode_complete",
                        processedFrames = frames.size,
                        estimatedTotalFrames = frames.size,
                        detail = if (frames.isEmpty()) "Decoded zero frames" else "Decoded frames are ready for analysis",
                    ),
                )
                if (frames.isEmpty()) {
                    observer.onProgress(
                        AnalysisProgressEvent(
                            stage = "decode_failed",
                            processedFrames = 0,
                            estimatedTotalFrames = estimatedTotalFrames.coerceAtLeast(1),
                            detail = "zero_decoded_frames",
                        ),
                    )
                }
                Log.i(
                    TAG,
                    "decode_pipeline_complete sampled=${frames.size} workers=$boundedWorkerCount maxBacklog=$maxBacklog avgActive=${"%.2f".format(lastDecodeTelemetry.averageWorkerActive)} avgDecodeMs=${"%.2f".format(lastDecodeTelemetry.averageDecodeMs)} maxDecodeMs=${lastDecodeTelemetry.maxDecodeMs} avgInferenceMs=${"%.2f".format(lastDecodeTelemetry.averageInferenceMs)} maxInferenceMs=${lastDecodeTelemetry.maxInferenceMs}",
                )
            } catch (error: Exception) {
                Log.e(TAG, "decode_pipeline_failed uri=$videoUri message=${error.message}", error)
                observer.onProgress(
                    AnalysisProgressEvent(
                        stage = "decode_failed",
                        processedFrames = 0,
                        estimatedTotalFrames = null,
                        detail = error.message ?: "decode_pipeline_failed",
                    ),
                )
            }
            finally {
                runCatching { retriever.release() }
            }
        }
        return frames.asSequence()
    }

    private fun mapBitmapToPoseFrame(
        bitmap: Bitmap,
        detector: com.google.mlkit.vision.pose.PoseDetector,
        frameIndex: Int,
        timestampMs: Long
    ): PoseFrame {
        val analysisBitmap = downscaleForAnalysis(bitmap)
        val analysisWidth = analysisBitmap.width.toFloat()
        val analysisHeight = analysisBitmap.height.toFloat()
        val pose = try {
            Tasks.await(detector.process(InputImage.fromBitmap(analysisBitmap, 0)))
        } catch (e: Exception) {
            Log.e(TAG, "pose_detection_failed frameIndex=$frameIndex timestampMs=$timestampMs message=${e.message}", e)
            throw e
        } finally {
            if (analysisBitmap !== bitmap) {
                analysisBitmap.recycle()
            }
        }
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) {
            Log.w(TAG, "pose_detection_empty timestampMs=$timestampMs")
        } else {
            Log.i(TAG, "pose_detection_success timestampMs=$timestampMs landmarks=${landmarks.size}")
        }
        val joints = landmarks.map { landmark ->
            JointLandmark(
                jointType = poseMapper.landmarkType(landmark.landmarkType),
                x = (landmark.position.x / analysisWidth).coerceIn(0f, 1f),
                y = (landmark.position.y / analysisHeight).coerceIn(0f, 1f),
                z = 0f,
                visibility = landmark.inFrameLikelihood,
            )
        }
        val confidence = if (landmarks.isEmpty()) 0f else landmarks.map { it.inFrameLikelihood }.average().toFloat()
        val internalFrame = InternalPoseFrame(
            timestampMs = timestampMs,
            joints = joints,
            confidence = confidence,
            landmarksDetected = landmarks.size,
            inferenceTimeMs = 0L,
            droppedFrames = 0,
            rejectionReason = if (landmarks.isEmpty()) "no_person_detected" else "none",
        )
        return poseMapper.toLegacy(internalFrame)
    }

    private fun downscaleForAnalysis(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDimension = maxOf(width, height)
        if (maxDimension <= MAX_ANALYSIS_DIMENSION) return bitmap

        val scale = MAX_ANALYSIS_DIMENSION.toFloat() / maxDimension.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun bitmapLumaSignal(bitmap: Bitmap): Double? {
        return runCatching {
            val targetWidth = 16
            val targetHeight = 16
            val sampleBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            try {
                val pixels = IntArray(targetWidth * targetHeight)
                sampleBitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                if (pixels.isEmpty()) return@runCatching null
                var total = 0.0
                pixels.forEach { pixel ->
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    total += (0.299 * r) + (0.587 * g) + (0.114 * b)
                }
                (total / pixels.size.toDouble()) / 255.0
            } finally {
                if (sampleBitmap !== bitmap) sampleBitmap.recycle()
            }
        }.getOrNull()
    }



    companion object {
        private fun defaultWorkerCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            return (cores - 2).coerceIn(1, 2)
        }
    }
}
