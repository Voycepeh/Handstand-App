package com.inversioncoach.app.pose

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService

class PoseAnalyzer(
    context: Context,
    private val onPoseFrame: (PoseFrame) -> Unit,
    private val onAnalyzerWarning: (String) -> Unit,
    private val backgroundExecutor: ExecutorService,
) : ImageAnalysis.Analyzer {
    private companion object {
        private const val TAG = "PoseAnalyzer"
        private const val MIN_VISIBLE_JOINT_CONFIDENCE = 0.25f
        private const val LOG_INTERVAL_MS = 2_000L
        private const val MIN_PIPELINE_CONFIDENCE = 0.45f
    }

    private val landmarkNames = listOf(
        "nose", "left_eye_inner", "left_eye", "left_eye_outer", "right_eye_inner", "right_eye", "right_eye_outer",
        "left_ear", "right_ear", "mouth_left", "mouth_right", "left_shoulder", "right_shoulder", "left_elbow",
        "right_elbow", "left_wrist", "right_wrist", "left_pinky", "right_pinky", "left_index", "right_index",
        "left_thumb", "right_thumb", "left_hip", "right_hip", "left_knee", "right_knee", "left_ankle", "right_ankle",
        "left_heel", "right_heel", "left_foot_index", "right_foot_index",
    )

    private var lastWarningAtMs = 0L
    private var lastPerfLogAtMs = 0L
    private var lastFrameTimestampMs = 0L
    private val closeLock = Any()
    private val pendingFrames = linkedMapOf<Long, ImageProxy>()
    @Volatile
    private var frameInFlight = false

    @Suppress("unused")
    private val poseLandmarker: PoseLandmarker by lazy {
        val baseOptions = BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(::onResult)
            .setErrorListener { error, _ ->
                Log.e(TAG, "Pose model error", error)
                closePendingFrames()
                onAnalyzerWarning("Pose model error. Reposition camera and retry.")
            }
            .build()
        PoseLandmarker.createFromOptions(context, options)
    }

    private fun onResult(result: PoseLandmarkerResult, inputImage: com.google.mediapipe.framework.image.MPImage) {
        releaseFrame(result.timestampMs())
        val landmarks = result.landmarks().firstOrNull().orEmpty()
        val joints = landmarks.mapIndexedNotNull { index, lm ->
            val visibility = lm.visibility().orElse(0f)
            if (visibility < MIN_VISIBLE_JOINT_CONFIDENCE) return@mapIndexedNotNull null
            val jointName = landmarkNames.getOrElse(index) { "joint_$index" }
            JointPoint(jointName, lm.x(), lm.y(), lm.z(), visibility)
        }.toMutableList()

        val shoulder = joints.firstOrNull { it.name == "left_shoulder" }
        val hip = joints.firstOrNull { it.name == "left_hip" }
        if (shoulder != null && hip != null) {
            joints += JointPoint(
                name = "left_rib_proxy",
                x = (shoulder.x + hip.x) / 2f,
                y = (shoulder.y + hip.y) / 2f,
                z = (shoulder.z + hip.z) / 2f,
                visibility = minOf(shoulder.visibility, hip.visibility),
            )
        }

        val confidence = if (landmarks.isEmpty()) 0f else landmarks.map { it.visibility().orElse(0f) }.average().toFloat()
        val timestampMs = result.timestampMs()
        backgroundExecutor.execute {
            onPoseFrame(PoseFrame(timestampMs = timestampMs, joints = joints, confidence = confidence))
        }

        if (confidence < MIN_PIPELINE_CONFIDENCE) {
            throttleWarning("Low landmark confidence. Improve lighting and keep full body in side view.")
        }

        val now = System.currentTimeMillis()
        if (now - lastPerfLogAtMs >= LOG_INTERVAL_MS) {
            lastPerfLogAtMs = now
            Log.d(TAG, "Pose stream active: landmarks=${joints.size}, confidence=${"%.2f".format(confidence)}")
        }
    }

    override fun analyze(image: ImageProxy) {
        if (frameInFlight) {
            image.close()
            return
        }

        try {
            val mediaImage = image.image ?: run {
                image.close()
                return
            }
            val frameTimestampMs = nextTimestampMs(image.imageInfo.timestamp)
            val mpImage = MediaImageBuilder(mediaImage).build()
            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(image.imageInfo.rotationDegrees)
                .build()

            synchronized(closeLock) {
                frameInFlight = true
                pendingFrames[frameTimestampMs] = image
            }
            poseLandmarker.detectAsync(mpImage, imageProcessingOptions, frameTimestampMs)
        } catch (t: Throwable) {
            Log.e(TAG, "Pose analysis frame failed", t)
            releaseFrameForImage(image)
            throttleWarning("Pose inference dropped a frame. Hold steady and retry.")
        }
    }

    private fun nextTimestampMs(timestampNs: Long): Long {
        val convertedMs = TimeUnit.NANOSECONDS.toMillis(timestampNs)
        return synchronized(closeLock) {
            val timestampMs = if (convertedMs <= lastFrameTimestampMs) lastFrameTimestampMs + 1 else convertedMs
            lastFrameTimestampMs = timestampMs
            timestampMs
        }
    }

    private fun releaseFrame(frameTimestampMs: Long) {
        val toClose = synchronized(closeLock) {
            val frame = pendingFrames.remove(frameTimestampMs)
            frameInFlight = pendingFrames.isNotEmpty()
            frame
        }
        toClose?.close()
    }

    private fun releaseFrameForImage(image: ImageProxy) {
        synchronized(closeLock) {
            val key = pendingFrames.entries.firstOrNull { it.value === image }?.key
            if (key != null) pendingFrames.remove(key)
            frameInFlight = pendingFrames.isNotEmpty()
        }
        image.close()
    }

    private fun closePendingFrames() {
        val frames = synchronized(closeLock) {
            val snapshot = pendingFrames.values.toList()
            pendingFrames.clear()
            frameInFlight = false
            snapshot
        }
        frames.forEach { it.close() }
    }

    private fun throttleWarning(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastWarningAtMs > 3000) {
            lastWarningAtMs = now
            onAnalyzerWarning(message)
        }
    }
}
