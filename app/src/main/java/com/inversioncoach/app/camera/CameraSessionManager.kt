package com.inversioncoach.app.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.inversioncoach.app.pose.PoseAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraSessionManager(
    private val context: Context,
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraProvider: ProcessCameraProvider? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: PoseAnalyzer,
        zoomOutCamera: Boolean,
        onReady: (Boolean, String?) -> Unit,
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setImageQueueDepth(2)
                    .setTargetResolution(Size(1280, 720))
                    .build().apply {
                        setAnalyzer(cameraExecutor, analyzer)
                    }
                videoCapture = VideoCapture.withOutput(Recorder.Builder().build())

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                    videoCapture,
                )

                val zoomState = camera.cameraInfo.zoomState.value
                val minZoomRatio = zoomState?.minZoomRatio ?: 1f
                val maxZoomRatio = zoomState?.maxZoomRatio ?: 1f
                val requestedZoomRatio = if (zoomOutCamera) 0.5f else 1f
                val zoomedOutRatio = requestedZoomRatio.coerceIn(minZoomRatio, maxZoomRatio)
                camera.cameraControl.setZoomRatio(zoomedOutRatio)
                Log.i(
                    "CameraSessionManager",
                    "CameraX bound with KEEP_ONLY_LATEST queueDepth=2 zoomRatio=$zoomedOutRatio (requested=$requestedZoomRatio min=$minZoomRatio max=$maxZoomRatio)",
                )
            }.onSuccess {
                onReady(true, null)
            }.onFailure {
                Log.e("CameraSessionManager", "Failed binding camera", it)
                onReady(false, "Unable to start camera session")
            }
        }, ContextCompat.getMainExecutor(context))
    }


    fun videoCapture(): VideoCapture<Recorder>? = videoCapture

    fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
