package com.aicamera.core.design

import androidx.compose.ui.graphics.Color

/**
 * v4 设计 token — 颜色。
 *
 * 纪律:
 * - 单一强调色 [Accent] (iOS 黄 #FFD60A): AI 引导 / 目标圈 / 选中态, 全应用唯一装饰色
 * - 语义色 [Success]/[Error]/[Warning] 只表达含义, 不作装饰
 * - 对比度: White on Black ≈ 21:1, Accent on Black ≈ 11:1, SecondaryText on Black ≈ 6.7:1 (WCAG AA)
 */
object CamColors {
    /** 纯黑取景器底 */
    val Black = Color(0xFF000000)

    /** 面板面 (iOS Dark Surface) */
    val Surface = Color(0xFF1C1C1E)

    /** 抬起面板 / 轨道 */
    val SurfaceElevated = Color(0xFF2C2C2E)

    /** 主文本 / 主控件 */
    val White = Color(0xFFFFFFFF)

    /** 次级文本 */
    val SecondaryText = Color(0xFF8E8E93)

    /** 三级文本 / 禁用 */
    val TertiaryText = Color(0xFF636366)

    /** 分隔线 */
    val Separator = Color(0xFF38383A)

    /** 受控毛玻璃: 底部控制面板, 全应用唯一玻璃面 (70% 黑 + 顶线) */
    val Frosted = Color(0xB3000000)

    /** 玻璃面板顶线 (白 12%) */
    val FrostedStroke = Color(0x1FFFFFFF)

    /** ★ 单一强调色 — iOS 黄 */
    val Accent = Color(0xFFFFD60A)

    /** 强调色 20% 填充 (选中态底) */
    val AccentDim = Color(0x33FFD60A)

    /** 语义: 成功 / 达标 */
    val Success = Color(0xFF34C759)

    /** 语义: 错误 */
    val Error = Color(0xFFFF3B30)

    /** 语义: 警告 / 倾斜 */
    val Warning = Color(0xFFFF9F0A)
}