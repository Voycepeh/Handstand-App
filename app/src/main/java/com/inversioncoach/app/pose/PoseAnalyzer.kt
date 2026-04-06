package com.inversioncoach.app.pose

import android.util.Log
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import com.inversioncoach.app.pose.model.JointLandmark
import com.inversioncoach.app.pose.model.JointType
import com.inversioncoach.app.pose.model.MlKitPoseMapper
import com.inversioncoach.app.pose.model.PoseFrame as InternalPoseFrame
import java.util.concurrent.ExecutorService

class PoseAnalyzer(
    private val onPoseFrame: (PoseFrame) -> Unit,
    private val onAnalyzerWarning: (String) -> Unit,
    private val backgroundExecutor: ExecutorService,
    private val isMirrored: () -> Boolean = { false },
) : ImageAnalysis.Analyzer {

    private data class PendingFrame(
        val image: ImageProxy,
        val startedAtMs: Long,
        val imageWidth: Int,
        val imageHeight: Int,
        val rotationDegrees: Int,
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
    private val poseMapper = MlKitPoseMapper()
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
            rotationDegrees = image.imageInfo.rotationDegrees,
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
                    rotationDegrees = pending.rotationDegrees,
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
        rotationDegrees: Int,
    ) {
        val normalizedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageHeight else imageWidth
        val normalizedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageWidth else imageHeight

        val landmarks = pose.allPoseLandmarks
        val joints = landmarks.mapNotNull { landmark ->
            val visibility = landmark.inFrameLikelihood
            if (visibility < MIN_VISIBLE_JOINT_CONFIDENCE) return@mapNotNull null
            JointPoint(
                name = poseMapper.landmarkType(landmark.landmarkType).legacyName,
                x = normalizeToUnit(landmark.position.x.toFloat(), normalizedWidth),
                y = normalizeToUnit(landmark.position.y.toFloat(), normalizedHeight),
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
            val internalFrame = toInternalFrame(
                timestampMs = System.currentTimeMillis(),
                joints = joints,
                confidence = confidence,
                landmarksDetected = landmarks.size,
                inferenceMs = inferenceMs,
                rejectionReason = rejectionReason,
                normalizedWidth = normalizedWidth,
                normalizedHeight = normalizedHeight,
                rotationDegrees = rotationDegrees,
                mirrored = isMirrored(),
            )
            onPoseFrame(poseMapper.toLegacy(internalFrame))
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


    private fun toInternalFrame(
        timestampMs: Long,
        joints: List<JointPoint>,
        confidence: Float,
        landmarksDetected: Int,
        inferenceMs: Long,
        rejectionReason: String,
        normalizedWidth: Int,
        normalizedHeight: Int,
        rotationDegrees: Int,
        mirrored: Boolean,
    ): InternalPoseFrame = InternalPoseFrame(
        timestampMs = timestampMs,
        joints = joints.map {
            JointLandmark(
                jointType = JointType.entries.firstOrNull { type -> type.legacyName == it.name } ?: JointType.UNKNOWN,
                x = it.x,
                y = it.y,
                z = it.z,
                visibility = it.visibility,
            )
        },
        confidence = confidence,
        landmarksDetected = landmarksDetected,
        inferenceTimeMs = inferenceMs,
        droppedFrames = droppedFrames.toInt(),
        rejectionReason = rejectionReason,
        analysisWidth = normalizedWidth,
        analysisHeight = normalizedHeight,
        analysisRotationDegrees = rotationDegrees,
        mirrored = mirrored,
    )

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

}
