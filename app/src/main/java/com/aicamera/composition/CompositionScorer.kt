package com.aicamera.composition

import android.graphics.RectF

/**
 * 构图评分器：0-100 分
 * 基础分来自主体位置/大小相对画面的关系，再按引导项扣分。
 */
object CompositionScorer {

    /** 计算单个主体的基础构图分（0-100） */
    fun scoreSubject(box: RectF): Int {
        val w = (box.width()).coerceIn(0f, 1f)
        val h = (box.height()).coerceIn(0f, 1f)

        // 1) 位置分：主体中心距最近三分点/中心的距离（0-50）
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f
        val thirdsX = arrayOf(1f / 3f, 2f / 3f)
        val thirdsY = arrayOf(1f / 3f, 2f / 3f)
        val minDx = thirdsX.minOf { kotlin.math.abs(it - cx) }
        val minDy = thirdsY.minOf { kotlin.math.abs(it - cy) }
        val centerD = kotlin.math.sqrt(
            (cx - 0.5f) * (cx - 0.5f) + (cy - 0.5f) * (cy - 0.5f)
        )
        // 距三分点越近分越高；同时考虑中心距离（要求主体大致靠近中心区域）
        val posScore = 50f * (1f - (minDx + minDy).coerceIn(0f, 1f) * 1.2f)

        // 2) 大小分：主体占画面比例（0-30）
        val area = w * h
        // 理想占画面 15%-60%
        val sizeScore = when {
            area < 0.03f -> (area / 0.03f) * 30f          // 太小
            area < 0.6f -> 30f                            // 理想区间
            else -> (30f * (1f - (area - 0.6f) / 0.4f)).coerceAtLeast(0f) // 太大
        }

        // 3) 完整度分：主体上下是否靠近画面边缘（0-20）
        var integrity = 20f
        if (box.top < 0.03f) integrity -= 10f   // 头接近/越过顶部
        if (box.bottom > 0.97f) integrity -= 10f // 脚接近/越过底部

        val raw = (posScore + sizeScore + integrity).coerceIn(0f, 100f)
        return raw.toInt()
    }

    /** 综合多主体：取最高分主体为准（择优原则，避免多人场景误判） */
    fun scoreSubjects(boxes: List<RectF>): Int =
        if (boxes.isEmpty()) 0 else boxes.maxOf { scoreSubject(it) }

    /**
     * 应用引导惩罚：有严重错误就降分。
     * @param base 基础分
     * @param penalties 每项扣分（值越大问题越严重）
     */
    fun applyPenalties(base: Int, penalties: Map<String, Int>): Int {
        var s = base
        for ((_, p) in penalties) s -= p
        return s.coerceIn(0, 100)
    }
}

/** 评分档位辅助 */
fun Int.scoreLevel(): ScoreLevel = when {
    this >= 80 -> ScoreLevel.EXCELLENT
    this >= 65 -> ScoreLevel.GOOD
    this >= 45 -> ScoreLevel.FAIR
    else -> ScoreLevel.POOR
}

enum class ScoreLevel { POOR, FAIR, GOOD, EXCELLENT }