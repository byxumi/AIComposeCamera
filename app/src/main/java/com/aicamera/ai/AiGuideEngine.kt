package com.aicamera.ai

import android.graphics.RectF
import com.aicamera.domain.model.AiTarget
import com.aicamera.domain.model.DetectedSubject
import com.aicamera.domain.model.GuideStep
import com.aicamera.domain.model.ShootingMode
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * AI 构图引导引擎（本地规则）：
 * - 目标圆圈计算（跟随主体）
 * - 达标判定（主体中心距目标 < 阈值）
 * - 分步引导步骤
 * - 手动选主体
 */
object AiGuideEngine {

    private const val REACH_DISTANCE = 0.08f

    /** 为当前模式计算目标位置（归一化 0..1） */
    fun computeTarget(mode: ShootingMode, subjectBox: RectF?): Pair<Float, Float> {
        if (subjectBox == null) {
            return when (mode) {
                ShootingMode.PORTRAIT -> 1f / 3f to 1f / 3f
                else -> 1f / 3f to 2f / 3f
            }
        }
        return when (mode) {
            ShootingMode.PORTRAIT, ShootingMode.NIGHT -> 2f / 3f to 1f / 3f
            ShootingMode.FOOD -> 0.5f to 0.42f
            ShootingMode.LANDSCAPE -> 0.5f to 1f / 3f
            ShootingMode.VIDEO -> 0.5f to 0.5f
            else -> {
                val cx = (subjectBox.left + subjectBox.right) / 2f
                val tx = if (abs(cx - 1f / 3f) <= abs(cx - 2f / 3f)) 1f / 3f else 2f / 3f
                tx to 2f / 3f
            }
        }
    }

