package com.aicamera.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.camera.AnalyzerManager
import com.aicamera.camera.CameraManager
import com.aicamera.composition.AnalysisResult
import com.aicamera.composition.AutoShutterEngine
import com.aicamera.composition.CompositionRuleEngine
import com.aicamera.composition.CompositionScorer
import com.aicamera.composition.DebounceTracker
import com.aicamera.composition.FramingBoxEngine
import com.aicamera.composition.Guidance
import com.aicamera.composition.GuidanceType
import com.aicamera.composition.OverlayState
import com.aicamera.composition.Recommendation
import com.aicamera.composition.Severity
import com.aicamera.settings.CameraSettings
import com.aicamera.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log

/**
 * 构图相机 ViewModel：编排 相机 → 检测 → 规则引擎 → 覆盖层/评分/自动快门。
 */
class CompositionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CompositionViewModel"
    }

    private lateinit var cameraManager: CameraManager
    private lateinit var analyzerManager: AnalyzerManager
    private val settingsRepository = SettingsRepository(application)
    private var cameraBound = false

    // 预览 Surface 可能先于 cameraManager 初始化到达，先缓存
    private var pendingPreview: Pair<Preview.SurfaceProvider, androidx.camera.view.PreviewView?>? = null

    // 规则组件
    private val scorer = CompositionScorer
    private val ruleEngine = CompositionRuleEngine
    private val framer = FramingBoxEngine
    private val debounce = DebounceTracker(requiredFrames = 6)
    private val autoShutter = AutoShutterEngine()

    // 水平仪
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var rotationReady = false

    // UI 状态
    private val _overlayState = MutableStateFlow(OverlayState())
    val overlayState: StateFlow<OverlayState> = _overlayState.asStateFlow()

    private val _settings = MutableStateFlow(CameraSettings())
    val settings: StateFlow<CameraSettings> = _settings.asStateFlow()

    private val _flashEnabled = MutableStateFlow(true)
    val flashEnabled: StateFlow<Boolean> = _flashEnabled.asStateFlow()

    private val _lastPhotoPath = MutableStateFlow<String?>(null)
    val lastPhotoPath: StateFlow<String?> = _lastPhotoPath.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // 最新分析结果缓存（供置顶体验）
    private var latestAnalysis = AnalysisResult()
    private var latestScore = 0
    private var latestThreshold = 70

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _settings.value = s
                latestThreshold = s.shutterSensitivity.threshold
            }
        }
        setupSensors()
    }

    /** 绑定相机管理器（Activity onResume 时注入） */
    fun bindCamera(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        onPreviewReady: (Preview.SurfaceProvider) -> Unit
    ) {
        if (cameraBound) return
        cameraBound = true
        val ctx = getApplication<Application>()
        cameraManager = CameraManager(ctx, lifecycleOwner)
        analyzerManager = AnalyzerManager(ctx, _settings.value.poseGuidance)

        cameraManager.setAnalyzer(analyzerManager)
        cameraManager.setAnalysisCallback { result ->
            onAnalysisResult(result)
        }

        viewModelScope.launch {
            // 订阅相机状态更新 flash 可用性
            cameraManager.hasBackCamera.collect { }
        }

        // 设置页面变更 pose 时不动态重建（需重启生效，设置页有提示）
        cameraManager.start()
        _flashEnabled.value = true

        // 若预览 Surface 已先到，补绑
        pendingPreview?.let { (provider, pv) ->
            cameraManager.setPreviewSurfaceProvider(provider, pv)
            pendingPreview = null
        }
    }

    /** 绑定预览 Surface（若 cameraManager 未就绪则缓存，bindCamera 时补绑） */
    fun bindPreview(provider: Preview.SurfaceProvider, pv: androidx.camera.view.PreviewView? = null) {
        if (!::cameraManager.isInitialized) {
            pendingPreview = provider to pv
            return
        }
        cameraManager.setPreviewSurfaceProvider(provider, pv)
    }

    /** 取景坐标点击对焦 */
    fun onTapToFocus(x: Float, y: Float) {
        if (!::cameraManager.isInitialized) return
        cameraManager.previewViewRef()?.let { pv ->
            cameraManager.focusAt(pv, x, y)
        }
    }

    private fun onAnalysisResult(result: AnalysisResult) {
        latestAnalysis = result
        viewModelScope.launch {
            evaluateAndPublish(result)
        }
    }

    /** 主体分析 → 规则引擎 → 覆盖层状态 */
    private suspend fun evaluateAndPublish(result: AnalysisResult) {
        val curSettings = _settings.value
        val subjects = result.subjects
        val pose = result.pose

        val best = ruleEngine.pickBestSubject(subjects)
        if (best == null) {
            // 无主体：显示"未检测到主体"，评分低，自动快门不触发
            val state = _overlayState.value.copy(
                subjects = subjects,
                pose = pose,
                guidance = Guidance(GuidanceType.NONE, "未检测到主体，请对准拍摄对象", Severity.INFO),
                recommendation = null,
                score = 0,
                horizonDegrees = _horizonDegrees,
                gridMode = curSettings.gridMode.value,
                scoreEnabled = curSettings.showScore,
                subjectsEnabled = curSettings.showSubjects,
                poseEnabled = curSettings.poseGuidance
            )
            _overlayState.value = state
            debounce.reset()
            autoShutter.onFrame(0, latestThreshold, subjectVisible = false)
            return
        }

        // 构图评估
        val guidance = ruleEngine.evaluate(
            subject = best,
            faceFeatures = best.faceFeatures,
            horizonDegrees = _horizonDegrees,
            pose = pose
        )

        // 防抖
        val showGuidance = debounce.shouldShow(guidance)

        // 评分：基础分 + 惩罚
        val base = scorer.scoreSubject(best.box)
        val penalties = buildPenalties(guidance, best.box)
        val finalScore = scorer.applyPenalties(base, penalties)
        latestScore = finalScore

        // 推荐框
        val rec: Recommendation? = if (showGuidance && guidance.type != GuidanceType.GOOD) {
            framer.recommend(best.box)
        } else null

        // 自动快门
        val threshold = curSettings.shutterSensitivity.threshold
        if (curSettings.autoShutter) {
            val shouldFire = autoShutter.onFrame(finalScore, threshold, subjectVisible = true)
            if (shouldFire) {
                triggerAutoShutter()
            }
        } else {
            autoShutter.reset()
        }

        val shownGuidance = if (showGuidance) guidance else Guidance(GuidanceType.NONE, "", Severity.INFO)

        val newState = OverlayState(
            gridMode = curSettings.gridMode.value,
            subjects = subjects,
            pose = pose,
            guidance = shownGuidance,
            recommendation = if (guidance.type == GuidanceType.GOOD) null else rec,
            score = finalScore,
            scoreEnabled = curSettings.showScore,
            subjectsEnabled = curSettings.showSubjects,
            horizonDegrees = _horizonDegrees,
            poseEnabled = curSettings.poseGuidance
        )
        _overlayState.value = newState
    }

    /** 构造惩罚项：按引导类型扣分 */
    private fun buildPenalties(guidance: Guidance, box: RectF): Map<String, Int> {
        val penalties = mutableMapOf<String, Int>()
        when (guidance.type) {
            GuidanceType.HEAD_CROPPED -> penalties["head_crop"] = 25
            GuidanceType.FEET_CROPPED -> penalties["feet_crop"] = 22
            GuidanceType.HORIZON_TILTED -> penalties["tilt"] = 15
            GuidanceType.SUBJECT_TOO_SMALL -> penalties["small"] = 18
            GuidanceType.SUBJECT_TOO_LARGE -> penalties["large"] = 15
            GuidanceType.TOP_SPACE -> penalties["top_space"] = 12
            GuidanceType.MOVE_LEFT, GuidanceType.MOVE_RIGHT,
            GuidanceType.MOVE_UP, GuidanceType.MOVE_DOWN,
            GuidanceType.ALIGN_THIRDS -> penalties["align"] = 10
            GuidanceType.EYES_CLOSED -> penalties["eyes"] = 15
            GuidanceType.SMILE -> penalties["smile"] = 5
            GuidanceType.POSE_SHOULDER -> penalties["shoulder"] = 8
            else -> {}
        }
        return penalties
    }

    /** 自动快门触发 */
    private fun triggerAutoShutter() {
        cameraManager.takePhoto { file ->
            if (file != null) {
                _lastPhotoPath.value = file.absolutePath
                _toastMessage.value = "已自动拍摄"
                hapticFeedback()
                autoShutter.onShotTaken()
            }
        }
    }

    /** 手动拍照 */
    fun manualShutter() {
        cameraManager.takePhoto { file ->
            if (file != null) {
                _lastPhotoPath.value = file.absolutePath
                _toastMessage.value = "已保存到相册"
                hapticFeedback()
            }
        }
    }

    private fun hapticFeedback() {
        if (!_settings.value.hapticFeedback) return
        val ctx = getApplication<Application>()
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    fun onToastShown() { _toastMessage.value = null }

    // ===== 相机操作转发（带初始化防护，避免未授权/未初始化时崩溃）=====
    fun switchCamera() {
        if (::cameraManager.isInitialized) cameraManager.switchCamera()
    }
    fun toggleFlash() {
        if (::cameraManager.isInitialized) cameraManager.toggleFlash()
    }
    fun setZoomRatio(f: Float) {
        if (::cameraManager.isInitialized) cameraManager.setZoom(f)
    }

    // ===== 设置更新 =====
    fun updateGridMode(mode: com.aicamera.settings.GridMode) {
        viewModelScope.launch { settingsRepository.setGridMode(mode) }
    }
    fun updateShowSubjects(v: Boolean) {
        viewModelScope.launch { settingsRepository.setShowSubjects(v) }
    }
    fun updateShowScore(v: Boolean) {
        viewModelScope.launch { settingsRepository.setShowScore(v) }
    }
    fun updateAutoShutter(v: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoShutter(v) }
    }
    fun updateSensitivity(s: com.aicamera.settings.ShutterSensitivity) {
        viewModelScope.launch { settingsRepository.setSensitivity(s) }
    }
    fun updatePoseGuidance(v: Boolean) {
        viewModelScope.launch { settingsRepository.setPoseGuidance(v) }
    }
    fun updateHaptic(v: Boolean) {
        viewModelScope.launch { settingsRepository.setHaptic(v) }
    }

    // ===== 传感器水平仪 =====
    private var _horizonDegrees = 0f

    private fun setupSensors() {
        val ctx = getApplication<Application>()
        sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accelerometer != null && magnetometer != null) {
            sensorManager?.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager?.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER ->
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                Sensor.TYPE_MAGNETIC_FIELD ->
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
            }
            val rotation = FloatArray(9)
            if (SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotation, orientation)
                // orientation[2] = roll（绕 Z 轴），转成度数
                val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
                _horizonDegrees = rollDeg
                rotationReady = true
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** 水平仪度数（供 UI 显示） */
    fun horizonDegrees(): Float = _horizonDegrees

    override fun onCleared() {
        super.onCleared()
        sensorManager?.unregisterListener(sensorListener)
        if (::cameraManager.isInitialized) {
            runCatching { cameraManager.stop() }
        }
    }
}