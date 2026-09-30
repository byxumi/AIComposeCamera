package com.aicamera.composition

import android.graphics.RectF

/** 检测主体（对象/人脸通用归一化结果，0..1 坐标，以预览画面为基准） */
data class DetectedSubject(
    val id: Int,
    val box: RectF,               // 归一化 [0..1] 坐标
    val label: String,            // 主体标签（人/宠物/食物…）
    val confidence: Float,        // 0..1
    val kind: SubjectKind,
    val faceFeatures: FaceFeatures? = null
)

enum class SubjectKind { OBJECT, FACE, POSE }

/** 人脸辅助特征（ML Kit Face 检测） */
data class FaceFeatures(
    val smilingProbability: Float? = null,
    val leftEyeOpen: Float? = null,
    val rightEyeOpen: Float? = null,
    val headEulerAngleY: Float? = null, // 偏航（左右转）
    val headEulerAngleZ: Float? = null  // 翻滚（倾斜）
)

/** 姿态关键点（MediaPipe，归一化 0..1/preview 基准） */
data class PoseLimb(
    val shoulderMidX: Float,
    val shoulderMidY: Float,
    val hipMidX: Float,
    val hipMidY: Float,
    val bodyHeight: Float,
    val visible: Boolean
)

/** 一次完整分析结果（送给 UI 层） */
data class AnalysisResult(
    val subjects: List<DetectedSubject> = emptyList(),
    val pose: PoseLimb? = null,
    val timestampMs: Long = 0L
)

/** 构图引导提示类型 */
enum class GuidanceType {
    NONE,
    HEAD_CROPPED,      // 头被裁切
    FEET_CROPPED,      // 脚被裁切
    HORIZON_TILTED,    // 画面倾斜
    SUBJECT_TOO_SMALL, // 主体过小
    SUBJECT_TOO_LARGE, // 主体过大
    TOP_SPACE,         // 头顶留白不足
    MOVE_LEFT,         // 向右移（主体偏左）
    MOVE_RIGHT,        // 向左移
    MOVE_UP,           // 向下移
    MOVE_DOWN,         // 向上移
    ZOOM_IN,           // 靠近/放大
    ZOOM_OUT,          // 后退/缩小
    ALIGN_THIRDS,      // 对准三分线交叉点
    EYES_CLOSED,       // 闭眼
    SMILE,             // 微笑
    POSE_SHOULDER,     // 肩膀摆正
    GOOD              // 构图良好
}

/** 单个引导提示 */
data class Guidance(
    val type: GuidanceType,
    val message: String,
    val severity: Severity
)

enum class Severity { INFO, WARN, ERROR, GOOD }

/** 推荐取景框（归一化 0..1） */
data class Recommendation(
    val frame: RectF,
    val moveX: Float,          // -1..1，正=向右
    val moveY: Float,          // -1..1，正=向下
    val zoomHint: Float        // >0 建议放大倍数
)

/** 覆盖层完整状态（驱动 Compose Canvas） */
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
    // ── AI 辅助 / AI 摄影师（mola 一比一）──
    val shootingMode: ShootingMode = ShootingMode.AUTO,
    val filterStyle: FilterStyle = FilterStyle.NONE,
    val aiAssistActive: Boolean = false,
    val aiTarget: AiTarget? = null,               // 目标圆圈（AI 辅助/构图引导）
    val aiPhotographer: AiPhotographerState? = null, // AI 摄影师对话
    val guideSteps: List<GuideStep> = emptyList(),   // AI 分步引导
    val selectedSubjectId: Int? = null,           // 手动选主体
    val lockedSubjectId: Int? = null,             // 锁定主体（手动选主体后构图以此为准）
    val showTargetReticle: Boolean = false,       // 目标圆圈高亮
    val aiPhase: AiPhase = AiPhase.IDLE,
    val aiMessage: String = "",                   // AI 辅助当前提示文案
    // ── mola 相机控件状态 ──
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,
    val exposureCompensation: Float = 0f,         // 曝光补偿 -2..+2
    val showHorizonLine: Boolean = true,          // 水平仪辅助线
    val showGridLines: Boolean = true,            // 网格辅助线
    val shutterSound: Boolean = true,
    val frameStyle: FrameStyle = FrameStyle.NONE, // 相框风格
    val timerSeconds: Int = 0,                    // 定时 0/3/5/10
    val flashState: FlashState = FlashState.OFF
)

/** 画幅比例（mola 顶部画幅切换） */
enum class AspectRatio(val label: String) {
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9"),
    RATIO_1_1("1:1"),
    FULL("全屏")
}

/** 闪光灯状态（mola 顶部） */
enum class FlashState(val label: String) {
    OFF("关"), ON("开"), AUTO("自动")
}

/** 相框风格（mola 相框风格） */
enum class FrameStyle(val label: String) {
    NONE("无相框"),
    FILM_FRAME("胶片框"),
    DATE_STAMP("日期戳"),
    BRAND_FRAME("MOLA 品牌框")
}

/** 拍摄模式（mola 底部模式栏） */
enum class ShootingMode(val label: String, val icon: String) {
    AUTO("自动", "A"),
    PORTRAIT("人像", "人"),
    NIGHT("夜景", "夜"),
    FOOD("美食", "食"),
    LANDSCAPE("风景", "景"),
    VIDEO("视频", "▶")
}

/** 滤镜风格（mola 滤镜轮，简化版） */
enum class FilterStyle(val label: String) {
    NONE("原图"),
    FILM("胶片"),
    FRESH("清新"),
    VINTAGE("复古"),
    BW("黑白"),
    WARM("暖阳"),
    COOL("冷调"),
    FOOD_WARM("美食暖"),
    PORTRAIT_SOFT("人像柔"),
    NIGHT_CITY("夜城")
}

/**
 * AI 目标圆圈：AI 辅助模式下的构图目标（归一化 0..1）。
 * - cx/cy 目标中心
 * - radiusPx 屏幕上目标圆圈半径（由 UI 层换算）
 * - moveX/moveY 期望移动方向（-1..1）
 * - reached 是否已对准（构图达标）
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

/** AI 摄影师状态机（mola 右下角笑脸对话 → 定制方案 → 分步引导） */
data class AiPhotographerState(
    val phase: AiPhase = AiPhase.IDLE,
    val userPrompt: String = "",
    val suggestionText: String = "",
    val thinking: Boolean = false
)

enum class AiPhase {
    IDLE,               // 未启用
    WELCOME,            // 欢迎语（"欢迎来到 AI 摄影师，告诉我你想拍什么"）
    PROMPT_INPUT,       // 用户输入拍摄意图
    ANALYZING,          // AI 思考中…（分析画面）
    PLAN_READY,         // 已生成定制拍摄方案（多步骤）
    GUIDING,            // 分步引导中
    READY_SHOOT,        // 构图达标，可拍摄
    ERROR               // 请求失败
}

/** AI 分步引导步骤（mola「AI 推荐的引导步骤」） */
data class GuideStep(
    val index: Int,
    val title: String,
    val instruction: String,
    val done: Boolean = false,
    val target: AiTarget? = null
)