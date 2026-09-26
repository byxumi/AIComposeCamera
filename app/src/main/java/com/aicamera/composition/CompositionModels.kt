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
    val poseEnabled: Boolean = false
)