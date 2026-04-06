package com.inversioncoach.app.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.inversioncoach.app.pose.PoseAnalyzer
import com.inversioncoach.app.ui.live.LiveCameraCapabilities
import com.inversioncoach.app.ui.live.LiveCameraFacing
import com.inversioncoach.app.ui.live.supportedZoomPresets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class CameraBindResult(
    val selectedCameraFacing: LiveCameraFacing,
    val availableCameraFacings: Set<LiveCameraFacing>,
    val selectedZoomRatio: Float,
    val supportedZoomRatios: List<Float>,
)

class CameraSessionManager(
    private val context: Context,
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: PoseAnalyzer,
        preferredFacing: LiveCameraFacing,
        preferredZoomRatio: Float,
        onReady: (Boolean, String?, CameraBindResult?) -> Unit,
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                cameraProvider = provider
                val availableFacings = availableFacings(provider)
                if (availableFacings.isEmpty()) {
                    throw IllegalStateException("No available camera on device")
                }

                val selectedFacing = if (preferredFacing in availableFacings) {
                    preferredFacing
                } else {
                    availableFacings.firstOrNull { it == LiveCameraFacing.BACK } ?: availableFacings.first()
                }

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setImageQueueDepth(2)
                    .setTargetResolution(Size(960, 540))
                    .build().apply {
                        setAnalyzer(cameraExecutor, analyzer)
                    }
                val qualitySelector = QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                )
                videoCapture = VideoCapture.withOutput(
                    Recorder.Builder()
                        .setQualitySelector(qualitySelector)
                        .build(),
                )

                val cameraSelector = CameraSelector.Builder().requireLensFacing(selectedFacing.cameraSelectorLensFacing).build()

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    videoCapture,
                )
                activeCamera = camera

                val zoomState = camera.cameraInfo.zoomState.value
                val minZoomRatio = zoomState?.minZoomRatio ?: 1f
                val maxZoomRatio = zoomState?.maxZoomRatio ?: 1f
                val supportedZoomRatios = supportedZoomPresets(minZoomRatio = minZoomRatio, maxZoomRatio = maxZoomRatio)
                val resolvedZoomRatio = preferredZoomRatio.coerceIn(minZoomRatio, maxZoomRatio)
                camera.cameraControl.setZoomRatio(resolvedZoomRatio)
                Log.i(
                    "CameraSessionManager",
                    "CameraX bound facing=${selectedFacing.encoded} zoomRatio=$resolvedZoomRatio min=$minZoomRatio max=$maxZoomRatio available=${availableFacings.map { it.encoded }}",
                )
                CameraBindResult(
                    selectedCameraFacing = selectedFacing,
                    availableCameraFacings = availableFacings,
                    selectedZoomRatio = resolvedZoomRatio,
                    supportedZoomRatios = supportedZoomRatios,
                )
            }.onSuccess { result ->
                onReady(true, null, result)
            }.onFailure {
                Log.e("CameraSessionManager", "Failed binding camera", it)
                onReady(false, "Unable to start camera session", null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun updateZoom(zoomRatio: Float, onResult: (Float?) -> Unit = {}) {
        val camera = activeCamera ?: run {
            onResult(null)
            return
        }
        val state = camera.cameraInfo.zoomState.value
        val min = state?.minZoomRatio ?: 1f
        val max = state?.maxZoomRatio ?: 1f
        val resolved = zoomRatio.coerceIn(min, max)
        camera.cameraControl.setZoomRatio(resolved)
        onResult(resolved)
    }

    fun currentCapabilities(): LiveCameraCapabilities {
        val camera = activeCamera
        if (camera == null) return LiveCameraCapabilities()
        val state = camera.cameraInfo.zoomState.value
        return LiveCameraCapabilities(
            availableFacings = cameraProvider?.let { availableFacings(it) } ?: setOf(LiveCameraFacing.BACK),
            supportedZoomRatios = supportedZoomPresets(
                minZoomRatio = state?.minZoomRatio ?: 1f,
                maxZoomRatio = state?.maxZoomRatio ?: 1f,
            ),
        )
    }

    private fun availableFacings(provider: ProcessCameraProvider): Set<LiveCameraFacing> = buildSet {
        if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) add(LiveCameraFacing.BACK)
        if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) add(LiveCameraFacing.FRONT)
    }

    fun videoCapture(): VideoCapture<Recorder>? = videoCapture

    fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
