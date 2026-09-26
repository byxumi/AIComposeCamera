package com.aicamera.camera

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.OrientationEventListener
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.aicamera.composition.AnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX 相机管理器：Preview + ImageCapture + ImageAnalysis 三 UseCase。
 * 负责相机生命周期绑定、镜头切换、闪光灯、变焦、点击对焦、拍照。
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    companion object {
        private const val TAG = "CameraManager"
    }

    // 对外状态
    private val _hasBackCamera = MutableStateFlow(true)
    val hasBackCamera: StateFlow<Boolean> = _hasBackCamera.asStateFlow()

    private val _hasFrontCamera = MutableStateFlow(true)
    val hasFrontCamera: StateFlow<Boolean> = _hasFrontCamera.asStateFlow()

    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady: StateFlow<Boolean> = _isCameraReady.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _deviceRotation = MutableStateFlow(0)
    val deviceRotation: StateFlow<Int> = _deviceRotation.asStateFlow()

    // 内部
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null

    // 点击对焦需要 PreviewView 做坐标转换
    private var previewView: PreviewView? = null
    fun previewViewRef(): PreviewView? = previewView

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var zoomRatio = 1f
    private var latestZoomRatio = 1f

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val capturing = AtomicBoolean(false)

    private var analysisCallback: ((AnalysisResult) -> Unit)? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    private var analyzer: AnalyzerManager? = null

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            _deviceRotation.value = orientation
        }
    }

    /** 绑定分析回调（由 ViewModel 提供，负责驱动构图引擎） */
    fun setAnalysisCallback(cb: (AnalysisResult) -> Unit) {
        analysisCallback = cb
    }

    /** 绑定预览 Surface（Compose AndroidView 里的 PreviewView） */
    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider, pv: PreviewView? = null) {
        previewSurfaceProvider = provider
        previewView = pv
        previewUseCase?.setSurfaceProvider(provider)
    }

    fun setAnalyzer(a: AnalyzerManager) {
        analyzer = a
    }

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
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder()
            .build()
        preview.setSurfaceProvider(previewSurfaceProvider)
        previewUseCase = preview

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(_flashMode.value)
            .build()
        imageCapture = capture

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val analyzer = this.analyzer ?: run {
                imageProxy.close()
                return@setAnalyzer
            }
            val w = imageProxy.width
            val h = imageProxy.height
            analyzer.analyze(imageProxy, w, h) { result ->
                analysisCallback?.invoke(result)
            }
        }
        imageAnalysis = analysis

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                capture,
                analysis
            )
            _hasBackCamera.value = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            _hasFrontCamera.value = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            _isCameraReady.value = true
            _errorMessage.value = null
            camera?.let { c ->
                val range = c.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                val max = c.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                c.cameraControl.setZoomRatio(1f)
                latestZoomRatio = 1f
            }
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
        val next = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        _flashMode.value = next
        imageCapture?.flashMode = next
    }

    fun setZoom(factor: Float) {
        val c = camera ?: return
        val state = c.cameraInfo.zoomState.value ?: return
        zoomRatio = factor.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        latestZoomRatio = zoomRatio
        _zoom.value = zoomRatio
        c.cameraControl.setZoomRatio(zoomRatio)
    }

    /** 点击对焦：
     * @param previewView 用于取景坐标 → 传感器坐标转换
     */
    fun focusAt(previewView: PreviewView, x: Float, y: Float) {
        val c = camera ?: return
        val factory: MeteringPointFactory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        c.cameraControl.startFocusAndMetering(action)
            .addListener({
                // 成功后轻微反馈（由 UI 层负责 haptic）
            }, ContextCompat.getMainExecutor(context))
    }

    /** 拍照，保存到相册 */
    fun takePhoto(onSaved: (File?) -> Unit) {
        val capture = imageCapture ?: run {
            onSaved(null)
            return
        }
        if (capturing.getAndSet(true)) return
        _isCapturing.value = true
        val rotation = _deviceRotation.value
        val file = createOutputFile(context)

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
                    _isCapturing.value = false
                    PhotoSaver.saveToGallery(context, file)
                    onSaved(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "拍照失败", exception)
                    capturing.set(false)
                    _isCapturing.value = false
                    _errorMessage.value = "拍照失败：${exception.message}"
                    onSaved(null)
                }
            })
    }

    private fun createOutputFile(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "photos")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    }

    fun stop() {
        orientationListener.disable()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        cameraProvider = null
        analyzer?.close()
        analyzer = null
    }
}