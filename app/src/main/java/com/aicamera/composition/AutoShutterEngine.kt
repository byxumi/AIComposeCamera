package com.aicamera.composition

/**
 * 自动快门状态机：
 * IDLE → LOCKING（构图达标且稳定）→ TRIGGER（拍照）→ COOLDOWN
 *
 * 参考 ai-composition-assistant 的 AutoShutterEngine 思路。
 */
class AutoShutterEngine {

    enum class State { IDLE, LOCKING, TRIGGER, COOLDOWN }

    var state: State = State.IDLE
        private set

    private var lockFrames = 0
    private var lastZ = -1f
    private var cooldownRemaining = 0

    /** 达标判定：连续达标帧数 */
    private val requiredLockFrames = 12   // 约 0.5s @ 24fps
    private val cooldownFrames = 40       // 约 1.7s 冷却，避免连拍

    /**
     * 每帧调用。
     * @param score 当前构图分 0-100
     * @param threshold 触发阈值（灵敏度）
     * @param subjectVisible 是否检测到主体
     * @return true 表示应触发快门
     */
    fun onFrame(score: Int, threshold: Int, subjectVisible: Boolean): Boolean {
        when (state) {
            State.COOLDOWN -> {
                cooldownRemaining--
                if (cooldownRemaining <= 0) state = State.IDLE
                return false
            }

            State.IDLE -> {
                if (subjectVisible && score >= threshold) {
                    state = State.LOCKING
                    lockFrames = 1
                    lastZ = score.toFloat()
                }
                return false
            }

            State.LOCKING -> {
                if (!subjectVisible || score < threshold) {
                    // 失稳：回到 IDLE（不惩罚，重新锁定）
                    state = State.IDLE
                    lockFrames = 0
                    return false
                }
                // 稳定性：分数变化幅度小
                val delta = kotlin.math.abs(score - lastZ)
                lastZ = score.toFloat()
                lockFrames++
                if (delta > 8f) {
                    // 抖动太大，重新计时
                    lockFrames = 0
                    return false
                }
                if (lockFrames >= requiredLockFrames) {
                    // 触发
                    state = State.TRIGGER
                    return true
                }
                return false
            }

            State.TRIGGER -> {
                // 由外部调用确认拍摄完成
                state = State.COOLDOWN
                cooldownRemaining = cooldownFrames
                return false
            }
        }
    }

    /** 拍摄完成后手动复位到冷却（若不走 TRIGGER 分支） */
    fun onShotTaken() {
        if (state == State.TRIGGER) state = State.COOLDOWN
        cooldownRemaining = cooldownFrames
    }

    fun reset() {
        state = State.IDLE
        lockFrames = 0
        cooldownRemaining = 0
        lastZ = -1f
    }
}