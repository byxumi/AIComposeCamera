package com.aicamera.composition

import android.graphics.RectF
import kotlin.math.abs

/**
 * 构图规则引擎：
 * 借鉴 clifftseng/AI-Camera 的「一次只提示一件最重要的事」思路，
 * 按优先级产出引导，避免信息轰炸。
 *
 * 优先级：切头/切脚 > 画面倾斜 > 主体过小/过大 > 头顶留白 > 三分线对齐 > 表情细节
 */
object CompositionRuleEngine {

    private val TAG = "CompositionRuleEngine"

    /** 对单主体产出引导（不裁剪场景下最优主体） */
    fun evaluate(
        subject: DetectedSubject,
        faceFeatures: FaceFeatures?,
        horizonDegrees: Float,
        pose: PoseLimb?
    ): Guidance {
        val box = subject.box
        val w = box.width().coerceIn(0f, 1f)
        val h = box.height().coerceIn(0f, 1f)
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f

        // ===== 1. 裁切（优先级最高）=====
        if (box.top <= 0.02f) {
            return Guidance(GuidanceType.HEAD_CROPPED, "头部被裁切，向下移动相机", Severity.ERROR)
        }
        if (box.bottom >= 0.98f) {
            return Guidance(GuidanceType.FEET_CROPPED, "脚部被裁切，向上移动相机", Severity.ERROR)
        }

        // ===== 2. 倾斜 =====
        if (abs(horizonDegrees) > 2.5f) {
            return Guidance(
                GuidanceType.HORIZON_TILTED,
                if (horizonDegrees > 0) "画面向右倾斜，请逆时针调整" else "画面向左倾斜，请顺时针调整",
                Severity.WARN
            )
        }

        // ===== 3. 主体大小 =====
        if (w < 0.12f || h < 0.16f) {
            return Guidance(GuidanceType.SUBJECT_TOO_SMALL, "主体太小，靠近一点或放大", Severity.WARN)
        }
        if (w > 0.92f || h > 0.95f) {
            return Guidance(GuidanceType.SUBJECT_TOO_LARGE, "主体太大，后退一点或缩小", Severity.WARN)
        }

        // ===== 4. 头顶留白（人像）=====
        if (subject.kind == SubjectKind.FACE || subject.kind == SubjectKind.POSE) {
            val topSpace = box.top
            if (topSpace < 0.06f) {
                return Guidance(GuidanceType.TOP_SPACE, "头顶留白不足，向下移动相机", Severity.WARN)
            }
            if (topSpace > 0.35f) {
                return Guidance(GuidanceType.MOVE_UP, "主体偏下，向上移动相机", Severity.WARN)
            }
        }

        // ===== 5. 三分线对齐 =====
        val dx = abs(cx - 1f / 3f).coerceAtMost(abs(cx - 2f / 3f))
        val dy = abs(cy - 1f / 3f).coerceAtMost(abs(cy - 2f / 3f))
        if (dx > 0.06f || dy > 0.06f) {
            return when {
                dx > 0.08f && cx < 0.45f ->
                    Guidance(GuidanceType.MOVE_RIGHT, "主体偏左，向右移动相机", Severity.INFO)
                dx > 0.08f && cx > 0.55f ->
                    Guidance(GuidanceType.MOVE_LEFT, "主体偏右，向左移动相机", Severity.INFO)
                dy > 0.08f && cy < 0.45f ->
                    Guidance(GuidanceType.MOVE_DOWN, "主体偏上，向下移动相机", Severity.INFO)
                dy > 0.08f && cy > 0.55f ->
                    Guidance(GuidanceType.MOVE_UP, "主体偏下，向上移动相机", Severity.INFO)
                else ->
                    Guidance(GuidanceType.ALIGN_THIRDS, "将主体对准三分线交叉点", Severity.INFO)
            }
        }

        // ===== 6. 表情/姿态细节 =====
        val fa = faceFeatures
        if (fa != null) {
            fa.leftEyeOpen?.let { le ->
                fa.rightEyeOpen?.let { re ->
                    if (le < 0.3f && re < 0.3f) {
                        return Guidance(GuidanceType.EYES_CLOSED, "眼睛闭上了，请睁眼", Severity.WARN)
                    }
                }
            }
            fa.smilingProbability?.let {
                if (it < 0.3f && subject.kind == SubjectKind.FACE) {
                    return Guidance(GuidanceType.SMILE, "微笑一下效果更好", Severity.INFO)
                }
            }
        }

        // ===== 7. 肩膀摆正（姿态）=====
        if (pose != null && pose.visible) {
            // 肩线倾斜度粗略判断（肩中点与髋中点横向偏差）
            val shoulderHipDx = abs(pose.shoulderMidX - pose.hipMidX)
            if (shoulderHipDx > 0.12f) {
                return Guidance(GuidanceType.POSE_SHOULDER, "肩膀倾斜，请摆正身体", Severity.INFO)
            }
        }

        return Guidance(GuidanceType.GOOD, "构图很棒！", Severity.GOOD)
    }

    /** 综合所有主体的最佳分类（取最大主体为分析对象） */
    fun pickBestSubject(subjects: List<DetectedSubject>): DetectedSubject? =
        subjects.maxByOrNull { it.box.width() * it.box.height() }
}