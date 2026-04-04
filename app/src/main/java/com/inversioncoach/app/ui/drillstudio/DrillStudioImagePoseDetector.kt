package com.inversioncoach.app.ui.drillstudio

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.inversioncoach.app.drills.catalog.JointPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ImagePoseDetectionResult(
    val normalizedJoints: Map<String, JointPoint>,
    val jointConfidence: Map<String, Float>,
    val qualityScore: Float,
)

class DrillStudioImagePoseDetector {
    private val detector by lazy {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build(),
        )
    }

    suspend fun detect(context: Context, imageUri: Uri): ImagePoseDetectionResult = withContext(Dispatchers.Default) {
        val input = InputImage.fromFilePath(context, imageUri)
        val pose = detector.process(input).await()
        val raw = mapOf(
            "head" to pose.getPoseLandmark(PoseLandmark.NOSE),
            "shoulder_left" to pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            "shoulder_right" to pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
            "elbow_left" to pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
            "elbow_right" to pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW),
            "wrist_left" to pose.getPoseLandmark(PoseLandmark.LEFT_WRIST),
            "wrist_right" to pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST),
            "hip_left" to pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
            "hip_right" to pose.getPoseLandmark(PoseLandmark.RIGHT_HIP),
            "knee_left" to pose.getPoseLandmark(PoseLandmark.LEFT_KNEE),
            "knee_right" to pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE),
            "ankle_left" to pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE),
            "ankle_right" to pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE),
        ).filterValues { it != null }

        if (raw.isEmpty()) {
            return@withContext ImagePoseDetectionResult(emptyMap(), emptyMap(), 0f)
        }

        val joints = raw.mapValues { (_, lm) ->
            normalizeLandmarkToImageSpace(
                landmark = lm,
                imageWidth = input.width,
                imageHeight = input.height,
            )
        }

        val confidence = raw.mapValues { (_, lm) -> lm?.inFrameLikelihood?.coerceIn(0f, 1f) ?: 0f }
        val quality = confidence.values.average().toFloat().coerceIn(0f, 1f)

        ImagePoseDetectionResult(
            normalizedJoints = DrillStudioPoseUtils.normalizeJointNames(joints),
            jointConfidence = confidence,
            qualityScore = quality,
        )
    }
}

internal fun normalizeLandmarkToImageSpace(
    landmark: PoseLandmark?,
    imageWidth: Int,
    imageHeight: Int,
): JointPoint {
    val x = landmark?.position?.x ?: landmark?.position3D?.x ?: 0f
    val y = landmark?.position?.y ?: landmark?.position3D?.y ?: 0f
    return normalizePointToImageSpace(x = x, y = y, imageWidth = imageWidth, imageHeight = imageHeight)
}

internal fun normalizePointToImageSpace(
    x: Float,
    y: Float,
    imageWidth: Int,
    imageHeight: Int,
): JointPoint {
    val safeWidth = imageWidth.coerceAtLeast(1)
    val safeHeight = imageHeight.coerceAtLeast(1)
    return JointPoint(
        x = (x / safeWidth.toFloat()).coerceIn(0f, 1f),
        y = (y / safeHeight.toFloat()).coerceIn(0f, 1f),
    )
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
