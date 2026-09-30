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
import com.aicamera.composition.AiGuideEngine
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
import com.aicamera.settings.GridMode
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
    private val aiGuide = AiGuideEngine

    // ── mola 复刻状态 ──
    private var currentMode = com.aicamera.composition.ShootingMode.AUTO
    private var currentFilter = com.aicamera.composition.FilterStyle.NONE
    private var aiAssistActive = false
    private var aiPhotographerPhase = com.aicamera.composition.AiPhase.IDLE
    private var aiPromptText = ""

    // ── mola 相机控件状态 ──
    private var currentAspect = com.aicamera.composition.AspectRatio.RATIO_4_3
    private var currentExposure = 0f
    private var showHorizonLine = true
    private var showGridLines = true
    private var currentFrameStyle = com.aicamera.composition.FrameStyle.NONE
    private var currentTimer = 0
    private var currentFlashState = com.aicamera.composition.FlashState.OFF

    // 手动选中/锁定的主体 id（AI 辅助模式下点击主体锁定）
    private var lockedSubjectId: Int? = null
    // AI 辅助目标是否已对准（触发一次自动快门的防重复标志）
    private var aiReachedFired = false

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
                guidance = Guidance(GuidanceType.NONE, if (aiAssistActive) "未找到主体，请将主体移入画面" else "未检测到主体，请对准拍摄对象", Severity.INFO),
                recommendation = null,
                score = 0,
                horizonDegrees = _horizonDegrees,
                gridMode = curSettings.gridMode.value,
                scoreEnabled = curSettings.showScore,
                subjectsEnabled = curSettings.showSubjects,
                poseEnabled = curSettings.poseGuidance,
                shootingMode = currentMode,
                filterStyle = currentFilter,
                aiAssistActive = aiAssistActive,
                aiTarget = if (aiAssistActive) aiGuide.createTarget(currentMode, null, 1080f, 1920f) else null,
                aiPhotographer = photographerState(),
                guideSteps = guideStepsFor(currentMode, hasSubject = false),
                selectedSubjectId = lockedSubjectId,
                lockedSubjectId = lockedSubjectId,
                showTargetReticle = aiAssistActive,
                aiPhase = aiPhotographerPhase,
                aiMessage = if (aiAssistActive) "未找到主体，请将主体移入画面" else "",
                aspectRatio = currentAspect,
                exposureCompensation = currentExposure,
                showHorizonLine = showHorizonLine,
                showGridLines = showGridLines,
                frameStyle = currentFrameStyle,
                timerSeconds = currentTimer,
                flashState = currentFlashState
            )
            _overlayState.value = state
            debounce.reset()
            aiReachedFired = false
            autoShutter.onFrame(0, latestThreshold, subjectVisible = false)
            return
        }

        // 构图评估
        // 手动锁定主体优先（mola「手动选主体」）
        val primary = if (lockedSubjectId != null) {
            subjects.firstOrNull { it.id == lockedSubjectId } ?: best
        } else best
        val guidance = ruleEngine.evaluate(
            subject = primary,
            faceFeatures = primary.faceFeatures,
            horizonDegrees = _horizonDegrees,
            pose = pose
        )

        // 防抖
        val showGuidance = debounce.shouldShow(guidance)

        // 评分：基础分 + 惩罚（以锁定/最大主体为准）
        val base = scorer.scoreSubject(primary.box)
        val penalties = buildPenalties(guidance, primary.box)
        val finalScore = scorer.applyPenalties(base, penalties)
        latestScore = finalScore

        // 推荐框
        val rec: Recommendation? = if (showGuidance && guidance.type != GuidanceType.GOOD) {
            framer.recommend(primary.box)
        } else null

        // ── AI 辅助：目标圆圈跟随主体 ──
        val aiTarget = if (aiAssistActive && aiPhotographerPhase == com.aicamera.composition.AiPhase.GUIDING) {
            aiGuide.createTarget(currentMode, primary.box, 1080f, 1920f)
        } else null

        // AI 辅助对准闭环：达到目标后自动拍摄（仅一次）
        if (aiAssistActive && aiTarget != null && aiTarget.reached && !aiReachedFired) {
            aiReachedFired = true
            aiPhotographerPhase = com.aicamera.composition.AiPhase.READY_SHOOT
            _toastMessage.value = "✓ 构图很棒，自动拍摄！"
            if (::cameraManager.isInitialized) {
                cameraManager.takePhoto { file ->
                    if (file != null) {
                        _lastPhotoPath.value = file.absolutePath
                        hapticFeedback()
                    }
                }
            }
        }
        // 对准后未触发条件消失（主体移开目标）→ 允许再次触发
        if (aiTarget == null || !aiTarget.reached) {
            aiReachedFired = false
        }

        // 自动快门（独立于 AI 辅助的自定义设置）
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
            poseEnabled = curSettings.poseGuidance,
            shootingMode = currentMode,
            filterStyle = currentFilter,
            aiAssistActive = aiAssistActive,
            aiTarget = aiTarget,
            aiPhotographer = photographerState(),
            guideSteps = guideStepsFor(currentMode, hasSubject = true),
            selectedSubjectId = lockedSubjectId,
            lockedSubjectId = lockedSubjectId,
            showTargetReticle = aiAssistActive,
            aiPhase = aiPhotographerPhase,
            aiMessage = aiTarget?.let { if (it.reached) "✓ 已对准，构图很棒！" else it.label } ?: "",
            aspectRatio = currentAspect,
            exposureCompensation = currentExposure,
            showHorizonLine = showHorizonLine,
            showGridLines = showGridLines,
            frameStyle = currentFrameStyle,
            timerSeconds = currentTimer,
            flashState = currentFlashState
        )
        _overlayState.value = newState
    }

    /** 当前 AI 摄影师状态（供覆盖层/UI 显示） */
    private fun photographerState(): com.aicamera.composition.AiPhotographerState? =
        if (aiPhotographerPhase == com.aicamera.composition.AiPhase.IDLE) null
        else com.aicamera.composition.AiPhotographerState(
            phase = aiPhotographerPhase,
            userPrompt = aiPromptText,
            suggestionText = if (aiPhotographerPhase == com.aicamera.composition.AiPhase.PLAN_READY ||
                aiPhotographerPhase == com.aicamera.composition.AiPhase.GUIDING ||
                aiPhotographerPhase == com.aicamera.composition.AiPhase.READY_SHOOT
            ) aiGuide.buildPlanText(currentMode, aiPromptText) else "",
            thinking = aiPhotographerPhase == com.aicamera.composition.AiPhase.ANALYZING
        )

    /** 分步引导列表 */
    private fun guideStepsFor(mode: com.aicamera.composition.ShootingMode, hasSubject: Boolean): List<com.aicamera.composition.GuideStep> {
        if (aiPhotographerPhase != com.aicamera.composition.AiPhase.GUIDING &&
            aiPhotographerPhase != com.aicamera.composition.AiPhase.READY_SHOOT
        ) return emptyList()
        val target = try {
            aiGuide.createTarget(mode, if (hasSubject) latestAnalysis.subjects.maxByOrNull { it.box.width() * it.box.height() }?.box else null, 1080f, 1920f)
        } catch (_: Exception) {
            com.aicamera.composition.AiTarget()
        }
        return aiGuide.buildGuideSteps(mode, hasSubject, target)
    }

    // ═══════════════ mola 复刻操作 ═══════════════

    /** 切换拍摄模式 */
    fun selectMode(mode: com.aicamera.composition.ShootingMode) {
        currentMode = mode
        // 镜头/滤镜相关模式提示
        when (mode) {
            com.aicamera.composition.ShootingMode.NIGHT -> _toastMessage.value = "夜景模式：低噪点长曝光"
            com.aicamera.composition.ShootingMode.PORTRAIT -> _toastMessage.value = "人像模式：主体对准右上三分点"
            com.aicamera.composition.ShootingMode.FOOD -> _toastMessage.value = "美食模式：45° 俯拍更佳"
            com.aicamera.composition.ShootingMode.LANDSCAPE -> _toastMessage.value = "风景模式：地平线在下三分之一"
            com.aicamera.composition.ShootingMode.VIDEO -> _toastMessage.value = "视频模式"
            else -> _toastMessage.value = "自动模式"
        }
        if (aiPhotographerPhase != com.aicamera.composition.AiPhase.IDLE) {
            aiPhotographerPhase = com.aicamera.composition.AiPhase.PLAN_READY
        }
    }

    /** 切换滤镜 */
    fun selectFilter(f: com.aicamera.composition.FilterStyle) {
        currentFilter = f
    }

    /** 开启/关闭 AI 辅助（底部按钮） */
    fun toggleAiAssist() {
        aiAssistActive = !aiAssistActive
        if (aiAssistActive) {
            aiPhotographerPhase = com.aicamera.composition.AiPhase.GUIDING
            aiReachedFired = false
            _toastMessage.value = "AI 辅助开启：自动识别主体，跟着圆圈移动手机"
        } else {
            aiPhotographerPhase = com.aicamera.composition.AiPhase.IDLE
            aiReachedFired = false
            lockedSubjectId = null
            _toastMessage.value = "AI 辅助已关闭"
        }
    }

    /** 打开 AI 摄影师（右下角笑脸） */
    fun openAiPhotographer() {
        if (aiPhotographerPhase == com.aicamera.composition.AiPhase.IDLE) {
            aiPhotographerPhase = com.aicamera.composition.AiPhase.WELCOME
            _toastMessage.value = "欢迎来到 AI 摄影师～告诉我你想拍什么"
        }
    }

    /** 提交 AI 拍摄意图 → 模拟分析 → 生成方案（mola：AI 思考中 → 定制方案） */
    fun submitAiPrompt(prompt: String) {
        aiPromptText = prompt
        aiPhotographerPhase = com.aicamera.composition.AiPhase.ANALYZING
        _toastMessage.value = "AI 正在思考…"
        viewModelScope.launch {
            // 模拟 1.2s 分析（真实实现接云端大模型）
            kotlinx.coroutines.delay(1200)
            aiPhotographerPhase = com.aicamera.composition.AiPhase.PLAN_READY
            _toastMessage.value = "方案已生成，开始分步引导"
            // 短暂后进入引导
            kotlinx.coroutines.delay(600)
            aiPhotographerPhase = com.aicamera.composition.AiPhase.GUIDING
            aiAssistActive = true
        }
    }

    /** 关闭 AI 摄影师 */
    fun closeAiPhotographer() {
        aiPhotographerPhase = com.aicamera.composition.AiPhase.IDLE
        aiAssistActive = false
    }

    /** 手动选主体（AI 框错时，mola：识别中点预览下方「手动选主体」） */
    fun manualSelectSubject(x: Float, y: Float) {
        val picked = aiGuide.pickAtPoint(latestAnalysis.subjects, x, y)
        if (picked != null) {
            lockedSubjectId = picked.id
            _toastMessage.value = "已锁定主体：${picked.label}"
            hapticFeedback()
        } else {
            // 点击空白取消锁定
            lockedSubjectId = null
            _toastMessage.value = "已取消锁定"
        }
    }

    /** 取消锁定主体 */
    fun clearLockedSubject() {
        lockedSubjectId = null
    }

    /** 重置 AI 引导（mola：「AI退出但保留视频构图」） */
    fun resetAiGuide() {
        aiAssistActive = false
        aiPhotographerPhase = com.aicamera.composition.AiPhase.IDLE
        aiReachedFired = false
        lockedSubjectId = null
    }

    // ═══════════════ mola 相机控件操作 ═══════════════

    /** 切换画幅 */
    fun selectAspect(a: com.aicamera.composition.AspectRatio) {
        currentAspect = a
        _toastMessage.value = "画幅：${a.label}"
    }

    /** 调节曝光补偿（-2..+2） */
    fun setExposure(value: Float) {
        currentExposure = value.coerceIn(-2f, 2f)
        if (::cameraManager.isInitialized) {
            // 通过 CameraX 曝光补偿（若支持）
        }
    }

    /** 切换水平仪辅助线 */
    fun toggleHorizonLine() {
        showHorizonLine = !showHorizonLine
    }

    /** 切换网格辅助线 */
    fun toggleGridLines() {
        showGridLines = !showGridLines
        // 同步网格模式
        val mode = if (showGridLines) GridMode.THIRDS else GridMode.NONE
        viewModelScope.launch { settingsRepository.setGridMode(mode) }
    }

    /** 切换相框风格（mola 相框风格：无/胶片/日期戳/品牌框） */
    fun cycleFrameStyle() {
        currentFrameStyle = when (currentFrameStyle) {
            com.aicamera.composition.FrameStyle.NONE -> com.aicamera.composition.FrameStyle.FILM_FRAME
            com.aicamera.composition.FrameStyle.FILM_FRAME -> com.aicamera.composition.FrameStyle.DATE_STAMP
            com.aicamera.composition.FrameStyle.DATE_STAMP -> com.aicamera.composition.FrameStyle.BRAND_FRAME
            com.aicamera.composition.FrameStyle.BRAND_FRAME -> com.aicamera.composition.FrameStyle.NONE
        }
        _toastMessage.value = "相框：${currentFrameStyle.label}"
    }

    /** 定时拍摄 0/3/5/10s */
    fun cycleTimer() {
        currentTimer = when (currentTimer) {
            0 -> 3
            3 -> 5
            5 -> 10
            else -> 0
        }
        _toastMessage.value = if (currentTimer > 0) "定时：${currentTimer}秒" else "定时：关"
    }

    /** 闪光灯状态循环 关→开→自动 */
    fun cycleFlash() {
        currentFlashState = when (currentFlashState) {
            com.aicamera.composition.FlashState.OFF -> com.aicamera.composition.FlashState.ON
            com.aicamera.composition.FlashState.ON -> com.aicamera.composition.FlashState.AUTO
            com.aicamera.composition.FlashState.AUTO -> com.aicamera.composition.FlashState.OFF
        }
        toggleFlash()
        _toastMessage.value = "闪光：${currentFlashState.label}"
    }

    /** 点按时调曝光（mola「点按对焦・调曝光」：点画面出现小太阳） */
    fun tapToAdjustExposure(delta: Float) {
        currentExposure = (currentExposure + delta).coerceIn(-2f, 2f)
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

    /** 手动拍照（支持 mola 定时） */
    fun manualShutter() {
        if (!::cameraManager.isInitialized) {
            _toastMessage.value = "相机未就绪，请稍候"
            return
        }
        val timer = currentTimer
        if (timer > 0) {
            _toastMessage.value = "定时 ${timer} 秒后拍摄"
            viewModelScope.launch {
                kotlinx.coroutines.delay(timer * 1000L)
                if (!::cameraManager.isInitialized) return@launch
                cameraManager.takePhoto { file ->
                    if (file != null) {
                        _lastPhotoPath.value = file.absolutePath
                        _toastMessage.value = "已保存到相册"
                        hapticFeedback()
                    }
                }
            }
        } else {
            cameraManager.takePhoto { file ->
                if (file != null) {
                    _lastPhotoPath.value = file.absolutePath
                    _toastMessage.value = "已保存到相册"
                    hapticFeedback()
                }
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