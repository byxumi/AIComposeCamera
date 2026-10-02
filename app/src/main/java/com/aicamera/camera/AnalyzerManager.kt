package com.aicamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.aicamera.domain.model.AnalysisResult
import com.aicamera.domain.model.DetectedSubject
import com.aicamera.domain.model.FaceFeatures
import com.aicamera.domain.model.PoseLimb
import com.aicamera.domain.model.SubjectKind
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import android.util.Log

/**
 * ML Kit 三路检测管理器：
 * - 对象检测（Object，识别人/宠物/食物/商品…）
 * - 人脸检测（Face，表情/闭眼/倾斜）
 * - 姿态检测（Pose，MediaPipe，可选，模型在 assets）
 *
 * 帧分析在专用线程池执行，结果合并后回调。
 */
class AnalyzerManager(
    private val context: Context,
    private val enablePose: Boolean = false
) {

    companion object {
        private const val TAG = "AnalyzerManager"
        private const val POSE_MODEL = "pose_landmarker_lite.task"
        private const val MAX_OBJECTS = 3
        private const val MAX_FACES = 3
    }

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .enableTracking()
            .build()
    )

    private val poseLandmarker: PoseLandmarker? =
        if (enablePose) buildPoseLandmarker() else null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val frameCounter = AtomicInteger(0)

    private fun buildPoseLandmarker(): PoseLandmarker? = try {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(POSE_MODEL)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .build()
        PoseLandmarker.createFromOptions(context, options)
    } catch (e: Exception) {
        Log.w(TAG, "PoseLandmarker 创建失败: ${e.message}", e)
        null
    }

    /**
     * 分析一帧。帧节流：约每 3 帧处理 1 帧；忙时跳过。
     * @param imageProxy 相机场景帧
     * @param imageWidth 预览宽（用于归一化）
     * @param imageHeight 预览高
     * @param onResult 回调（主线程）
     * @param onClose 由调用方负责 close ImageProxy
     */
    fun analyze(
        imageProxy: ImageProxy,
        imageWidth: Int,
        imageHeight: Int,
        onResult: (AnalysisResult) -> Unit
    ) {
        if (busy.get()) {
            imageProxy.close()
            return
        }
        val n = frameCounter.incrementAndGet()
        if (n % 3 != 0) {
            imageProxy.close()
            return
        }
        busy.set(true)
        executor.execute {
            try {
                val inputImage = InputImage.fromMediaImage(
                    imageProxy.image ?: return@execute,
                    imageProxy.imageInfo.rotationDegrees
                )
                val rotation = inputImage.rotationDegrees
                val results = mutableListOf<DetectedSubject>()
                var pose: PoseLimb? = null

                // 对象 + 人脸并行检测（Tasks.await 同步等待，因为我们已在工作线程）
                try {
                    val objs = com.google.android.gms.tasks.Tasks.await(
                        objectDetector.process(inputImage)
                    )
                    objs.forEach { obj ->
                        val box = obj.boundingBox ?: return@forEach
                        results += DetectedSubject(
                            id = obj.trackingId ?: 0,
                            box = normalize(box, imageWidth, imageHeight, rotation),
                            label = obj.labels.firstOrNull()?.text ?: "对象",
                            confidence = obj.labels.firstOrNull()?.confidence ?: 1f,
                            kind = SubjectKind.OBJECT
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Object 检测失败: ${e.message}")
                }

                // 人脸结果
                try {
                    val faces = com.google.android.gms.tasks.Tasks.await(
                        faceDetector.process(inputImage)
                    )
                    faces.forEach { face ->
                        val box = face.boundingBox ?: return@forEach
                        results += DetectedSubject(
                            id = face.trackingId ?: 0,
                            box = normalize(box, imageWidth, imageHeight, rotation),
                            label = "人",
                            confidence = 1f,
                            kind = SubjectKind.FACE,
                            faceFeatures = FaceFeatures(
                                smilingProbability = face.smilingProbability,
                                leftEyeOpen = face.leftEyeOpenProbability,
                                rightEyeOpen = face.rightEyeOpenProbability,
                                headEulerAngleY = face.headEulerAngleY,
                                headEulerAngleZ = face.headEulerAngleZ
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Face 检测失败: ${e.message}")
                }

                // 姿态（可选）：MediaPipe 需要 Bitmap → MPImage
                poseLandmarker?.let { pm ->
                    try {
                        val raw = Bitmap.createBitmap(
                            imageProxy.width,
                            imageProxy.height,
                            Bitmap.Config.ARGB_8888
                        )
                        raw.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
                        val mpImage = BitmapImageBuilder(raw).build()
                        val poseResult: PoseLandmarkerResult = pm.detect(mpImage)
                        poseResult.landmarks().firstOrNull()?.let { lm ->
                            if (lm.size >= 25) {
                                val leftShoulder = lm[11]
                                val rightShoulder = lm[12]
                                val leftHip = lm[23]
                                val rightHip = lm[24]
                                val visibility =
                                    lm[11].visibility().orElse(1f) + lm[12].visibility().orElse(1f)
                                pose = PoseLimb(
                                    shoulderMidX = ((leftShoulder.x() + rightShoulder.x()) / 2f),
                                    shoulderMidY = ((leftShoulder.y() + rightShoulder.y()) / 2f),
                                    hipMidX = ((leftHip.x() + rightHip.x()) / 2f),
                                    hipMidY = ((leftHip.y() + rightHip.y()) / 2f),
                                    bodyHeight = kotlin.math.abs(
                                        (leftShoulder.y() + rightShoulder.y()) / 2f -
                                            (leftHip.y() + rightHip.y()) / 2f
                                    ),
                                    visible = visibility > 0.4f
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Pose 检测失败: ${e.message}")
                    }
                }

                val sorted = results.sortedByDescending { it.box.width() * it.box.height() }
                    .take(MAX_OBJECTS)
                onResult(AnalysisResult(subjects = sorted, pose = pose))
            } catch (e: Exception) {
                Log.e(TAG, "分析帧失败", e)
                onResult(AnalysisResult())
            } finally {
                imageProxy.close()
                busy.set(false)
            }
        }
    }

    /** 归一化到 [0..1]；把图像传感器坐标映射到竖屏预览坐标 */
    private fun normalize(rect: Rect, imgW: Int, imgH: Int, rotation: Int): RectF {
        val l = rect.left.toFloat()
        val t = rect.top.toFloat()
        val r = rect.right.toFloat()
        val b = rect.bottom.toFloat()
        val w = imgW.toFloat()
        val h = imgH.toFloat()
        val (x1, y1, x2, y2) = when (rotation) {
            90 -> floatArrayOf(t / h, 1f - r / w, b / h, 1f - l / w)
            180 -> floatArrayOf(1f - r / w, 1f - b / h, 1f - l / w, 1f - t / h)
            270 -> floatArrayOf(1f - b / h, l / w, 1f - t / h, r / w)
            else -> floatArrayOf(l / w, t / h, r / w, b / h)
        }
        return RectF(x1, y1, x2, y2)
    }

    fun close() {
        busy.set(true)
        executor.shutdown()
        try { objectDetector.close() } catch (_: Exception) {}
        try { faceDetector.close() } catch (_: Exception) {}
        try { poseLandmarker?.close() } catch (_: Exception) {}
    }
}