    /** 创建目标圆圈 */
    fun createTarget(
        mode: ShootingMode,
        subjectBox: RectF?,
        frameWidth: Float,
        frameHeight: Float
    ): AiTarget {
        val (tx, ty) = computeTarget(mode, subjectBox)
        val sx = subjectBox?.let { (it.left + it.right) / 2f }
        val sy = subjectBox?.let { (it.top + it.bottom) / 2f }

        val moveX = if (sx == null) 0f else ((tx - sx) * 2f).coerceIn(-1f, 1f)
        val moveY = if (sy == null) -0.3f else ((ty - sy) * 2f).coerceIn(-1f, 1f)

        val distance = if (sx != null && sy != null) {
            sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy))
        } else Float.MAX_VALUE

        val reached = distance <= REACH_DISTANCE
        val radius = (0.12f * frameWidth).coerceAtLeast(60f).coerceAtMost(140f)

        return AiTarget(
            cx = tx,
            cy = ty,
            radiusNormalized = radius / frameWidth,
            moveX = moveX,
            moveY = moveY,
            reached = reached,
            label = when (mode) {
                ShootingMode.PORTRAIT -> "人脸对准圆圈"
                ShootingMode.FOOD -> "美食对准圆圈"
                ShootingMode.LANDSCAPE -> "主体对准圆圈"
                else -> "对准目标圆圈"
            }
        )
    }

    /** 生成分步引导 */
    fun buildGuideSteps(mode: ShootingMode, hasSubject: Boolean, target: AiTarget): List<GuideStep> {
        return listOf(
            GuideStep(
                index = 1,
                title = "找到主体",
                instruction = if (hasSubject) "已识别到主体，保持它在画面中"
                else "移动手机，让主体进入画面",
                done = hasSubject,
                target = null
            ),
            GuideStep(
                index = 2,
                title = "对准构图",
                instruction = if (target.reached) "构图已就位"
                else "跟着圆圈移动手机，让主体对准目标位置",
                done = target.reached,
                target = target
            ),
            GuideStep(
                index = 3,
                title = "按下快门",
                instruction = "对准后自动拍摄或点击快门",
                done = false
            )
        )
    }

    /** 点选主体 */
    fun pickAtPoint(subjects: List<DetectedSubject>, x: Float, y: Float): DetectedSubject? {
        return subjects.minByOrNull { s ->
            val cx = (s.box.left + s.box.right) / 2f
            val cy = (s.box.top + s.box.bottom) / 2f
            (cx - x) * (cx - x) + (cy - y) * (cy - y)
        }
    }

    /** 生成拍摄方案文案 */
    fun buildPlanText(mode: ShootingMode, prompt: String): String {
        val base = when (mode) {
            ShootingMode.PORTRAIT -> "人像写真方案：脸部放右上三分点，浅景深突出主体，自然光更佳。"
            ShootingMode.NIGHT -> "夜景方案：降低曝光避免过曝，主体对准目标圈，稳定手持。"
            ShootingMode.FOOD -> "美食方案：45° 俯拍，主体居中，暖色调提升食欲。"
            ShootingMode.LANDSCAPE -> "风景方案：地平线在下三分之一，前景引导线增强纵深感。"
            ShootingMode.VIDEO -> "视频方案：主体居中，保持水平，缓慢运镜。"
            else -> "经典构图方案：主体对准三分点，保持水平，光线柔和。"
        }
        return if (prompt.isNotBlank()) "针对「$prompt」→ $base" else base
    }

    // ═══════════════ mola AI 摄影师固定方案(ay0 逆向) ═══════════════

    /** AI 摄影师固定方案: 三套(元气自拍/杂志半身/旅拍大片) */
    val MOLA_PLANS = listOf(
        MolaGuidePlan(
            id = "lively_selfie",
            title = "元气自拍",
            desc = "歪头微笑，活泼治愈。手机举高一点点俯拍，笑容和眼神都亮起来，像随手拍到的高光瞬间。",
            steps = listOf(
                MolaPlanStep("手机举到眉心斜上方一拳，脸放大到画面约一半——离近一点。", "framing", 7.0),
                MolaPlanStep("头轻轻歪向屏幕一侧一点点，眼睛看向镜头。", "headTilt", 6.0),
                MolaPlanStep("肩膀放松下沉，轻松地笑，眼神看镜头上沿亮起来。", "timed", 5.0),
                MolaPlanStep("好状态！马上拍。", "ready", 999.0)
            )
        ),
        MolaGuidePlan(
            id = "magazine_half",
            title = "杂志半身",
            desc = "侧身错肩，气场全开。像时尚杂志内页一样干净利落的半身像，线条舒展、眼神克制。",
            steps = listOf(
                MolaPlanStep("往后半步露出肩膀，头顶别顶满，脸放在屏幕三分线一侧。", "framing", 7.0),
                MolaPlanStep("身体侧三十度左右，肩膀一前一后错开。", "bodyTurnedAway", 6.0),
                MolaPlanStep("一只手撩头发或插兜，手腕露出来，另一只手放松。", "wristVisible", 6.0),
                MolaPlanStep("看向镜头，下巴轻轻收，稳住重心准备拍。", "timed", 5.0),
                MolaPlanStep("很有杂志感，马上拍！", "ready", 999.0)
            )
        ),
        MolaGuidePlan(
            id = "travel_grand",
            title = "旅拍大片",
            desc = "全身入镜，意境感拉满。人小景大留足负空间，像旅行画报封面一样有故事感。",
            steps = listOf(
                MolaPlanStep("退到全身进画面：脚下留地面，头顶别顶满，人小景大。", "framing", 8.0),
                MolaPlanStep("手张开或扶一下包、帽檐，把身体线条拉开，别挡光。", "timed", 6.0),
                MolaPlanStep("眼睛看向远处，下巴轻轻收，别低头看脚。", "headUp", 6.0),
                MolaPlanStep("构图很棒，马上拍！", "ready", 999.0)
            )
        )
    )
}

/** mola AI 摄影师方案(ay0 逆向): 标题/描述/分步引导 */
data class MolaGuidePlan(
    val id: String,
    val title: String,
    val desc: String,
    val steps: List<MolaPlanStep>
)

/** 单步引导: 文案 + condition 类型 + 时长秒(999=常驻) */
data class MolaPlanStep(
    val text: String,
    val condition: String,
    val seconds: Double
)