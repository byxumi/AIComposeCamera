package com.aicamera.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * v4 设计 token — 字号系统。
 *
 * 系统字体 (Roboto, 中文回退系统黑体), 行高 ≈ 1.25。
 * 28 大标题 / 20 页标题 / 17 标题 / 15 正文 / 13 次级 / 11 角标 — 全应用唯一 scale。
 */
object CamType {
    val Display = TextStyle(
        fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp, letterSpacing = 0.2.sp
    )

    /** 页面大标题 (设置 / 相册) */
    val ScreenTitle = TextStyle(
        fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp
    )

    /** 区块 / 对话框标题 */
    val Title = TextStyle(
        fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp
    )

    /** 正文 */
    val Body = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )

    val BodyMedium = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    )

    /** 次级说明 */
    val Secondary = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.Normal,
        lineHeight = 17.sp
    )

    /** 角标 / 按钮文字 */
    val Caption = TextStyle(
        fontSize = 11.sp, fontWeight = FontWeight.Medium,
        lineHeight = 14.sp
    )
}