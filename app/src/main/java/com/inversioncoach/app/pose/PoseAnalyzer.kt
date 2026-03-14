package com.inversioncoach.app.pose

import android.util.Log
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import java.util.concurrent.ExecutorService

class PoseAnalyzer(
    private val onPoseFrame: (PoseFrame) -> Unit,
    private val onAnalyzerWarning: (String) -> Unit,
    private val backgroundExecutor: ExecutorService,
) : ImageAnalysis.Analyzer {

    private data class PendingFrame(
        val image: ImageProxy,
        val startedAtMs: Long,
        val imageWidth: Int,
        val imageHeight: Int,
    )

    private companion object {
        private const val TAG = "PoseAnalyzer"
        private const val MIN_VISIBLE_JOINT_CONFIDENCE = 0.25f
        private const val MIN_PIPELINE_CONFIDENCE = 0.35f
        private const val LOG_INTERVAL_MS = 2_000L
    }

    private val detector: PoseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build(),
    )

    private val lock = Any()
    @Volatile
    private var frameInFlight = false
    private var lastWarningAtMs = 0L
    private var lastPerfLogAtMs = 0L
    private var droppedFrames = 0L
    private var processedFrames = 0L
    private var failedFrames = 0L
    private var receivedFrames = 0L

    override fun analyze(image: ImageProxy) {
        receivedFrames += 1
        if (frameInFlight) {
            droppedFrames += 1
            image.close()
            Log.v(TAG, "frame_skipped inFlight=true")
            return
        }

        val mediaImage = image.image
        if (mediaImage == null) {
            droppedFrames += 1
            image.close()
            Log.w(TAG, "frame_skipped mediaImage=null")
            return
        }

        val pending = PendingFrame(
            image = image,
            startedAtMs = SystemClock.elapsedRealtime(),
            imageWidth = image.width,
            imageHeight = image.height,
        )
        synchronized(lock) {
            frameInFlight = true
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        Log.v(TAG, "inference_start rotation=${image.imageInfo.rotationDegrees}")
        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val inferenceMs = SystemClock.elapsedRealtime() - pending.startedAtMs
                handleSuccess(
                    pose = pose,
                    inferenceMs = inferenceMs,
                    imageWidth = pending.imageWidth,
                    imageHeight = pending.imageHeight,
                )
            }
            .addOnFailureListener { error ->
                failedFrames += 1
                Log.e(TAG, "inference_failure", error)
                throttleWarning("Frame processing failure. Hold steady and retry.")
            }
            .addOnCompleteListener {
                releaseFrame(pending)
            }
    }

    fun close() {
        detector.close()
    }

    private fun handleSuccess(
        pose: Pose,
        inferenceMs: Long,
        imageWidth: Int,
        imageHeight: Int,
    ) {
        val landmarks = pose.allPoseLandmarks
        val joints = landmarks.mapNotNull { landmark ->
            val visibility = landmark.inFrameLikelihood
            if (visibility < MIN_VISIBLE_JOINT_CONFIDENCE) return@mapNotNull null
            JointPoint(
                name = landmarkName(landmark.landmarkType),
                x = normalizeToUnit(landmark.position.x.toFloat(), imageWidth),
                y = normalizeToUnit(landmark.position.y.toFloat(), imageHeight),
                z = 0f,
                visibility = visibility,
            )
        }.toMutableList()

        val shoulder = joints.firstOrNull { it.name == "left_shoulder" }
        val hip = joints.firstOrNull { it.name == "left_hip" }
        if (shoulder != null && hip != null) {
            joints += JointPoint(
                name = "left_rib_proxy",
                x = (shoulder.x + hip.x) / 2f,
                y = (shoulder.y + hip.y) / 2f,
                z = 0f,
                visibility = minOf(shoulder.visibility, hip.visibility),
            )
        }

        val confidence = if (landmarks.isEmpty()) 0f else landmarks.map { it.inFrameLikelihood }.average().toFloat()
        val rejectionReason = when {
            landmarks.isEmpty() -> "no_person_detected"
            confidence < MIN_PIPELINE_CONFIDENCE -> "low_confidence"
            joints.size < 12 -> "body_not_fully_visible"
            else -> "none"
        }

        processedFrames += 1
        backgroundExecutor.execute {
            onPoseFrame(
                PoseFrame(
                    timestampMs = System.currentTimeMillis(),
                    joints = joints,
                    confidence = confidence,
                    landmarksDetected = landmarks.size,
                    inferenceTimeMs = inferenceMs,
                    droppedFrames = droppedFrames.toInt(),
                    rejectionReason = rejectionReason,
                ),
            )
        }

        if (confidence < MIN_PIPELINE_CONFIDENCE) {
            throttleWarning("Low confidence. Improve lighting and keep your full body visible in side view.")
        }

        val now = System.currentTimeMillis()
        if (now - lastPerfLogAtMs > LOG_INTERVAL_MS) {
            lastPerfLogAtMs = now
            Log.d(TAG, "inference_end landmarks=${landmarks.size} conf=${"%.2f".format(confidence)} inferenceMs=$inferenceMs dropped=$droppedFrames rejection=$rejectionReason")
        }
    }

    private fun normalizeToUnit(value: Float, max: Int): Float {
        if (max <= 0) return 0f
        return (value / max.toFloat()).coerceIn(0f, 1f)
    }

    private fun releaseFrame(pending: PendingFrame) {
        pending.image.close()
        synchronized(lock) {
            frameInFlight = false
        }
    }

    private fun throttleWarning(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastWarningAtMs > 3000) {
            lastWarningAtMs = now
            onAnalyzerWarning(message)
        }
        if (now - lastPerfLogAtMs > LOG_INTERVAL_MS) {
            lastPerfLogAtMs = now
            Log.w(TAG, "pipeline_warning reason=$message received=$receivedFrames processed=$processedFrames dropped=$droppedFrames failed=$failedFrames")
        }
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
}
