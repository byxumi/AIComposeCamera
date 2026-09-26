package com.aicamera.composition

import java.util.concurrent.ConcurrentHashMap

/**
 * 防抖：一条提示需连续成立 N 帧才真正显示，避免小幅抖动导致闪烁。
 */
class DebounceTracker(
    private val requiredFrames: Int = 6,
    private val releaseFrames: Int = 3
) {
    private val counts = ConcurrentHashMap<GuidanceType, Int>()

    /** 输入当前帧的引导，输出是否应显示（已防抖） */
    fun shouldShow(current: Guidance): Boolean {
        val type = current.type
        if (type == GuidanceType.NONE || type == GuidanceType.GOOD) {
            // 良好状态：只要当前不是错误提示即可显示（轻微延迟）
            counts.keys.removeIf { it != type }
            return true
        }
        val c = (counts[type] ?: 0) + 1
        counts[type] = c
        // 清理其它类型计数（防止残留）
        counts.keys.filter { it != type }.forEach { counts.remove(it) }
        return c >= requiredFrames
    }

    /** 复位（切换场景/主体时调用） */
    fun reset() = counts.clear()
}