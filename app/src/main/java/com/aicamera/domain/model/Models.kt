package com.aicamera.domain.model

import android.graphics.RectF

/**
 * 检测主体（对象/人脸/姿态统一归一化结果，0..1 坐标，以预览画面为基准）
 */
data class DetectedSubject(
    val id: Int,
    val box: RectF,
    val label: String,
    val confidence: Float = 1f,
    val kind: SubjectKind,
    val faceFeatures: FaceFeatures? = null
)

enum class SubjectKind { OBJECT, FACE, POSE }

data class FaceFeatures(
    val smilingProbability: Float? = null,
    val leftEyeOpen: Float? = null,
    val rightEyeOpen: Float? = null,
    val headEulerAngleY: Float? = null,
    val headEulerAngleZ: Float? = null
)

/** 姿态关键点（MediaPipe，归一化 0..1） */
data class PoseLimb(
    val shoulderMidX: Float,
    val shoulderMidY: Float,
    val hipMidX: Float,
    val hipMidY: Float,
    val bodyHeight: Float,
    val visible: Boolean
)

/** 一次完整分析结果 */
data class AnalysisResult(
    val subjects: List<DetectedSubject> = emptyList(),
    val pose: PoseLimb? = null,
    val timestampMs: Long = 0L
)

/** 拍摄模式 (mola 模式栏: 照片/视频/夜间 + 人像/美食/风景) */
enum class ShootingMode(val label: String, val icon: String, val desc: String) {
    AUTO("照片", "照", "智能识别场景"),
    PORTRAIT("人像", "人", "人脸对准右上三分点"),
    NIGHT("夜间", "夜", "低噪长曝光"),
    FOOD("美食", "食", "45° 俯拍更佳"),
    LANDSCAPE("风景", "景", "地平线下三分之一"),
    VIDEO("视频", "▶", "居中保持水平")
}

/** 流光快门子模式 (mola: 按住快门拍摄, 保持手机稳定) */
enum class SilkFlowMode(val label: String) {
    NONE("流光快门"),
    SILK("丝绢流水"),
    LIGHT("光轨/车流")
}

/** 滤镜风格 */
enum class FilterStyle(val label: String, val accent: Long) {
    NONE("原图", 0xFFFFFFFF),
    FILM("胶片", 0xFFE8C4A0),
    FRESH("清新", 0xFFA8E6CF),
    VINTAGE("复古", 0xFFD4A574),
    BW("黑白", 0xFF9E9E9E),
    WARM("暖阳", 0xFFF5C26B),
    COOL("冷调", 0xFF6FA8DC),
    FOOD_WARM("美食", 0xFFF0A868),
    PORTRAIT_SOFT("人像", 0xFFF4B8C8),
    NIGHT_CITY("夜城", 0xFF5C6BC0)
}

/** 相框风格(mola 17 款) */
enum class FrameStyle(val label: String, val accent: Long = 0xFFFFFFFF) {
    NONE("无相框", 0xFFFFFFFF),
    STANDARD("标准", 0xFFFFFFFF),
    PEARL("珠光", 0xFFF5E6D3),
    GOLD("金色", 0xFFD4A373),
    ROSE_GOLD("玫瑰金", 0xFFE8B4A0),
    AMBER("琥珀色", 0xFFF09A3E),
    COOL_ROSE("冷调玫瑰金", 0xFFC9B8C8),
    CREAM_FILM("奶油胶片", 0xFFF0E6D2),
    AIRY_TONE("日系清透", 0xFFE8F0F2),
    TEAL_CINE("青橙电影", 0xFF3E8E8E),
    FOREST_GREEN("森系绿调", 0xFF6B8F5E),
    FILM_BLUE("富士蓝", 0xFF6FA8DC),
    RETRO_SUN("柯达暖阳", 0xFFF5C26B),
    SOFT_PINK("韩系粉调", 0xFFF4B8C8),
    COOL_WHITE("冷白清透", 0xFFE6F0F5),
    CRIMSON("中国红", 0xFFC0392B),
    BLOSSOM_HAZE("繁花如梦", 0xFFD8A8C8),
    NATURAL_PRIME("徕卡自然", 0xFF7A8C6E)
}

/** 画幅比例 */
enum class AspectRatio(val label: String) {
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9"),
    RATIO_1_1("1:1"),
    FULL("全屏")
}

/** 闪光灯状态 */
enum class FlashState(val label: String) {
    OFF("关"),
    ON("开"),
    AUTO("自动")
}

