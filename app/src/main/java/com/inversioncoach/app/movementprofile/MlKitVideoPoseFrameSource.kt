package com.inversioncoach.app.movementprofile

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "UploadPoseFrameSource"
private const val MAX_ANALYSIS_DIMENSION = 720

class MlKitVideoPoseFrameSource(
    private val context: Context,
    private val sampleFps: Int = 6,
    workerCount: Int = defaultWorkerCount(),
    private val queueCapacity: Int = 6,
) : VideoPoseFrameSource {
    data class DecodeTelemetry(
        val sampledFrames: Int,
        val workerCount: Int,
        val maxQueueBacklog: Int,
        val averageWorkerActive: Double,
    )

    private val boundedWorkerCount = workerCount.coerceIn(1, 2)
    @Volatile
    var lastDecodeTelemetry: DecodeTelemetry = DecodeTelemetry(0, boundedWorkerCount, 0, 0.0)
        private set

    override fun decode(videoUri: Uri): Sequence<PoseFrame> = decode(videoUri, observer = AnalysisProgressObserver { })

    override fun decode(videoUri: Uri, observer: AnalysisProgressObserver): Sequence<PoseFrame> {
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
                val intervalMs = (1000f / sampleFps.coerceAtLeast(1)).toLong().coerceAtLeast(16L)
                val estimatedTotalFrames = (durationMs / intervalMs).toInt().coerceAtLeast(1) + 1
                Log.i(TAG, "decode_loop_start uri=$videoUri durationMs=$durationMs intervalMs=$intervalMs estimatedTotalFrames=$estimatedTotalFrames")
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
                val backlog = AtomicInteger(0)
                var maxBacklog = 0
                val activeWorkers = AtomicInteger(0)
                var activeSamples = 0L
                var activeTicks = 0

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
                                        val poseFrame = mapBitmapToPoseFrame(packet.bitmap, detector, packet.index, packet.timestampMs)
                                        resultQueue.send(packet.index to poseFrame)
                                        observer.onProgress(
                                            AnalysisProgressEvent(
                                                stage = "pose_detection_complete",
                                                processedFrames = packet.index + 1,
                                                estimatedTotalFrames = estimatedTotalFrames,
                                                timestampMs = packet.timestampMs,
                                                detail = "Pose detection completed",
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
                        var index = 0
                        while (timestampMs <= durationMs) {
                            try {
                                retriever.getFrameAtTime(timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)?.let { bitmap ->
                                    if (index % 2 == 0) {
                                        Log.i(TAG, "decode_sample frameIndex=$index timestampMs=$timestampMs/$durationMs")
                                    }
                                    frameQueue.send(FramePacket(index = index, timestampMs = timestampMs, bitmap = bitmap))
                                    maxBacklog = maxOf(maxBacklog, backlog.incrementAndGet())
                                    activeSamples += activeWorkers.get().toLong()
                                    activeTicks += 1
                                    observer.onProgress(
                                        AnalysisProgressEvent(
                                            stage = "frame_sampled",
                                            processedFrames = index + 1,
                                            estimatedTotalFrames = estimatedTotalFrames,
                                            timestampMs = timestampMs,
                                            detail = "Frame sampled for pose detection",
                                        ),
                                    )
                                    index += 1
                                } ?: run {
                                    Log.w(TAG, "decode_frame_missing timestampMs=$timestampMs index=$index")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "decode_exception frameIndex=$index timestampMs=$timestampMs message=${e.message}", e)
                                throw e
                            }
                            timestampMs += intervalMs
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
                lastDecodeTelemetry = DecodeTelemetry(
                    sampledFrames = frames.size,
                    workerCount = boundedWorkerCount,
                    maxQueueBacklog = maxBacklog,
                    averageWorkerActive = if (activeTicks == 0) 0.0 else activeSamples.toDouble() / activeTicks.toDouble(),
                )
                observer.onProgress(
                    AnalysisProgressEvent(
                        stage = "decode_complete",
                        processedFrames = frames.size,
                        estimatedTotalFrames = frames.size,
                        detail = "Decoded frames are ready for analysis",
                    ),
                )
                Log.i(
                    TAG,
                    "decode_pipeline_complete sampled=${frames.size} workers=$boundedWorkerCount maxBacklog=$maxBacklog avgActive=${"%.2f".format(lastDecodeTelemetry.averageWorkerActive)}",
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
            JointPoint(
                name = landmarkName(landmark.landmarkType),
                x = (landmark.position.x / analysisWidth).coerceIn(0f, 1f),
                y = (landmark.position.y / analysisHeight).coerceIn(0f, 1f),
                z = 0f,
                visibility = landmark.inFrameLikelihood,
            )
        }
        val confidence = if (landmarks.isEmpty()) 0f else landmarks.map { it.inFrameLikelihood }.average().toFloat()
        return PoseFrame(
            timestampMs = timestampMs,
            joints = joints,
            confidence = confidence,
            landmarksDetected = landmarks.size,
            inferenceTimeMs = 0L,
            droppedFrames = 0,
            rejectionReason = if (landmarks.isEmpty()) "no_person_detected" else "none",
        )
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

    private fun landmarkName(type: Int): String = when (type) {
        PoseLandmark.NOSE -> "nose"
        PoseLandmark.LEFT_EYE_INNER -> "left_eye_inner"
        PoseLandmark.LEFT_EYE -> "left_eye"
        PoseLandmark.LEFT_EYE_OUTER -> "left_eye_outer"
        PoseLandmark.RIGHT_EYE_INNER -> "right_eye_inner"
        PoseLandmark.RIGHT_EYE -> "right_eye"
        PoseLandmark.RIGHT_EYE_OUTER -> "right_eye_outer"
        PoseLandmark.LEFT_EAR -> "left_ear"
        PoseLandmark.RIGHT_EAR -> "right_ear"
        PoseLandmark.LEFT_MOUTH -> "mouth_left"
        PoseLandmark.RIGHT_MOUTH -> "mouth_right"
        PoseLandmark.LEFT_SHOULDER -> "left_shoulder"
        PoseLandmark.RIGHT_SHOULDER -> "right_shoulder"
        PoseLandmark.LEFT_ELBOW -> "left_elbow"
        PoseLandmark.RIGHT_ELBOW -> "right_elbow"
        PoseLandmark.LEFT_WRIST -> "left_wrist"
        PoseLandmark.RIGHT_WRIST -> "right_wrist"
        PoseLandmark.LEFT_PINKY -> "left_pinky"
        PoseLandmark.RIGHT_PINKY -> "right_pinky"
        PoseLandmark.LEFT_INDEX -> "left_index"
        PoseLandmark.RIGHT_INDEX -> "right_index"
        PoseLandmark.LEFT_THUMB -> "left_thumb"
        PoseLandmark.RIGHT_THUMB -> "right_thumb"
        PoseLandmark.LEFT_HIP -> "left_hip"
        PoseLandmark.RIGHT_HIP -> "right_hip"
        PoseLandmark.LEFT_KNEE -> "left_knee"
        PoseLandmark.RIGHT_KNEE -> "right_knee"
        PoseLandmark.LEFT_ANKLE -> "left_ankle"
        PoseLandmark.RIGHT_ANKLE -> "right_ankle"
        PoseLandmark.LEFT_HEEL -> "left_heel"
        PoseLandmark.RIGHT_HEEL -> "right_heel"
        PoseLandmark.LEFT_FOOT_INDEX -> "left_foot_index"
        PoseLandmark.RIGHT_FOOT_INDEX -> "right_foot_index"
        else -> "joint_$type"
    }

    companion object {
        private fun defaultWorkerCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            return (cores - 2).coerceIn(1, 2)
        }
    }
}
