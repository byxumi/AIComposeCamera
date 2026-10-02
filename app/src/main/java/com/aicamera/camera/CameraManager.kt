package com.aicamera.camera

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.OrientationEventListener
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.aicamera.domain.model.AnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX 相机管理器：Preview + ImageCapture + ImageAnalysis + VideoCapture
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    companion object {
        private const val TAG = "CameraManager"
    }

    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady: StateFlow<Boolean> = _isCameraReady.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private var recording: Recording? = null

    private var previewView: PreviewView? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var zoomRatio = 1f

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val capturing = AtomicBoolean(false)

    private var analysisCallback: ((AnalysisResult) -> Unit)? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    private var analyzer: AnalyzerManager? = null
    private var onVideoSaved: ((File?) -> Unit)? = null

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            // 拍照方向由系统处理，这里保持接口
        }
    }

    fun setAnalyzer(a: AnalyzerManager) { analyzer = a }

    fun setAnalysisCallback(cb: (AnalysisResult) -> Unit) { analysisCallback = cb }

    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider, pv: PreviewView? = null) {
        previewSurfaceProvider = provider
        previewView = pv
        previewUseCase?.setSurfaceProvider(provider)
    }

    fun previewViewRef(): PreviewView? = previewView

    fun start() {
        if (!hasCameraPermission()) {
            _errorMessage.value = "没有相机权限"
            return
        }
        orientationListener.enable()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "相机初始化失败", e)
                _errorMessage.value = "相机初始化失败：${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewSurfaceProvider)
        previewUseCase = preview

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = capture

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val a = this.analyzer ?: run { imageProxy.close(); return@setAnalyzer }
            a.analyze(imageProxy, imageProxy.width, imageProxy.height) { result ->
                analysisCallback?.invoke(result)
            }
        }
        imageAnalysis = analysis

        // 视频录制
        val recorder = Recorder.Builder().build()
        val vc = VideoCapture.withOutput(recorder)
        videoCapture = vc

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner, selector, preview, capture, analysis, vc
            )
            _isCameraReady.value = true
            _errorMessage.value = null
            camera?.cameraControl?.setZoomRatio(1f)
            zoomRatio = 1f
        } catch (e: Exception) {
            Log.e(TAG, "绑定 UseCase 失败", e)
            _errorMessage.value = "绑定相机失败：${e.message}"
        }
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindUseCases()
    }

    fun toggleFlash() {
        imageCapture?.flashMode = if (imageCapture?.flashMode == ImageCapture.FLASH_MODE_ON) {
            ImageCapture.FLASH_MODE_OFF
        } else {
            ImageCapture.FLASH_MODE_ON
        }
    }

    fun setZoom(factor: Float) {
        val c = camera ?: return
        val state = c.cameraInfo.zoomState.value ?: return
        zoomRatio = factor.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        _zoom.value = zoomRatio
        c.cameraControl.setZoomRatio(zoomRatio)
    }

    fun focusAt(previewView: PreviewView, x: Float, y: Float) {
        val c = camera ?: return
        val factory: MeteringPointFactory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        c.cameraControl.startFocusAndMetering(action)
    }

    /** 拍照保存到临时文件 */
    fun takePhoto(onSaved: (File?) -> Unit) {
        val capture = imageCapture ?: run { onSaved(null); return }
        if (capturing.getAndSet(true)) return
        val file = createTempFile("IMG_", ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file)
            .setMetadata(
                ImageCapture.Metadata().apply {
                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                }
            )
            .build()
        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    capturing.set(false)
                    onSaved(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "拍照失败", exception)
                    capturing.set(false)
                    _errorMessage.value = "拍照失败：${exception.message}"
                    onSaved(null)
                }
            })
    }

    /** 开始视频录制 */
    fun startRecording(onSaved: (File?) -> Unit) {
        val vc = videoCapture ?: return
        if (_isRecording.value) return
        onVideoSaved = onSaved
        val file = createTempFile("VID_", ".mp4")
        val options = FileOutputOptions.Builder(file).build()
        recording = vc.output
            .prepareRecording(context, options)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        recording = null
                        if (event.hasError()) {
                            _errorMessage.value = "录像失败：${event.error}"
                            onSaved(null)
                        } else {
                            onSaved(file)
                        }
                    }
                    is VideoRecordEvent.Start -> _isRecording.value = true
                    else -> {}
                }
            }
    }

    /** 停止录制 */
    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun createTempFile(prefix: String, suffix: String): File {
        val dir = File(context.cacheDir, "captures")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$prefix${System.currentTimeMillis()}$suffix")
    }

    fun stop() {
        orientationListener.disable()
        recording?.stop()
        recording = null
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        cameraProvider = null
        analyzer?.close()
        analyzer = null
    }
}