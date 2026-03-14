package com.inversioncoach.app.pose

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.inversioncoach.app.model.JointPoint
import com.inversioncoach.app.model.PoseFrame
import java.util.concurrent.ExecutorService

class PoseAnalyzer(
    context: Context,
    private val onPoseFrame: (PoseFrame) -> Unit,
    private val onAnalyzerWarning: (String) -> Unit,
    private val backgroundExecutor: ExecutorService,
) : ImageAnalysis.Analyzer {

    private val landmarkNames = listOf(
        "nose", "left_eye_inner", "left_eye", "left_eye_outer", "right_eye_inner", "right_eye", "right_eye_outer",
        "left_ear", "right_ear", "mouth_left", "mouth_right", "left_shoulder", "right_shoulder", "left_elbow",
        "right_elbow", "left_wrist", "right_wrist", "left_pinky", "right_pinky", "left_index", "right_index",
        "left_thumb", "right_thumb", "left_hip", "right_hip", "left_knee", "right_knee", "left_ankle", "right_ankle",
        "left_heel", "right_heel", "left_foot_index", "right_foot_index",
    )

    private var lastWarningAtMs = 0L

    @Suppress("unused")
    private val poseLandmarker: PoseLandmarker by lazy {
        val baseOptions = BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(::onResult)
            .setErrorListener { _, _ -> onAnalyzerWarning("Pose model error. Reposition camera and retry.") }
            .build()
        PoseLandmarker.createFromOptions(context, options)
    }

    private fun onResult(result: PoseLandmarkerResult, inputImage: com.google.mediapipe.framework.image.MPImage) {
        val landmarks = result.landmarks().firstOrNull().orEmpty()
        val joints = landmarks.mapIndexed { index, lm ->
            val jointName = landmarkNames.getOrElse(index) { "joint_$index" }
            JointPoint(jointName, lm.x(), lm.y(), lm.z(), lm.visibility().orElse(0f))
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

        onPoseFrame(
            PoseFrame(
                timestampMs = System.currentTimeMillis(),
                joints = joints,
                confidence = if (joints.isEmpty()) 0f else joints.map { it.visibility }.average().toFloat(),
            ),
        )
    }

    override fun analyze(image: ImageProxy) {
        // TODO: Wire ImageProxy -> MPImage conversion and detectAsync() call.
        // Until then, surface explicit warning once per 3 seconds instead of silently dropping frames.
        val now = System.currentTimeMillis()
        if (now - lastWarningAtMs > 3000) {
            lastWarningAtMs = now
            onAnalyzerWarning("Pose analyzer is not fully initialized yet. Live scoring is degraded.")
        }
        image.close()
    }
}
