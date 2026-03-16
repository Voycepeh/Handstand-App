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

private const val TAG = "UploadPoseFrameSource"

class MlKitVideoPoseFrameSource(
    private val context: Context,
    private val sampleFps: Int = 12,
) : VideoPoseFrameSource {
    override fun decode(videoUri: Uri): Sequence<PoseFrame> {
        val retriever = MediaMetadataRetriever()
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build(),
        )
        val frames = mutableListOf<PoseFrame>()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
            if (durationMs <= 0L) {
                Log.w(TAG, "decode_failed reason=missing_duration uri=$videoUri")
                frames
            } else {
                val intervalMs = (1000f / sampleFps.coerceAtLeast(1)).toLong().coerceAtLeast(16L)
                var timestampMs = 0L
                while (timestampMs <= durationMs) {
                    val bitmap = retriever.getFrameAtTime(timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    val frame = bitmap?.let { mapBitmapToPoseFrame(it, detector, timestampMs, width, height) }
                    bitmap?.recycle()
                    if (frame != null) frames += frame
                    timestampMs += intervalMs
                }
                frames
            }
        } finally {
            runCatching { detector.close() }
            runCatching { retriever.release() }
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
}
