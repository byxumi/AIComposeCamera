package com.aicamera.ai

import com.aicamera.domain.model.ShootingMode

/**
 * 本地 AI 规则库：场景识别文案 + 拍摄建议（离线可用）
 */
object LocalAiRules {

    /** 场景识别（简单亮度/主体规则，占位实现） */
    fun detectScene(subjectLabel: String?, hasFace: Boolean, averageLuma: Float?): String {
        return when {
            hasFace -> "人像场景"
            subjectLabel != null -> "$subjectLabel 场景"
            averageLuma != null && averageLuma < 0.2f -> "夜景场景"
            else -> "常规场景"
        }
    }

    /** 模式对应的专业建议文案 */
    fun adviceFor(mode: ShootingMode): String = when (mode) {
        ShootingMode.AUTO -> "自动模式：让 AI 帮你识别场景，随拍随用"
        ShootingMode.PORTRAIT -> "人像：把脸对准右上三分点，找柔和顺光避免逆光脸黑"
        ShootingMode.NIGHT -> "夜景：稳住手机别抖，对准主体让它进目标圈，自动低噪合成"
        ShootingMode.FOOD -> "美食：45° 俯拍最有食欲，让主体居中、靠近光源"
        ShootingMode.LANDSCAPE -> "风景：地平线放在下三分之一，找一条前景线增强纵深"
        ShootingMode.VIDEO -> "视频：主体居中、保持水平，缓慢运镜更稳"
    }

    /** 构图错误 → 修正建议映射 */
    fun fixAdvice(error: String): String = when (error) {
        "head_crop" -> "头被切了，往下移一点"
        "feet_crop" -> "脚被切了，往上移一点"
        "tilt" -> "画面歪了，扶正手机"
        "too_small" -> "主体太小，走近一点或放大"
        "too_large" -> "主体太大，退后一点"
        "top_space" -> "头顶留白不够，往下移"
        "align" -> "把主体对准三分线交叉点"
        "eyes_closed" -> "眼睛闭上啦，睁眼重拍"
        "smile" -> "笑一下，表情更自然"
        "shoulder" -> "肩膀歪了，摆正身体"
        else -> "调整一下构图再拍"
    }

    /** 相册照片自动文案（mola：拍完自动变好看） */
    val autoCaption: String = "拍完自动变好看"
}