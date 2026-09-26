package com.aicamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.aicamera.composition.CompositionModels.Subject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * CameraManager — CameraX 生命周期 + ML Kit 检测
 *
 * 职责：
 *  1. 初始化 CameraX（Preview / ImageCapture / ImageAnalysis）
 *  2. 前后摄切换、闪光灯、点击对焦、变焦
 *  3. ML Kit 对象检测 + 人脸检测（可叠加，CountDownLatch 同步）
 *  4. 帧节流分析并回调
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {

    companion object {
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"
    }

    /** 帧分析回调（后台线程） */
    interface FrameCallback {
        fun onFrameAnalyzed(subjects: List<Subject>, imageWidth: Int, imageHeight: Int)
        fun onCameraError(message: String)
    }

    var frameCallback: FrameCallback? = null

    // ─── CameraX ───
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    var imageCapture: ImageCapture? = null
        private set
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    var isFrontCamera: Boolean = false
        private set

    // ─── ML Kit ───
    private var objectDetector: ObjectDetector? = null
    private var faceDetector: FaceDetector? = null
    private var objectDetectorEnabled = true
    private var faceDetectorEnabled = false

    // ─── 执行器 ───
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // ─── 帧节流 ───
    private var lastAnalysisTime = 0L
    private val analysisIntervalMs = 150L // 约 6-7 FPS 分析

    // ───────────────────────────
    // 初始化
    // ───────────────────────────

    fun setDetectors(objectsEnabled: Boolean, faceEnabled: Boolean) {
        objectDetectorEnabled = objectsEnabled
        faceDetectorEnabled = faceEnabled
    }

    private fun initDetectors() {
        if (objectDetectorEnabled) {
            objectDetector = ObjectDetection.getClient(
                ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build()
            )
        } else {
            objectDetector?.close()
            objectDetector = null
        }

        if (faceDetectorEnabled) {
            faceDetector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
            )
        } else {
            faceDetector?.close()
            faceDetector = null
        }
    }

    fun startCamera() {
        initDetectors()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "相机初始化失败", e)
                frameCallback?.onCameraError("相机初始化失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
            )
            .build()
            .also {
                it.setAnalyzer(analysisExecutor) { imageProxy ->
                    analyzeFrame(imageProxy)
                }
            }

        cameraProvider?.unbindAll()
        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageCapture, imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "绑定 UseCase 失败", e)
            frameCallback?.onCameraError("绑定相机失败: ${e.message}")
        }
    }

    // ───────────────────────────
    // 帧分析（ML Kit 检测）
    // ───────────────────────────

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < analysisIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalysisTime = now

        val mediaImage = imageProxy.image
        val subjRef = AtomicReference<List<Subject>?>(null)

        if (mediaImage == null || (objectDetector == null && faceDetector == null)) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val latch = CountDownLatch(2)

        // 对象检测
        if (objectDetector != null) {
            objectDetector!!.process(inputImage)
                .addOnSuccessListener { results ->
                    val subjects = results.mapNotNull { obj ->
                        val box = obj.boundingBox
                        val norm = normalizeBox(
                            box.left.toFloat(), box.top.toFloat(),
                            box.right.toFloat(), box.bottom.toFloat(),
                            imageProxy.width, imageProxy.height
                        ) ?: return@mapNotNull null
                        val category = mapCategory(obj.labels.firstOrNull()?.text)
                        Subject(
                            centerX = norm[0] + norm[2] / 2f,
                            centerY = norm[1] + norm[3] / 2f,
                            left = norm[0],
                            top = norm[1],
                            right = norm[0] + norm[2],
                            bottom = norm[1] + norm[3],
                            width = norm[2],
                            height = norm[3],
                            confidence = obj.trackingId?.let { 0.9f } ?: (obj.labels.firstOrNull()?.confidence ?: 0.7f),
                            category = category,
                            label = obj.labels.firstOrNull()?.text ?: ""
                        )
                    }
                    synchronized(subjRef) {
                        val prev = subjRef.get()
                        subjRef.set(if (prev != null) prev + subjects else subjects)
                    }
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "对象检测失败", e)
                    latch.countDown()
                }
        } else {
            latch.countDown()
        }

        // 人脸检测（仅作为主体补充，不单独生成引导，人脸即"人物"主体）
        if (faceDetector != null) {
            faceDetector!!.process(inputImage)
                .addOnSuccessListener { faces ->
                    val subjects = faces.mapNotNull { face ->
                        val box = face.boundingBox
                        val norm = normalizeBox(
                            box.left.toFloat(), box.top.toFloat(),
                            box.right.toFloat(), box.bottom.toFloat(),
                            imageProxy.width, imageProxy.height
                        ) ?: return@mapNotNull null
                        Subject(
                            centerX = norm[0] + norm[2] / 2f,
                            centerY = norm[1] + norm[3] / 2f,
                            left = norm[0],
                            top = norm[1],
                            right = norm[0] + norm[2],
                            bottom = norm[1] + norm[3],
                            width = norm[2],
                            height = norm[3],
                            confidence = 0.95f,
                            category = "人物",
                            label = "face"
                        )
                    }
                    synchronized(subjRef) {
                        val prev = subjRef.get()
                        subjRef.set(if (prev != null) prev + subjects else subjects)
                    }
                    latch.countDown()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "人脸检测失败", e)
                    latch.countDown()
                }
        } else {
            latch.countDown()
        }

        // 等两个检测完成，再回调
        cameraExecutor.execute {
            try {
                latch.await(500, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
            }
            val subjects = subjRef.get() ?: emptyList()
            // 去重：人脸优先（人物主体就够了，避免人脸和对象重复框）
            val merged = if (faceDetector != null && objectDetector != null) {
                subjects.filter { it.category != "人物" } + subjects.filter { it.category == "人物" }
            } else subjects
            frameCallback?.onFrameAnalyzed(
                merged.distinctBy { "${it.left}-${it.top}" },
                imageProxy.width,
                imageProxy.height
            )
            imageProxy.close()
        }
    }

    /** 归一化框到 [0,1]，返回 left/top/width/height */
    private fun normalizeBox(l: Float, t: Float, r: Float, b: Float, w: Int, h: Int): FloatArray? {
        if (w <= 0 || h <= 0) return null
        val nw = r - l
        val nh = b - t
        if (nw <= 0 || nh <= 0) return null
        return floatArrayOf(
            (l / w).coerceIn(0f, 1f),
            (t / h).coerceIn(0f, 1f),
            (nw / w).coerceIn(0f, 1f),
            (nh / h).coerceIn(0f, 1f)
        )
    }

    private fun mapCategory(label: String?): String {
        return when (label?.lowercase()) {
            "person" -> "人物"
            "cat", "dog", "bird", "pet" -> "宠物"
            "food", "fruit", "dish" -> "食物"
            "bottle", "book", "cup", "laptop", "phone", "product" -> "商品"
            "building", "house" -> "建筑"
            else -> "其他"
        }
    }

    // ───────────────────────────
    // 拍摄
    // ───────────────────────────

    interface CaptureCallback {
        fun onCaptured(file: File)
        fun onCaptureError(message: String)
    }

    fun takePicture(outputDir: File, callback: CaptureCallback) {
        val capture = imageCapture ?: run {
            callback.onCaptureError("相机未就绪")
            return
        }
        val file = File(
            outputDir,
            "IMG_${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())}.jpg"
        )
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                callback.onCaptured(file)
            }

            override fun onError(exception: ImageCaptureException) {
                callback.onCaptureError("拍照失败: ${exception.message}")
            }
        })
    }

    // ───────────────────────────
    // 控制
    // ───────────────────────────

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA
        bindUseCases()
    }

    fun setFlashEnabled(enabled: Boolean) {
        imageCapture?.flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun getMaxZoom(): Float = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f

    fun focusOn(x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF
        ).build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        objectDetector?.close()
        faceDetector?.close()
        analysisExecutor.shutdown()
        cameraExecutor.shutdown()
    }
}