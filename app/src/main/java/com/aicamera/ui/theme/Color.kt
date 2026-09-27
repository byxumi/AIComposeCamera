package com.aicamera.ui.theme

import androidx.compose.ui.graphics.Color

/* ═══════════════════════════════════════════════
 * 苹果 iOS 设计语言配色（HIG System Colors）
 * 参考 CZAN / 苹果 HIG 色彩体系
 * ═══════════════════════════════════════════════ */

// —— iOS 基础色 ——
val IosSystemBackground = Color(0xFF000000)          // 纯黑相机底
val IosSystemGroupedBackground = Color(0xFFF2F2F7)   // iOS 分组背景（浅）/ 深色模式 #000
val IosSystemSecondaryGroupedBackground = Color(0xFF1C1C1E) // 卡片
val IosSystemLabel = Color(0xFFFFFFFF)               // 主文字
val IosSystemSecondaryLabel = Color(0xFFEBEBF5)      // 副文字（带透明度用 copy）
val IosSystemSeparator = Color(0xFF38383A)           // 分隔线

// iOS 品牌色
val IosBlue = Color(0xFF007AFF)
val IosGreen = Color(0xFF34C759)
val IosRed = Color(0xFFFF3B30)
val IosOrange = Color(0xFFFF9500)
val IosYellow = Color(0xFFFFCC00)
val IosTeal = Color(0xFF5AC8FA)
val IosPink = Color(0xFFFF2D55)
val IosIndigo = Color(0xFF5856D6)
val IosPurple = Color(0xFFAF52DE)
val IosGray = Color(0xFF8E8E93)

// 毛玻璃/半透明（相机控制条）
val IosFrostedWhite = Color(0x99FFFFFF)   // 60% 白毛玻璃
val IosFrostedDark = Color(0x66000000)    // 40% 黑毛玻璃
val IosFrostedBar = Color(0xB3000000)     // 底部控制条（70% 黑）

// ── 兼容旧变量名（构图引导色等）──
val NeoBackground = IosSystemBackground
val NeoBackgroundCard = IosSystemSecondaryGroupedBackground
val NeoAccent = IosBlue
val NeoAccentDim = IosTeal.copy(alpha = 0.6f)
val NeoTextPrimary = IosSystemLabel
val NeoTextSecondary = Color(0xFF98989F)   // iOS secondaryLabel 近似

// 构图引导色（沿用语义，换成 iOS 亮色）
val GuideGood = IosGreen
val GuideWarn = IosYellow
val GuideError = IosRed

// 取景框/箭头
val FrameColor = IosYellow                       // 构图框用 iOS 黄色高亮
val ArrowColor = Color(0xFFFFD60A)