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

class MlKitVideoPoseFrameSource(
    private val context: Context,
    private val sampleFps: Int = 12,
    workerCount: Int = defaultWorkerCount(),
    private val queueCapacity: Int = 6,
) : VideoPoseFrameSource {
    data class DecodeTelemetry(
        val sampledFrames: Int,
        val workerCount: Int,
        val maxQueueBacklog: Int,
        val averageWorkerActive: Double,
    )

    private val boundedWorkerCount = workerCount.coerceIn(1, 4)
    @Volatile
    var lastDecodeTelemetry: DecodeTelemetry = DecodeTelemetry(0, boundedWorkerCount, 0, 0.0)
        private set

    override fun decode(videoUri: Uri): Sequence<PoseFrame> {
        val frames = mutableListOf<PoseFrame>()
        runBlocking(Dispatchers.Default) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
                if (durationMs <= 0L) {
                    Log.w(TAG, "decode_failed reason=missing_duration uri=$videoUri")
                    return@runBlocking
                }
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
                                        val poseFrame = mapBitmapToPoseFrame(packet.bitmap, detector, packet.timestampMs, width, height)
                                        resultQueue.send(packet.index to poseFrame)
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
                        val intervalMs = (1000f / sampleFps.coerceAtLeast(1)).toLong().coerceAtLeast(16L)
                        var timestampMs = 0L
                        var index = 0
                        while (timestampMs <= durationMs) {
                            retriever.getFrameAtTime(timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)?.let { bitmap ->
                                frameQueue.send(FramePacket(index = index, timestampMs = timestampMs, bitmap = bitmap))
                                maxBacklog = maxOf(maxBacklog, backlog.incrementAndGet())
                                activeSamples += activeWorkers.get().toLong()
                                activeTicks += 1
                                index += 1
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
        timestampMs: Long,
        width: Int,
        height: Int,
    ): PoseFrame {
        val pose = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
        val landmarks = pose.allPoseLandmarks
        val joints = landmarks.map { landmark ->
            JointPoint(
                name = landmarkName(landmark.landmarkType),
                x = (landmark.position.x / width.toFloat()).coerceIn(0f, 1f),
                y = (landmark.position.y / height.toFloat()).coerceIn(0f, 1f),
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
            return (cores - 2).coerceIn(1, 4)
        }
    }
}
