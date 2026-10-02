package com.aicamera.core.design

import androidx.compose.ui.graphics.Color

/**
 * mola 相机真实色板(逆向自 mola APK defpackage.v22 + ta0)。
 *
 * 黑金系:
 * - 强调金 [Accent] 0xFFF1C27A(暖金, 非纯黄): 选中态/目标圈/AI 引导/按钮
 * - 深金强调 [AccentStrong] 0xFFD4A373 / 亮金 [AccentLight] 0xFFFFD89B
 * - 背景 [Black] 0xFF0D0B09 近黑(带暖), 面板 [Surface] 0xFF1A120A, 抬起 [SurfaceElevated] 0xFF2E1F0E
 * - 文字 [White] 0xFFFFFFFF / [SecondaryText] 0xFF8A7560 暖灰金 / [TertiaryText] 0xFF7A726C
 * - 录制红 [RecRed] 0xFFFF0000
 * - 会员按钮 [Member] 0xFF3A2E10 深棕金
 */
object CamColors {
    /** 纯黑取景器底 → mola 近黑 0xFF0D0B09 */
    val Black = Color(0xFF0D0B09)

    /** 面板面 (mola 黑褐 0xFF1A120A) */
    val Surface = Color(0xFF1A120A)

    /** 抬起面板 / 轨道 (mola 0xFF2E1F0E) */
    val SurfaceElevated = Color(0xFF2E1F0E)

    /** 主文本 / 主控件 */
    val White = Color(0xFFFFFFFF)

    /** 次级文本 (mola 暖灰金 0xFF8A7560) */
    val SecondaryText = Color(0xFF8A7560)

    /** 三级文本 / 禁用 (mola 0xFF7A726C) */
    val TertiaryText = Color(0xFF7A726C)

    /** 分隔线 (近似来自 mola 面板线 0xFF2E1F0E 提亮) */
    val Separator = Color(0xFF3A2A10)

    /** 受控毛玻璃: 底部控制面板 (70% 黑 + 顶线, mola 风格) */
    val Frosted = Color(0xB30D0B09)

    /** 玻璃面板顶线 (白 12%) */
    val FrostedStroke = Color(0x1FFFFFFF)

    /** ★ 强调金 — mola 主色 (暖金 0xFFF1C27A) */
    val Accent = Color(0xFFF1C27A)

    /** 深金强调 (选中描边 / 次级高亮, 0xFFD4A373) */
    val AccentStrong = Color(0xFFD4A373)

    /** 亮金 (目标圈高亮 / 高光, 0xFFFFD89B) */
    val AccentLight = Color(0xFFFFD89B)

    /** 强调色 20% 填充 (选中态底) */
    val AccentDim = Color(0x33F1C27A)

    /** 语义: 成功 / 达标 */
    val Success = Color(0xFF34C759)

    /** 语义: 错误 */
    val Error = Color(0xFFFF3B30)

    /** 语义: 警告 / 倾斜 */
    val Warning = Color(0xFFFF9F0A)

    /** 录制红 (mola 0xFFFF0000) */
    val RecRed = Color(0xFFFF0000)

    /** 会员按钮底 (mola 0xFF3A2E10 深棕金) */
    val Member = Color(0xFF3A2E10)
}