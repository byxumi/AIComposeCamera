package com.aicamera.ui.camera

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicamera.ai.AiGuideEngine
import com.aicamera.ai.LocalAiRules
import com.aicamera.ai.MolaGuidePlan
import com.aicamera.ai.MolaPlanStep
import com.aicamera.camera.AnalyzerManager
import com.aicamera.camera.CameraManager
import com.aicamera.core.util.LutRepository
import com.aicamera.core.util.MolaLut
import com.aicamera.data.PhotoStore
import com.aicamera.data.SettingsRepository
import com.aicamera.domain.model.AiPhase
import com.aicamera.domain.model.AiPhotographerState
import com.aicamera.domain.model.AiTarget
import com.aicamera.domain.model.AnalysisResult
import com.aicamera.domain.model.AppSettings
import com.aicamera.domain.model.AspectRatio
import com.aicamera.domain.model.FilterPreset
import com.aicamera.domain.model.FilterStyle
import com.aicamera.domain.model.FlashState
import com.aicamera.domain.model.FrameStyle
import com.aicamera.domain.model.GuideStep
import com.aicamera.domain.model.Guidance
import com.aicamera.domain.model.GuidanceType
import com.aicamera.domain.model.OverlayState
import com.aicamera.domain.model.Severity
import com.aicamera.domain.model.ShootingMode
import com.aicamera.domain.model.SilkFlowMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 相机主页 ViewModel：编排 相机 → 检测 → AI 构图 → 覆盖层状态
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    private var cameraManager: CameraManager? = null
    private var analyzerManager: AnalyzerManager? = null
    private var cameraBound = false
    private var pendingPreview: Pair<Preview.SurfaceProvider, androidx.camera.view.PreviewView?>? = null

    private val aiGuide = AiGuideEngine

    // ── 相机控件状态 ──
    private var currentMode = ShootingMode.AUTO
    private var currentFilter = FilterStyle.NONE
    private var currentLutFilter: String? = null
    /** mola 流光快门子模式 */
    private var currentSilkFlow = SilkFlowMode.NONE
    private var currentFullRes = false
    private var currentLivePhoto = false
    private var aiAssistActive = false
    private var aiPhase = AiPhase.IDLE
    private var aiPromptText = ""
    /** mola 固定方案步骤(ay0); 为空时退回 buildPlanText */
    private var aiGuideSteps: List<MolaPlanStep> = emptyList()
    private var lockedSubjectId: Int? = null
    private var aiReachedFired = false
    private var currentAspect = AspectRatio.RATIO_4_3
    private var currentExposure = 0f
    private var currentFrameStyle = FrameStyle.NONE
    private var currentTimer = 0
    private var currentFlash = FlashState.OFF

    // ── 状态流 ──
    private val _overlayState = MutableStateFlow(OverlayState())
    val overlayState: StateFlow<OverlayState> = _overlayState.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _cameraReady = MutableStateFlow(false)
    val cameraReady: StateFlow<Boolean> = _cameraReady.asStateFlow()

    private val _lastSavedUri = MutableStateFlow<String?>(null)
    val lastSavedUri: StateFlow<String?> = _lastSavedUri.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _isFront = MutableStateFlow(false)
    val isFront: StateFlow<Boolean> = _isFront.asStateFlow()

    // 传感器
    private var sensorManager: SensorManager? = null
    private var gravity = FloatArray(3)
    private var geomagnetic = FloatArray(3)
    private var _horizonDegrees = 0f

    private var latestSubjects: List<com.aicamera.domain.model.DetectedSubject> = emptyList()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _settings.value = s
            }
        }
        setupSensors()
    }

    fun bindCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val ctx = getApplication<Application>()
        if (cameraBound) {
            // 已绑定(通常因权限回调再次进入): 重设预览并确保会话已启动
            pendingPreview?.let { (provider, pv) ->
                cameraManager?.setPreviewSurfaceProvider(provider, pv)
                pendingPreview = null
            }
            cameraManager?.start() // start 内部有权限检查, 无权限自行 return, 幂等安全
            return
        }
        cameraBound = true
        val mgr = CameraManager(ctx, lifecycleOwner)
        cameraManager = mgr
        analyzerManager = AnalyzerManager(ctx, _settings.value.poseGuidance)
        mgr.setAnalyzer(analyzerManager!!)
        mgr.setAnalysisCallback { result ->
            onAnalysisResult(result)
        }
        viewModelScope.launch {
            mgr.isCameraReady.collect { ready -> _cameraReady.value = ready }
        }
        viewModelScope.launch {
            mgr.isRecording.collect { r -> _isRecording.value = r }
        }
        viewModelScope.launch {
            mgr.errorMessage.collect { msg -> _errorMessage.value = msg }
        }
        viewModelScope.launch {
            mgr.zoom.collect { z -> _zoom.value = z }
        }
        viewModelScope.launch {
            mgr.isFront.collect { f -> _isFront.value = f }
        }
        mgr.start()
        pendingPreview?.let { (provider, pv) ->
            mgr.setPreviewSurfaceProvider(provider, pv)
            pendingPreview = null
        }
    }

    fun bindPreview(provider: Preview.SurfaceProvider, pv: androidx.camera.view.PreviewView? = null) {
        val mgr = cameraManager
        if (mgr == null) {
            pendingPreview = provider to pv
            return
        }
        mgr.setPreviewSurfaceProvider(provider, pv)
    }

    private fun onAnalysisResult(result: AnalysisResult) {
        latestSubjects = result.subjects
        viewModelScope.launch { evaluateAndPublish(result) }
    }

    private suspend fun evaluateAndPublish(result: AnalysisResult) {
        val s = _settings.value
        val subjects = result.subjects
        val pose = result.pose

        // 主体选择：锁定优先
        val primary = lockedSubjectId?.let { id ->
            subjects.firstOrNull { it.id == id }
        } ?: subjects.maxByOrNull { it.box.width() * it.box.height() }

        if (primary == null) {
            val state = _overlayState.value.copy(
                subjects = subjects,
                pose = pose,
                guidance = Guidance(
                    GuidanceType.NONE,
                    if (aiAssistActive) "未找到主体，请将主体移入画面" else "未检测到主体",
                    Severity.INFO
                ),
                score = 0,
                horizonDegrees = _horizonDegrees,
                shootingMode = currentMode,
                filterStyle = currentFilter,
                lutFilterId = currentLutFilter,
                silkFlowMode = currentSilkFlow,
                fullResMode = currentFullRes,
                livePhotoMode = currentLivePhoto,
                aiAssistActive = aiAssistActive,
                aiTarget = if (aiAssistActive && aiPhase == AiPhase.GUIDING) {
                    aiGuide.createTarget(currentMode, null, 1080f, 1920f)
                } else null,
                aiPhotographer = photographerState(),
                guideSteps = guideStepsFor(hasSubject = false),
                lockedSubjectId = lockedSubjectId,
                aiPhase = aiPhase,
                aiMessage = if (aiAssistActive) "未找到主体" else "",
                aspectRatio = currentAspect,
                exposureCompensation = currentExposure,
                frameStyle = currentFrameStyle,
                timerSeconds = currentTimer,
                flashState = currentFlash,
                isRecording = _isRecording.value
            )
            _overlayState.value = state
            aiReachedFired = false
            return
        }

        // 构图评估（简化本地规则评分）
        val guidance = evaluateGuidance(primary.box)
        val score = computeScore(primary.box, guidance)
        val rec = if (guidance.type != GuidanceType.GOOD && guidance.type != GuidanceType.NONE) {
            aiGuide.createTarget(currentMode, primary.box, 1080f, 1920f)
                .let { t ->
                    com.aicamera.domain.model.Recommendation(
                        frame = RectF(t.cx - 0.15f, t.cy - 0.2f, t.cx + 0.15f, t.cy + 0.2f),
                        moveX = t.moveX,
                        moveY = t.moveY,
                        zoomHint = 1f
                    )
                }
        } else null

        // AI 目标圈
        val aiTarget = if (aiAssistActive && aiPhase == AiPhase.GUIDING) {
            aiGuide.createTarget(currentMode, primary.box, 1080f, 1920f)
        } else null

        // 对准 → 自动拍（仅一次）
        if (aiAssistActive && aiTarget != null && aiTarget.reached && !aiReachedFired) {
            aiReachedFired = true
            aiPhase = AiPhase.READY_SHOOT
            _toastMessage.value = "✓ 构图很棒，自动拍摄！"
            takePhoto(auto = true)
        }
        if (aiTarget == null || !aiTarget.reached) aiReachedFired = false

        // 自动快门（设置项）
        if (s.autoShutter && score >= s.shutterSensitivity && !aiAssistActive) {
            _toastMessage.value = "构图达标，自动拍摄"
            takePhoto(auto = true)
        }

        val newState = _overlayState.value.copy(
            subjects = subjects,
            pose = pose,
            guidance = guidance,
            recommendation = rec,
            score = score,
            horizonDegrees = _horizonDegrees,
            shootingMode = currentMode,
            filterStyle = currentFilter,
            lutFilterId = currentLutFilter,
            silkFlowMode = currentSilkFlow,
            fullResMode = currentFullRes,
            livePhotoMode = currentLivePhoto,
            aiAssistActive = aiAssistActive,
            aiTarget = aiTarget,
            aiPhotographer = photographerState(),
            guideSteps = guideStepsFor(hasSubject = true),
            lockedSubjectId = lockedSubjectId,
            aiPhase = aiPhase,
            aiMessage = aiTarget?.let { if (it.reached) "✓ 已对准，构图很棒！" else it.label } ?: "",
            aspectRatio = currentAspect,
            exposureCompensation = currentExposure,
            frameStyle = currentFrameStyle,
            timerSeconds = currentTimer,
            flashState = currentFlash,
            isRecording = _isRecording.value
        )
        _overlayState.value = newState
    }

    private fun evaluateGuidance(box: RectF): Guidance {
        val w = box.width().coerceIn(0f, 1f)
        val h = box.height().coerceIn(0f, 1f)
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f

        return when {
            box.top <= 0.02f -> Guidance(GuidanceType.HEAD_CROPPED, "头部被裁切，向下移动相机", Severity.ERROR)
            box.bottom >= 0.98f -> Guidance(GuidanceType.FEET_CROPPED, "脚部被裁切，向上移动相机", Severity.ERROR)
            w < 0.12f || h < 0.16f -> Guidance(GuidanceType.SUBJECT_TOO_SMALL, "主体太小，靠近一点", Severity.WARN)
            w > 0.92f || h > 0.95f -> Guidance(GuidanceType.SUBJECT_TOO_LARGE, "主体太大，退后一点", Severity.WARN)
            else -> {
                val dx = kotlin.math.abs(cx - 1f / 3f).coerceAtMost(kotlin.math.abs(cx - 2f / 3f))
                val dy = kotlin.math.abs(cy - 1f / 3f).coerceAtMost(kotlin.math.abs(cy - 2f / 3f))
                if (dx > 0.08f || dy > 0.08f) {
                    when {
                        dx > 0.08f && cx < 0.45f -> Guidance(GuidanceType.MOVE_RIGHT, "主体偏左，向右移动", Severity.INFO)
                        dx > 0.08f && cx > 0.55f -> Guidance(GuidanceType.MOVE_LEFT, "主体偏右，向左移动", Severity.INFO)
                        dy > 0.08f && cy < 0.45f -> Guidance(GuidanceType.MOVE_DOWN, "主体偏上，向下移动", Severity.INFO)
                        else -> Guidance(GuidanceType.MOVE_UP, "主体偏下，向上移动", Severity.INFO)
                    }
                } else if (kotlin.math.abs(_horizonDegrees) > 2.5f) {
                    Guidance(GuidanceType.HORIZON_TILTED, "画面倾斜，扶正手机", Severity.WARN)
                } else {
                    Guidance(GuidanceType.GOOD, "构图很棒！", Severity.GOOD)
                }
            }
        }
    }

    private fun computeScore(box: RectF, guidance: Guidance): Int {
        val w = box.width().coerceIn(0f, 1f)
        val h = box.height().coerceIn(0f, 1f)
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f
        val dx = kotlin.math.abs(cx - 1f / 3f).coerceAtMost(kotlin.math.abs(cx - 2f / 3f))
        val dy = kotlin.math.abs(cy - 1f / 3f).coerceAtMost(kotlin.math.abs(cy - 2f / 3f))
        val posScore = 50f * (1f - (dx + dy).coerceIn(0f, 1f) * 1.2f)
        val area = w * h
        val sizeScore = when {
            area < 0.03f -> (area / 0.03f) * 30f
            area < 0.6f -> 30f
            else -> (30f * (1f - (area - 0.6f) / 0.4f)).coerceAtLeast(0f)
        }
        var integrity = 20f
        if (box.top < 0.03f) integrity -= 10f
        if (box.bottom > 0.97f) integrity -= 10f
        var score = (posScore + sizeScore + integrity).coerceIn(0f, 100f).toInt()
        score -= when (guidance.type) {
            GuidanceType.HEAD_CROPPED -> 25
            GuidanceType.FEET_CROPPED -> 22
            GuidanceType.HORIZON_TILTED -> 15
            GuidanceType.SUBJECT_TOO_SMALL -> 18
            GuidanceType.SUBJECT_TOO_LARGE -> 15
            GuidanceType.MOVE_LEFT, GuidanceType.MOVE_RIGHT,
            GuidanceType.MOVE_UP, GuidanceType.MOVE_DOWN, GuidanceType.ALIGN_THIRDS -> 10
            else -> 0
        }
        return score.coerceIn(0, 100)
    }

    private fun photographerState(): AiPhotographerState? =
        if (aiPhase == AiPhase.IDLE) null
        else AiPhotographerState(
            phase = aiPhase,
            userPrompt = aiPromptText,
            suggestionText = if (aiPhase == AiPhase.PLAN_READY || aiPhase == AiPhase.GUIDING ||
                aiPhase == AiPhase.READY_SHOOT
            ) {
                if (aiGuideSteps.isNotEmpty()) {
                    val step = aiGuideSteps.firstOrNull { it.condition != "ready" }
                        ?: aiGuideSteps.last()
                    step.text
                } else aiGuide.buildPlanText(currentMode, aiPromptText)
            } else "",
            thinking = aiPhase == AiPhase.ANALYZING
        )

    private fun guideStepsFor(hasSubject: Boolean): List<GuideStep> {
        if (aiPhase != AiPhase.GUIDING && aiPhase != AiPhase.READY_SHOOT) return emptyList()
        // mola ay0 固定方案: 直接使用步骤文案
        if (aiGuideSteps.isNotEmpty()) {
            return aiGuideSteps.mapIndexed { i, s ->
                GuideStep(
                    index = i,
                    title = if (i == 0) aiPromptText else "步骤 ${i + 1}",
                    instruction = s.text,
                    done = false
                )
            }
        }
        val target = aiGuide.createTarget(
            currentMode,
            latestSubjects.maxByOrNull { it.box.width() * it.box.height() }?.box,
            1080f, 1920f
        )
        return aiGuide.buildGuideSteps(currentMode, hasSubject, target)
    }

    // ═══════════════ 操作 ═══════════════

    fun selectMode(mode: ShootingMode) {
        currentMode = mode
        _toastMessage.value = LocalAiRules.adviceFor(mode)
        if (aiPhase != AiPhase.IDLE) aiPhase = AiPhase.PLAN_READY
    }

    /** mola 流光快门子模式切换 (丝绢流水/光轨车流) */
    fun selectSilkFlow(mode: SilkFlowMode) {
        currentSilkFlow = mode
        _toastMessage.value = when (mode) {
            SilkFlowMode.NONE -> "流光快门已关闭"
            SilkFlowMode.SILK -> "丝绢流水 · 按住快门拍摄，保持手机稳定"
            SilkFlowMode.LIGHT -> "光轨/车流 · 按住快门拍摄，保持手机稳定"
        }
        if (mode != SilkFlowMode.NONE) {
            currentMode = ShootingMode.VIDEO
        }
    }

    /** 流光快门：按住开始连拍 */
    fun startSilkFlow() {
        if (currentSilkFlow == SilkFlowMode.NONE) return
        _isRecording.value = true
        cameraManager?.startBurstShots()
    }

    /** 流光快门：松手合成并保存 */
    fun stopSilkFlow() {
        if (currentSilkFlow == SilkFlowMode.NONE) return
        _isRecording.value = false
        val lightTrail = currentSilkFlow == SilkFlowMode.LIGHT
        cameraManager?.finishBurst(lightTrail) { file ->
            if (file != null) {
                val ctx = getApplication<Application>()
                val lutId = currentLutFilter
                var saved = false
                if (lutId != null) {
                    saved = kotlinx.coroutines.runBlocking {
                        val lut = findLut(lutId)
                        if (lut != null) {
                            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            if (bmp != null) {
                                val out = MolaLut.apply(bmp, lut)
                                val outFile = File(ctx.cacheDir, "edited_lut_${System.currentTimeMillis()}.jpg")
                                outFile.outputStream().use { os ->
                                    out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, os)
                                }
                                out.recycle(); bmp.recycle()
                                PhotoStore.saveImage(ctx, outFile)
                            } else false
                        } else PhotoStore.saveImage(ctx, file)
                    }
                } else {
                    saved = PhotoStore.saveImage(ctx, file)
                }
                if (saved) {
                    _toastMessage.value = if (lightTrail) "光轨/车流已保存到相册" else "丝绢流水已保存到相册"
                    haptic()
                } else {
                    _toastMessage.value = "保存失败"
                }
                file.delete()
            } else {
                _toastMessage.value = "流光拍摄失败"
            }
        }
    }

    fun selectFilter(f: FilterStyle) {
        currentFilter = f
        currentLutFilter = null
        _toastMessage.value = f.label
    }

    /** mola LUT 滤镜选择(151 款, id=assets/luts 文件名) */
    fun selectLutFilter(id: String?) {
        currentLutFilter = id
        _recommendedLutIds.value = emptyList()
        _toastMessage.value = id?.let { "滤镜已选择" } ?: "原图"
    }

    // ═══════════════ mola AI 推荐滤镜 ═══════════════

    private val _recommendedLutIds = MutableStateFlow<List<String>>(emptyList())
    val recommendedLutIds: StateFlow<List<String>> = _recommendedLutIds.asStateFlow()

    /** AI 智能推荐 N 款滤镜(离线启发式: 优先冷/暖分区 + 收藏, 展示 "AI 挑了 N 款滤镜点选后开始拍摄") */
    fun aiRecommendLuts() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            LutRepository.ensureLoaded(ctx)
            val all = LutRepository.allFilters()
            if (all.isEmpty()) { _toastMessage.value = "滤镜加载中…"; return@launch }
            // 启发式: 均匀抽 4 个分类各 1-2 款, 优先收藏, 组成 5 款
            val favs = LutRepository.favorites(ctx)
            val pool = if (favs.isNotEmpty()) all.filter { it.id in favs } + all else all
            val cats = pool.map { it.category }.distinct().take(4)
            val picked = LinkedHashSet<String>()
            for (c in cats) {
                val inCat = pool.filter { it.category == c }
                if (inCat.isNotEmpty()) picked.add(inCat.first().id)
            }
            // 补足到 5 款
            val rest = pool.filter { it.id !in picked }
            var i = 0
            while (picked.size < 5 && i < rest.size) { picked.add(rest[i].id); i++ }
            _recommendedLutIds.value = picked.toList()
            _toastMessage.value = "AI 挑了 ${picked.size} 款滤镜，点选后开始拍摄"
        }
    }

    fun clearRecommendedLuts() { _recommendedLutIds.value = emptyList() }

    /** mola 长按封面收藏/取消收藏 */
    fun toggleFavoriteLut(id: String) {
        val ctx = getApplication<Application>()
        val added = LutRepository.toggleFavorite(ctx, id)
        _toastMessage.value = if (added) "已收藏「$id」" else "已取消收藏「$id」"
        haptic()
    }

    /** mola 满血像素开关: 高画质模式 */
    fun toggleFullRes() {
        currentFullRes = !currentFullRes
        _toastMessage.value = if (currentFullRes) "满血像素已开启" else "满血像素已关闭"
    }

    /** mola 实况照片开关: 拍照同时录 3 秒动态 */
    fun toggleLivePhoto() {
        currentLivePhoto = !currentLivePhoto
        _toastMessage.value = if (currentLivePhoto) "实况照片已开启" else "实况照片已关闭"
    }

    fun toggleAiAssist() {
        aiAssistActive = !aiAssistActive
        if (aiAssistActive) {
            aiPhase = AiPhase.GUIDING
            aiReachedFired = false
            _toastMessage.value = "AI 辅助开启：跟着圆圈移动手机"
        } else {
            aiPhase = AiPhase.IDLE
            aiReachedFired = false
            lockedSubjectId = null
            _toastMessage.value = "AI 辅助已关闭"
        }
    }

    fun openAiPhotographer() {
        if (aiPhase == AiPhase.IDLE) {
            aiPhase = AiPhase.WELCOME
            _toastMessage.value = "欢迎来到 AI 摄影师～告诉我你想拍什么"
        }
    }

    /** mola ay0: 三套固定方案(元气自拍/杂志半身/旅拍大片) */
    fun selectMolaPlan(plan: MolaGuidePlan) {
        aiPromptText = plan.title
        aiPhase = AiPhase.PLAN_READY
        aiGuideSteps = plan.steps
        _toastMessage.value = "「${plan.title}」方案已就绪"
        delayThenGuide()
    }

    private fun delayThenGuide() {
        viewModelScope.launch {
            delay(800)
            if (aiPhase == AiPhase.PLAN_READY) {
                aiPhase = AiPhase.GUIDING
                aiAssistActive = true
            }
        }
    }

    fun submitAiPrompt(prompt: String) {
        aiPromptText = prompt
        aiPhase = AiPhase.ANALYZING
        _toastMessage.value = "AI 正在思考…"
        viewModelScope.launch {
            delay(1200)
            aiPhase = AiPhase.PLAN_READY
            _toastMessage.value = "方案已生成"
            delayThenGuide()
        }
    }

    fun closeAiPhotographer() {
        aiPhase = AiPhase.IDLE
        aiAssistActive = false
        aiGuideSteps = emptyList()
    }

    fun manualSelectSubject(x: Float, y: Float) {
        val picked = aiGuide.pickAtPoint(latestSubjects, x, y)
        if (picked != null) {
            lockedSubjectId = picked.id
            _toastMessage.value = "已锁定主体：${picked.label}"
            haptic()
        } else {
            lockedSubjectId = null
            _toastMessage.value = "已取消锁定"
        }
    }

    fun onTapToFocus(x: Float, y: Float) {
        cameraManager?.previewViewRef()?.let { pv ->
            cameraManager?.focusAt(pv, x, y)
        }
    }

    fun cycleAspect() {
        currentAspect = when (currentAspect) {
            AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
            AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_1_1
            AspectRatio.RATIO_1_1 -> AspectRatio.FULL
            AspectRatio.FULL -> AspectRatio.RATIO_4_3
        }
        _toastMessage.value = "画幅：${currentAspect.label}"
    }

    fun cycleFlash() {
        currentFlash = when (currentFlash) {
            FlashState.OFF -> FlashState.ON
            FlashState.ON -> FlashState.AUTO
            FlashState.AUTO -> FlashState.OFF
        }
        cameraManager?.toggleFlash()
        _toastMessage.value = "闪光：${currentFlash.label}"
    }

    fun cycleTimer() {
        currentTimer = when (currentTimer) {
            0 -> 3; 3 -> 5; 5 -> 10; else -> 0
        }
        _toastMessage.value = if (currentTimer > 0) "定时：${currentTimer}秒" else "定时：关"
    }

    /** 曝光小太阳（mola）：±1/3 EV 步进 */
    fun adjustExposure(delta: Int) {
        currentExposure = cameraManager?.setExposureCompensation(delta)?.toFloat() ?: 0f
        _toastMessage.value = if (currentExposure == 0f) "曝光：自动" else "曝光：+${currentExposure.toInt()} EV"
    }

    fun cycleFrameStyle() {
        currentFrameStyle = when (currentFrameStyle) {
            FrameStyle.NONE -> FrameStyle.STANDARD
            FrameStyle.STANDARD -> FrameStyle.PEARL
            FrameStyle.PEARL -> FrameStyle.GOLD
            FrameStyle.GOLD -> FrameStyle.ROSE_GOLD
            FrameStyle.ROSE_GOLD -> FrameStyle.AMBER
            FrameStyle.AMBER -> FrameStyle.COOL_ROSE
            FrameStyle.COOL_ROSE -> FrameStyle.CREAM_FILM
            FrameStyle.CREAM_FILM -> FrameStyle.AIRY_TONE
            FrameStyle.AIRY_TONE -> FrameStyle.TEAL_CINE
            FrameStyle.TEAL_CINE -> FrameStyle.FOREST_GREEN
            FrameStyle.FOREST_GREEN -> FrameStyle.FILM_BLUE
            FrameStyle.FILM_BLUE -> FrameStyle.RETRO_SUN
            FrameStyle.RETRO_SUN -> FrameStyle.SOFT_PINK
            FrameStyle.SOFT_PINK -> FrameStyle.COOL_WHITE
            FrameStyle.COOL_WHITE -> FrameStyle.CRIMSON
            FrameStyle.CRIMSON -> FrameStyle.BLOSSOM_HAZE
            FrameStyle.BLOSSOM_HAZE -> FrameStyle.NATURAL_PRIME
            FrameStyle.NATURAL_PRIME -> FrameStyle.NONE
        }
        _toastMessage.value = "相框：${currentFrameStyle.label}"
    }

    fun setZoom(f: Float) { cameraManager?.setZoom(f) }

    fun switchCamera() { cameraManager?.switchCamera() }

    /** 快门（支持定时） */
    fun manualShutter() {
        // mola 实况照片: 拍照 + 同步录 3 秒动态
        if (currentLivePhoto && currentSilkFlow == SilkFlowMode.NONE && currentMode != ShootingMode.VIDEO) {
            livePhotoShutter()
            return
        }
        val timer = currentTimer
        if (timer > 0) {
            _toastMessage.value = "定时 ${timer} 秒后拍摄"
            viewModelScope.launch {
                delay(timer * 1000L)
                takePhoto(auto = false)
            }
        } else {
            takePhoto(auto = false)
        }
    }

    /** 实况照片: 按快门 = 拍照 + 同步录制 3 秒动态并保存 */
    private fun livePhotoShutter() {
        takePhoto(auto = false)
        if (_isRecording.value) return
        cameraManager?.startRecording { file ->
            if (file != null) {
                val ctx = getApplication<Application>()
                PhotoStore.saveVideo(ctx, file)
                file.delete()
                _toastMessage.value = "实况照片·动态已保存到相册"
                haptic()
            }
        }
        viewModelScope.launch {
            delay(3000)
            cameraManager?.stopRecording()
        }
    }

    private fun takePhoto(auto: Boolean) {
        cameraManager?.takePhoto { file ->
            if (file != null) {
                savePhoto(file, auto)
            }
        }
    }

    private fun savePhoto(file: File, auto: Boolean) {
        val ctx = getApplication<Application>()
        // mola LUT 滤镜: 拍照后应用 3D LUT(未选滤镜则原样保存)
        val lutId = currentLutFilter
        var saved: Boolean
        if (lutId != null) {
            saved = viewModelScope.let { scope ->
                kotlinx.coroutines.runBlocking {
                    val lut = findLut(lutId)
                    if (lut != null) {
                        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) {
                            val out = MolaLut.apply(bmp, lut)
                            val outFile = File(ctx.cacheDir, "edited_lut_${System.currentTimeMillis()}.jpg")
                            outFile.outputStream().use { os ->
                                out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, os)
                            }
                            out.recycle(); bmp.recycle()
                            PhotoStore.saveImage(ctx, outFile)
                        } else false
                    } else PhotoStore.saveImage(ctx, file)
                }
            }
        } else {
            saved = PhotoStore.saveImage(ctx, file)
        }
        if (saved) {
            _toastMessage.value = if (auto) "已自动拍摄并保存" else "已保存到相册"
            haptic()
            file.delete()
        } else {
            _toastMessage.value = "保存失败"
        }
    }

    private suspend fun findLut(id: String): MolaLut.LutData? = withContext(Dispatchers.IO) {
        val f = LutRepository.find(id) ?: return@withContext null
        try {
            MolaLut.decode(getApplication<Application>().assets.open("luts/${f.lutFile}").readBytes())
        } catch (_: Exception) { null }
    }

    /** 录像：按下开始，抬起停止 */
    fun toggleVideo(onComplete: () -> Unit) {
        val mgr = cameraManager ?: return
        if (_isRecording.value) {
            mgr.stopRecording()
        } else {
            mgr.startRecording { file ->
                if (file != null) {
                    val ctx = getApplication<Application>()
                    PhotoStore.saveVideo(ctx, file)
                    file.delete()
                    _toastMessage.value = "视频已保存到相册"
                    haptic()
                }
            }
        }
        onComplete()
    }

    private fun haptic() {
        if (!_settings.value.hapticFeedback) return
        val ctx = getApplication<Application>()
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    fun onToastShown() { _toastMessage.value = null }

    // ═══════════════ 传感器 ═══════════════

    private fun setupSensors() {
        val ctx = getApplication<Application>()
        sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magn = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accel != null && magn != null) {
            sensorManager?.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_UI)
            sensorManager?.registerListener(sensorListener, magn, SensorManager.SENSOR_DELAY_UI)
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
                _horizonDegrees = Math.toDegrees(orientation[2].toDouble()).toFloat()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager?.unregisterListener(sensorListener)
        cameraManager?.stop()
        cameraManager = null
    }
}