/** 构图引导提示类型 */
enum class GuidanceType {
    NONE,
    HEAD_CROPPED, FEET_CROPPED, HORIZON_TILTED,
    SUBJECT_TOO_SMALL, SUBJECT_TOO_LARGE, TOP_SPACE,
    MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN,
    ZOOM_IN, ZOOM_OUT, ALIGN_THIRDS,
    EYES_CLOSED, SMILE, POSE_SHOULDER, GOOD
}

enum class Severity { INFO, WARN, ERROR, GOOD }

data class Guidance(
    val type: GuidanceType,
    val message: String,
    val severity: Severity
)

/** 推荐取景框 */
data class Recommendation(
    val frame: RectF,
    val moveX: Float,
    val moveY: Float,
    val zoomHint: Float
)

/**
 * AI 目标圆圈（AI 辅助核心）
 */
data class AiTarget(
    val cx: Float = 0.5f,
    val cy: Float = 0.5f,
    val radiusNormalized: Float = 0.12f,
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val reached: Boolean = false,
    val label: String = "目标"
)

/** AI 摄影师状态机 */
data class AiPhotographerState(
    val phase: AiPhase = AiPhase.IDLE,
    val userPrompt: String = "",
    val suggestionText: String = "",
    val thinking: Boolean = false
)

enum class AiPhase {
    IDLE, WELCOME, PROMPT_INPUT, ANALYZING, PLAN_READY, GUIDING, READY_SHOOT, ERROR
}

/** AI 分步引导 */
data class GuideStep(
    val index: Int,
    val title: String,
    val instruction: String,
    val done: Boolean = false,
    val target: AiTarget? = null
)

/** 覆盖层完整状态 */
data class OverlayState(
    val gridMode: Int = 1,
    val subjects: List<DetectedSubject> = emptyList(),
    val pose: PoseLimb? = null,
    val guidance: Guidance = Guidance(GuidanceType.NONE, "", Severity.INFO),
    val recommendation: Recommendation? = null,
    val score: Int = 0,
    val scoreEnabled: Boolean = true,
    val subjectsEnabled: Boolean = true,
    val horizonDegrees: Float = 0f,
    val poseEnabled: Boolean = false,
    val shootingMode: ShootingMode = ShootingMode.AUTO,
    val filterStyle: FilterStyle = FilterStyle.NONE,
    val aiAssistActive: Boolean = false,
    val aiTarget: AiTarget? = null,
    val aiPhotographer: AiPhotographerState? = null,
    val guideSteps: List<GuideStep> = emptyList(),
    val selectedSubjectId: Int? = null,
    val lockedSubjectId: Int? = null,
    val showTargetReticle: Boolean = false,
    val aiPhase: AiPhase = AiPhase.IDLE,
    val aiMessage: String = "",
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val exposureCompensation: Float = 0f,
    val showHorizonLine: Boolean = true,
    val showGridLines: Boolean = true,
    val shutterSound: Boolean = true,
    val frameStyle: FrameStyle = FrameStyle.NONE,
    val timerSeconds: Int = 0,
    val flashState: FlashState = FlashState.OFF,
    val isRecording: Boolean = false,
    /** mola 3D LUT 滤镜 id(assets/luts/),null = 原图 */
    val lutFilterId: String? = null,
    /** mola 流光快门子模式 (NONE=关, SILK=丝绢流水, LIGHT=光轨/车流) */
    val silkFlowMode: SilkFlowMode = SilkFlowMode.NONE,
    /** mola 满血像素: 无损高画质保存 */
    val fullResMode: Boolean = false,
    /** mola 实况照片: 拍照同时录制 3 秒动态 */
    val livePhotoMode: Boolean = false
)

/** 相册条目 */
data class GalleryItem(
    val id: Long,
    val uri: String,
    val dateTaken: Long,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L
)

/** 滤镜参数（ColorMatrix 滤镜引擎） */
data class FilterPreset(
    val style: FilterStyle,
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val brightness: Float = 0f,
    val warmth: Float = 0f,   // -1..1 冷→暖
    val vignette: Float = 0f, // 0..1 暗角
    val tint: Long = 0xFFFFFFFF,
    val tintStrength: Float = 0f
)

/** 应用设置 */
data class AppSettings(
    val gridMode: Int = 1,
    val showSubjects: Boolean = true,
    val showScore: Boolean = true,
    val autoShutter: Boolean = false,
    val shutterSensitivity: Int = 70,
    val poseGuidance: Boolean = false,
    val hapticFeedback: Boolean = true,
    val shutterSound: Boolean = true,
    val saveLocation: Boolean = false,
    val watermarkMode: String = "none"  // none | date | brand
)