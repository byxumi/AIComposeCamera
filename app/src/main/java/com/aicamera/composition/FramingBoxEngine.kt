package com.aicamera.composition

import android.graphics.RectF
import kotlin.math.abs

/**
 * 推荐取景框引擎：
 * 根据主体当前位置，计算一个"理想取景框"（把主体框进三分点附近），
 * 并给出方向箭头（移动方向）与缩放建议。
 */
object FramingBoxEngine {

    private const val TARGET_TOP = 0.18f   // 理想主体顶部位置
    private const val TARGET_BOTTOM = 0.82f // 理想主体底部位置
    private const val TARGET_SIZE = 0.5f    // 理想主体宽度占画面比例

    /**
     * @param box 主体归一化框（0..1）
     * @return 推荐框（归一化）+ 移动方向
     */
    fun recommend(box: RectF): Recommendation {
        val w = box.width().coerceAtLeast(0.05f)
        val h = box.height().coerceAtLeast(0.05f)
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f

        // 目标中心：最近的三分点
        val tx = if (abs(cx - 1f / 3f) <= abs(cx - 2f / 3f)) 1f / 3f else 2f / 3f
        val ty = if (abs(cy - 1f / 3f) <= abs(cy - 2f / 3f)) 1f / 3f else 2f / 3f

        // 移动量（归一化，-1..1）
        var moveX = ((tx - cx) * 3f).coerceIn(-1f, 1f)
        var moveY = ((ty - cy) * 3f).coerceIn(-1f, 1f)

        // 缩放建议：主体占画面过小需放大，过大需缩小
        val zoomHint = when {
            w < 0.25f -> (0.35f / w).coerceAtMost(3f)
            w > 0.7f -> (0.55f / w).coerceAtLeast(0.5f)
            else -> 1f
        }
        if (zoomHint != 1f && w < 0.3f) {
            // 太小时不显示方向移动（因为放大即可解决）
            moveX = 0f
            moveY = 0f
        }

        // 理想框：以主体尺寸为基础，放到目标位置
        val targetW = (w * 1.1f).coerceIn(0.3f, 0.7f)
        val targetH = targetW / w * h
        val left = (tx - targetW / 2f).coerceIn(0f, 1f - targetW)
        val top = (ty - targetH / 2f).coerceIn(0f, 1f - targetH)
        val frame = RectF(left, top, left + targetW, top + targetH)

        return Recommendation(frame = frame, moveX = moveX, moveY = moveY, zoomHint = zoomHint)
    }

    /** 方向箭头：把移动量转换成箭头朝向 */
    fun arrowDirection(moveX: Float, moveY: Float): ArrowDirection {
        if (abs(moveX) > abs(moveY)) {
            return if (moveX > 0) ArrowDirection.RIGHT else ArrowDirection.LEFT
        }
        return if (moveY > 0) ArrowDirection.DOWN else ArrowDirection.UP
    }
}

enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }